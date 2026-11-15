"""Check child lifecycle, not EGL."""
from contextlib import contextmanager
import copy
import json
import os
from pathlib import Path
import select
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

import native_xvfb as display


@contextmanager
def child(ignore_term=False, executable=sys.executable, environment=None):
    source = ("import signal,time; "
              + ("signal.signal(signal.SIGTERM,signal.SIG_IGN); " if ignore_term else "")
              + "print('started',flush=True); time.sleep(30)")
    process = subprocess.Popen([str(Path(executable).resolve()), "-c", source], stdin=subprocess.DEVNULL,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=environment)
    try:
        if not select.select([process.stdout], [], [], 3)[0] or process.stdout.readline() != b"started\n":
            raise RuntimeError("Child readiness unavailable")
        yield process
    finally:
        if process.poll() is None:
            process.kill()
        process.wait(timeout=3)
        process.stdout.close()
        process.stderr.close()


@contextmanager
def startup_child(reply=b"0\n"):
    """Real child; synthetic display protocol."""
    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)
        executable = directory / "Xvfb"
        shutil.copy2(Path(sys.executable).resolve(), executable)
        (directory / "display.txt").write_bytes(reply)
        with child(executable=executable, environment={**os.environ, "PYTHONHOME": sys.base_prefix}) as process, \
                mock.patch.object(display, "ARGUMENTS", process.args[1:]), \
                mock.patch.object(display.os, "getppid", return_value=os.getpid()):
            yield process, executable, directory


@contextmanager
def command_reads(pid, observation):
    """Inject only proc command observations."""
    path = Path(f"/proc/{pid}/cmdline")
    original = Path.read_bytes
    reads = []

    def read(candidate):
        if candidate == path:
            data = observation(len(reads), original(candidate))
            reads.append(data)
            return data
        return original(candidate)

    with mock.patch.object(Path, "read_bytes", read):
        yield reads


class OwnedDisplayLifecycleTest(unittest.TestCase):
    def test_empty_command_during_startup_is_bounded_until_same_child_ready(self):
        with startup_child() as (process, executable, directory):
            report = {}
            started = time.monotonic()
            with command_reads(process.pid, lambda index, data: b"" if index == 0 else data) as observations:
                display.ready(process.pid, str(executable), directory, report)
            self.assertEqual("READY", report["status"])
            self.assertEqual(":0", report["display"])
            self.assertEqual(b"", observations[0])
            self.assertGreaterEqual(len(observations), 3)
            self.assertEqual(display.identity(process.pid, os.getpid()), report["process"])
            self.assertEqual({key: report["process"][key] for key in display.PROCESS_FIELDS},
                             report["startup_process"])
            self.assertEqual(1, report["empty_command_observations"])
            self.assertEqual(5, display.START_SECONDS)
            self.assertLess(time.monotonic() - started, display.START_SECONDS)

    def test_persistent_empty_command_expires_original_startup_deadline(self):
        with startup_child() as (process, executable, directory):
            report = {}
            started = time.monotonic()
            with command_reads(process.pid, lambda index, data: b""), \
                    self.assertRaisesRegex(TimeoutError, "startup deadline"):
                display.ready(process.pid, str(executable), directory, report)
            elapsed = time.monotonic() - started
            self.assertEqual(5, display.START_SECONDS)
            self.assertGreaterEqual(elapsed, display.START_SECONDS)
            self.assertLess(elapsed, display.START_SECONDS + 2)
            self.assertGreater(report["empty_command_observations"], 1)
            self.assertNotIn("process", report)
            self.assertNotIn("status", report)
            self.assertIsNone(process.poll())

    def test_malformed_nonempty_commands_fail_without_retry(self):
        for command in (b"incomplete", b"x" * 16384 + b"\0", b"\xff\0"):
            with self.subTest(length=len(command)), startup_child() as (process, executable, directory):
                report = {}
                with command_reads(process.pid, lambda index, data: command) as observations, \
                        self.assertRaises(ValueError):
                    display.ready(process.pid, str(executable), directory, report)
                self.assertEqual([command], observations)
                self.assertNotIn("empty_command_observations", report)
                self.assertNotIn("status", report)
                self.assertIsNone(process.poll())

    def test_empty_command_does_not_override_parent_or_uid(self):
        for mismatch in ("parent", "uid"):
            with self.subTest(mismatch=mismatch), startup_child() as (process, executable, directory):
                original = Path.read_text
                status = Path(f"/proc/{process.pid}/status")

                def read(candidate, *args, **kwargs):
                    data = original(candidate, *args, **kwargs)
                    if candidate == status and mismatch == "uid":
                        data = display.re.sub(r"^Uid:.*$", "Uid:\t" + "\t".join([str(os.getuid() + 1)] * 4),
                                              data, flags=display.re.M)
                    return data

                report = {}
                with command_reads(process.pid, lambda index, data: b""), \
                        mock.patch.object(Path, "read_text", read), \
                        mock.patch.object(display.os, "getppid", return_value=os.getpid() + (mismatch == "parent")), \
                        self.assertRaisesRegex(ValueError, "parent or UID"):
                    display.ready(process.pid, str(executable), directory, report)
                self.assertNotIn("empty_command_observations", report)
                self.assertNotIn("status", report)
                self.assertIsNone(process.poll())

    def test_changed_start_ticks_after_empty_command_never_ready(self):
        with startup_child() as (process, executable, directory):
            actual = display.identity(process.pid, os.getpid())
            original = Path.read_text
            stat_path = Path(f"/proc/{process.pid}/stat")
            stat_reads = []

            def read(candidate, *args, **kwargs):
                data = original(candidate, *args, **kwargs)
                if candidate == stat_path:
                    stat_reads.append(data)
                    if len(stat_reads) > 1:
                        prefix, fields = data.rsplit(") ", 1)
                        fields = fields.split()
                        fields[19] = str(int(fields[19]) + 1)
                        data = prefix + ") " + " ".join(fields) + "\n"
                return data

            report = {}
            with command_reads(process.pid, lambda index, data: b"" if index == 0 else data), \
                    mock.patch.object(Path, "read_text", read), \
                    self.assertRaisesRegex(ValueError, "process changed"):
                display.ready(process.pid, str(executable), directory, report)
            self.assertEqual(actual["start_ticks"], report["startup_process"]["start_ticks"])
            self.assertNotIn("status", report)
            self.assertIsNone(process.poll())

    def test_child_exit_during_empty_observation_fails_immediately(self):
        with startup_child() as (process, executable, directory):
            def exited_command(index, data):
                process.terminate()
                process.wait(timeout=1)
                return b""

            report = {}
            with command_reads(process.pid, exited_command), \
                    self.assertRaisesRegex(RuntimeError, "exited during startup"):
                display.ready(process.pid, str(executable), directory, report)
            self.assertNotIn("status", report)
            self.assertEqual(-signal.SIGTERM, process.returncode)

    def test_complete_identity_cannot_disappear_or_change_command(self):
        for change in (lambda data: b"", lambda data: data + b"changed\0"):
            with self.subTest(change=change), startup_child(reply=b"") as (process, executable, directory):
                actual = display.identity(process.pid, os.getpid())
                report = {}
                with command_reads(process.pid, lambda index, data: data if index == 0 else change(data)), \
                        self.assertRaisesRegex(ValueError, "identity changed"):
                    display.ready(process.pid, str(executable), directory, report)
                self.assertEqual(actual, report["process"])
                self.assertNotIn("status", report)
                self.assertIsNone(process.poll())

    def test_cleanup_rejects_replaced_startup_process_without_signalling(self):
        with startup_child() as (process, executable, directory):
            actual = display.identity(process.pid, os.getpid())
            bound = {key: actual[key] for key in display.PROCESS_FIELDS}
            bound["start_ticks"] -= 1
            (directory / "ready.json").write_text(json.dumps({"startup_process": bound}))
            report = {}
            with self.assertRaisesRegex(ValueError, "process changed before cleanup"):
                display.stop(process.pid, str(executable), directory, report)
            self.assertNotIn("signals", report)
            self.assertIsNone(process.poll())

    def test_cleanup_can_finish_original_child_after_empty_startup(self):
        with startup_child() as (process, executable, directory):
            actual = display.identity(process.pid, os.getpid())
            bound = {key: actual[key] for key in display.PROCESS_FIELDS}
            (directory / "ready.json").write_text(json.dumps({"startup_process": bound}))
            report = {}
            display.stop(process.pid, str(executable), directory, report)
            self.assertEqual("EXITED", report["status"])
            self.assertEqual(actual, report["process"])
            self.assertEqual([int(signal.SIGTERM)], report["signals"])
            self.assertEqual(-signal.SIGTERM, process.wait(timeout=1))

    def test_display_response_must_be_one_complete_number(self):
        self.assertEqual(":0", display.display_number(b"0\n"))
        self.assertEqual(":65535", display.display_number(b"65535\n"))
        for value in (b"", b"0", b"01\n", b"-1\n", b"65536\n", b"0\n1\n", b":0\n", b"0\r\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                display.display_number(value)

    def test_identity_observes_actual_owned_process(self):
        with child() as process:
            observed = display.identity(process.pid, os.getpid())
            self.assertEqual(process.pid, observed["pid"])
            self.assertEqual(os.getuid(), observed["uid"])
            self.assertEqual(os.getpid(), observed["parent_pid"])
            self.assertEqual(str(Path(sys.executable).resolve()), observed["executable"])
            self.assertEqual(process.args, observed["argv"])
            self.assertGreater(observed["start_ticks"], 0)

    def test_empty_command_is_allowed_only_during_owned_startup(self):
        with child() as process:
            complete = display.identity(process.pid, os.getpid())
            original = Path.read_bytes
            def read(path):
                return b"" if path == Path(f"/proc/{process.pid}/cmdline") else original(path)
            with mock.patch.object(Path, "read_bytes", read):
                with self.assertRaisesRegex(ValueError, "command unavailable"):
                    display.identity(process.pid, os.getpid())
                starting = display.identity(process.pid, os.getpid(), allow_empty_command=True)
                self.assertEqual({k: v for k, v in complete.items() if k not in ("executable", "argv")},
                                 {k: v for k, v in starting.items() if k not in ("executable", "argv")})
                self.assertIsNone(starting["executable"])
                self.assertIsNone(starting["argv"])
                with self.assertRaisesRegex(ValueError, "parent or UID"):
                    display.identity(process.pid, os.getpid() + 1, allow_empty_command=True)

    def test_malformed_command_never_becomes_startup_transition(self):
        with child() as process:
            original = Path.read_bytes
            for command in (b"not-terminated", b"x" * 16384 + b"\0"):
                def read(path):
                    return command if path == Path(f"/proc/{process.pid}/cmdline") else original(path)
                with self.subTest(command_length=len(command)), mock.patch.object(Path, "read_bytes", read):
                    with self.assertRaisesRegex(ValueError, "command unavailable"):
                        display.identity(process.pid, os.getpid(), allow_empty_command=True)

    def test_cleanup_terminates_only_bound_live_child(self):
        with child() as process, child() as unrelated:
            expected = display.identity(process.pid, os.getpid())
            report = {}
            display.stop_process(process.pid, expected, os.getpid(), report)
            self.assertEqual("EXITED", report["status"])
            self.assertEqual([int(signal.SIGTERM)], report["signals"])
            self.assertEqual(-signal.SIGTERM, process.wait(timeout=1))
            self.assertIsNone(unrelated.poll())

    def test_cleanup_escalates_only_after_term_grace(self):
        with child(ignore_term=True) as process:
            expected = display.identity(process.pid, os.getpid())
            report = {}
            display.stop_process(process.pid, expected, os.getpid(), report)
            self.assertEqual("EXITED", report["status"])
            self.assertEqual([int(signal.SIGTERM), int(signal.SIGKILL)], report["signals"])
            self.assertEqual(-signal.SIGKILL, process.wait(timeout=1))

    def test_changed_identity_never_signals_child(self):
        with child() as process:
            actual = display.identity(process.pid, os.getpid())
            for key in ("pid", "uid", "parent_pid", "start_ticks", "process_group", "session", "executable", "argv"):
                changed = copy.deepcopy(actual)
                value = changed[key]
                changed[key] = value + 1 if isinstance(value, int) else (value + ["changed"] if isinstance(value, list) else value + "-changed")
                report = {}
                with self.subTest(key=key), self.assertRaises(ValueError):
                    display.stop_process(process.pid, changed, os.getpid(), report)
                self.assertNotIn("signals", report)
                self.assertIsNone(process.poll())

    def test_unrelated_parent_never_signals_child(self):
        with child() as process:
            actual = display.identity(process.pid, os.getpid())
            report = {}
            with self.assertRaises(ValueError):
                display.stop_process(process.pid, actual, os.getpid() + 1, report)
            self.assertNotIn("signals", report)
            self.assertIsNone(process.poll())

    def test_exited_child_requires_no_signal(self):
        with child() as process:
            actual = display.identity(process.pid, os.getpid())
            process.terminate()
            process.wait(timeout=3)
            report = {}
            display.stop_process(process.pid, actual, os.getpid(), report)
            self.assertEqual("ALREADY_EXITED", report["status"])
            self.assertNotIn("signals", report)

    def test_cleanup_preserves_recorded_startup_identity(self):
        with child() as process, tempfile.TemporaryDirectory() as temporary:
            actual = display.identity(process.pid, os.getpid())
            startup = {key: value for key, value in actual.items() if key not in ("executable", "argv")}
            directory = Path(temporary)
            for key in startup:
                changed = dict(startup)
                changed[key] += 1
                (directory / "ready.json").write_text(json.dumps({"startup_process": changed}))
                report = {}
                with self.subTest(key=key), \
                        mock.patch.object(display, "ARGUMENTS", process.args[1:]), \
                        mock.patch.object(display.os, "getppid", return_value=os.getpid()), \
                        self.assertRaisesRegex(ValueError, "startup identity changed"):
                    display.stop(process.pid, actual["executable"], directory, report)
                self.assertNotIn("signals", report)
                self.assertIsNone(process.poll())

    def test_cleanup_accepts_same_recorded_startup_child(self):
        with child() as process, tempfile.TemporaryDirectory() as temporary:
            actual = display.identity(process.pid, os.getpid())
            startup = {key: value for key, value in actual.items() if key not in ("executable", "argv")}
            directory = Path(temporary)
            (directory / "ready.json").write_text(json.dumps({"startup_process": startup}))
            report = {}
            with mock.patch.object(display, "ARGUMENTS", process.args[1:]), \
                    mock.patch.object(display.os, "getppid", return_value=os.getpid()):
                display.stop(process.pid, actual["executable"], directory, report)
            self.assertEqual("EXITED", report["status"])
            self.assertEqual([int(signal.SIGTERM)], report["signals"])
            self.assertEqual(-signal.SIGTERM, process.wait(timeout=1))


class OwnedDisplayStartupTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.binary = self.directory / "Xvfb"
        self.binary.write_bytes(b"readiness fixture; not executed")
        (self.directory / "display.txt").write_bytes(b"9\n")
        self.complete = {"pid": 1234, "parent_pid": 1233, "uid": os.getuid(),
                         "start_ticks": 321, "process_group": 1233, "session": 1233,
                         "executable": str(self.binary), "argv": [str(self.binary), *display.ARGUMENTS]}
        self.starting = dict(self.complete, executable=None, argv=None)
        self.now = 0.0
        self.report = {}
        self.identities = [self.starting, self.complete, self.complete]

    def probe(self, exited_values=None):
        sequence = iter(self.identities)
        last = self.identities[-1]
        def identify(*args, **kwargs):
            return copy.deepcopy(next(sequence, last))
        def ended(descriptor, seconds=0):
            self.now += seconds
            return False
        with mock.patch.object(display.os, "pidfd_open", return_value=17), \
                mock.patch.object(display.os, "close") as close, \
                mock.patch.object(display.os, "getppid", return_value=1233), \
                mock.patch.object(display, "identity", side_effect=identify), \
                mock.patch.object(display, "exited", side_effect=exited_values or ended), \
                mock.patch.object(display.time, "monotonic", side_effect=lambda: self.now):
            try:
                display.ready(1234, self.binary, self.directory, self.report)
            finally:
                close.assert_called_once_with(17)

    def test_empty_transition_requires_full_identity_and_display_reply(self):
        self.probe()
        self.assertEqual("READY", self.report["status"])
        self.assertEqual(":9", self.report["display"])
        self.assertEqual(self.complete, self.report["process"])
        self.assertGreater(self.now, 0)

    def test_identity_reader_retries_empty_exec_window(self):
        read_text, read_bytes, resolve = Path.read_text, Path.read_bytes, Path.resolve
        command = ("\0".join(self.complete["argv"]) + "\0").encode()
        commands = iter([b"", command, command])
        def text(path, *args, **kwargs):
            if path == Path("/proc/1234/stat"):
                return "1234 (Xvfb) S 1233 1233 1233 " + "0 " * 15 + "321"
            if path == Path("/proc/1234/status"):
                return "Uid:\t" + "\t".join([str(os.getuid())] * 4) + "\n"
            return read_text(path, *args, **kwargs)
        def data(path):
            return next(commands, command) if path == Path("/proc/1234/cmdline") else read_bytes(path)
        def target(path, *args, **kwargs):
            return self.binary if path == Path("/proc/1234/exe") else resolve(path, *args, **kwargs)
        def ended(descriptor, seconds=0):
            self.now += seconds
            return False
        with mock.patch.object(Path, "read_text", text), mock.patch.object(Path, "read_bytes", data), \
                mock.patch.object(Path, "resolve", target), \
                mock.patch.object(display.os, "pidfd_open", return_value=17), \
                mock.patch.object(display.os, "close"), \
                mock.patch.object(display.os, "getppid", return_value=1233), \
                mock.patch.object(display, "exited", side_effect=ended), \
                mock.patch.object(display.time, "monotonic", side_effect=lambda: self.now):
            display.ready(1234, self.binary, self.directory, self.report)
        self.assertEqual("READY", self.report["status"])
        self.assertEqual(self.complete, self.report["process"])

    def test_empty_transition_cannot_extend_original_start_deadline(self):
        self.identities = [self.starting]
        with self.assertRaisesRegex(TimeoutError, "startup deadline"):
            self.probe()
        self.assertLessEqual(self.now, display.START_SECONDS)
        self.assertNotEqual("READY", self.report.get("status"))

    def test_startup_process_change_cannot_supply_display(self):
        for key in ("pid", "parent_pid", "uid", "start_ticks", "process_group", "session"):
            changed = copy.deepcopy(self.complete)
            changed[key] += 1
            self.identities = [self.starting, changed]
            self.report = {}
            self.now = 0
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "identity changed"):
                self.probe()
            self.assertNotEqual("READY", self.report.get("status"))

    def test_wrong_executable_or_arguments_never_ready(self):
        for key in ("executable", "argv"):
            changed = copy.deepcopy(self.complete)
            changed[key] = str(self.directory / "other") if key == "executable" else [str(self.binary), "-changed"]
            self.identities = [self.starting, changed]
            self.report = {}
            self.now = 0
            with self.subTest(key=key), self.assertRaisesRegex(TimeoutError, "startup deadline"):
                self.probe()
            self.assertNotEqual("READY", self.report.get("status"))

    def test_empty_or_malformed_display_reply_never_ready(self):
        for reply in (b"", b"9", b"9\n10\n"):
            (self.directory / "display.txt").write_bytes(reply)
            self.report = {}
            self.now = 0
            with self.subTest(reply=reply), self.assertRaises((TimeoutError, ValueError)):
                self.probe()
            self.assertNotEqual("READY", self.report.get("status"))

    def test_dead_child_cannot_supply_readiness(self):
        with self.assertRaisesRegex(RuntimeError, "exited during startup"):
            self.probe(exited_values=[True])
        self.assertNotEqual("READY", self.report.get("status"))

    def test_final_identity_change_cannot_supply_readiness(self):
        self.identities[-1] = dict(self.complete, start_ticks=999)
        with self.assertRaisesRegex(RuntimeError, "ended before readiness"):
            self.probe()
        self.assertNotEqual("READY", self.report.get("status"))

    def test_empty_command_after_binding_is_not_a_new_startup(self):
        (self.directory / "display.txt").write_bytes(b"")
        self.identities = [self.complete, self.starting]
        with self.assertRaisesRegex(ValueError, "identity changed"):
            self.probe()
        self.assertNotEqual("READY", self.report.get("status"))


if __name__ == "__main__":
    unittest.main()
