import json
import contextlib
import io
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import native_hologram_host_stack
from native_hologram_host_stack import HostStack, SCOPE, cleanup_group, group_members, process_start, read_process, validate_identity, verify_resumable, verify_status


class HostStackIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.emulator = self.root / "emulator"
        self.emulator.write_bytes(b"fixture")
        self.executable = self.root / "qemu/linux-x86_64/qemu-system-x86_64-headless"
        self.executable.parent.mkdir(parents=True)
        self.executable.write_bytes(b"fixture")
        self.source = "1" * 40
        self.value = {"schema": 1, "scope": SCOPE, "serial": "emulator-5554", "source_sha": self.source,
                      "pid": 3427, "uid": os.getuid(), "start_ticks": "9001", "executable": str(self.executable),
                      "argv": [str(self.executable), "-avd", "continuum-native-pdf", "-port", "5554", "-gpu", "swangle"]}

    def validate(self, value):
        return validate_identity(value, self.emulator, "emulator-5554", self.source, os.getuid())

    def test_owned_pinned_identity_is_accepted(self):
        self.assertEqual(self.value, self.validate(self.value))

    def test_identity_cannot_expand_launch_scope(self):
        for key, value in (("schema", True), ("scope", "OTHER_PROCESS"), ("serial", "emulator-5556"),
                           ("source_sha", "2" * 40), ("pid", True), ("uid", os.getuid() + 1),
                           ("start_ticks", ""), ("executable", "/usr/bin/python3")):
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.validate(dict(self.value, **{key: value}))
        with self.assertRaises(ValueError):
            self.validate(dict(self.value, extra="unbound"))

    def test_observed_command_requires_complete_bounded_arguments(self):
        for argv in ([], [None], ["embedded\0argument"], ["x" * 131073]):
            with self.assertRaises(ValueError):
                self.validate(dict(self.value, argv=argv))

    def test_pinned_executable_cannot_escape_through_symlink(self):
        other = self.root / "other"
        other.write_bytes(b"unbound")
        self.executable.unlink()
        self.executable.symlink_to(other)
        with self.assertRaises(ValueError):
            self.validate(dict(self.value, executable=str(other)))

    def test_every_thread_must_be_owned_untraced_and_resumed(self):
        status = "Pid:\t12\nUid:\t1001\t1001\t1001\t1001\nTracerPid:\t0\nState:\tS (sleeping)\n"
        verify_status(12, 1001, status)
        for changed in (status.replace("Pid:\t12", "Pid:\t13"), status.replace("1001\n", "0\n"),
                        status.replace("TracerPid:\t0", "TracerPid:\t200"),
                        status.replace("S (sleeping)", "t (tracing stop)"),
                        status.replace("S (sleeping)", "T (stopped)"), status.replace("S (sleeping)", "Z (zombie)")):
            with self.assertRaises(ValueError):
                verify_status(12, 1001, changed)

    def test_reused_or_incomplete_process_stat_is_rejected(self):
        stat = "12 (emulator (thread)) S " + " ".join(["0"] * 18 + ["9001"])
        self.assertEqual("9001", process_start(12, stat))
        for changed in (stat.replace("12 ", "13 ", 1), "12 (thread) S 0", stat.rsplit(" ", 1)[0] + " invalid"):
            with self.assertRaises(ValueError):
                process_start(12, changed)

    def test_actual_proc_reader_keeps_own_identity(self):
        before = read_process(os.getpid(), os.getuid())
        after = read_process(os.getpid(), os.getuid())
        self.assertEqual(before, after)
        self.assertEqual(str(Path("/proc/self/exe").resolve()), before["executable"])

    def test_expired_handoff_never_launches_debugger(self):
        host = HostStack.__new__(HostStack)
        host.state = {"scope": SCOPE}
        host.process = None
        host.directory = self.root
        host.safe_to_resume = True
        with patch("native_hologram_host_stack.subprocess.Popen") as process, self.assertRaises(TimeoutError):
            host.capture(time.monotonic() + 1)
        process.assert_not_called()
        report = json.loads((self.root / "collection.json").read_text())
        self.assertEqual("FAILED", report["status"])
        self.assertTrue(report["safe_to_resume"])

    def test_actual_cancelled_launcher_never_executes_late_command(self):
        marker, stop, unexpected = self.root / "group.json", self.root / "stop", self.root / "unexpected"
        stop.write_text("CANCELLED\n")
        command = [sys.executable, "-c", "from pathlib import Path; Path(" + repr(str(unexpected)) + ").touch()"]
        result = subprocess.run([sys.executable, native_hologram_host_stack.__file__, "--launch-debugger",
            str(marker), str(stop), "nonce", json.dumps(command)], capture_output=True, timeout=3, start_new_session=True)
        self.assertEqual(125, result.returncode, result.stderr.decode())
        self.assertFalse(unexpected.exists())
        group = json.loads(marker.read_text())
        self.assertEqual(group["pid"], group["pgid"])
        self.assertEqual([], group_members(group))

    def test_actual_launcher_and_pidfd_cleanup_keep_exact_process_identity(self):
        marker, stop = self.root / "live-group.json", self.root / "live-stop"
        command = [sys.executable, "-c", "import time; time.sleep(30)"]
        process = subprocess.Popen([sys.executable, native_hologram_host_stack.__file__, "--launch-debugger",
            str(marker), str(stop), "nonce", json.dumps(command)], start_new_session=True,
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            deadline = time.monotonic() + 3
            while not marker.exists() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(marker.exists())
            group = json.loads(marker.read_text())
            self.assertEqual(process.pid, group["pid"])
            self.assertEqual(os.getpgid(process.pid), group["pgid"])
            self.assertEqual(os.getsid(process.pid), group["sid"])
            members = group_members(group)
            self.assertTrue(any(member["pid"] == process.pid for member in members))
            with self.assertRaises(ValueError):
                group_members(dict(group, sid=group["sid"] + 1))
            request = self.root / "actual-cleanup.json"
            forged = [dict(member, start_ticks=str(int(member["start_ticks"]) + 1)) for member in members]
            request.write_text(json.dumps({"group": group, "members": forged, "emulator_pid": os.getpid()}))
            with self.assertRaisesRegex(ValueError, "changed before privileged cleanup"):
                cleanup_group(request)
            self.assertIsNone(process.poll())
            request.write_text(json.dumps({"group": group, "members": members, "emulator_pid": os.getpid()}))
            with contextlib.redirect_stdout(io.StringIO()):
                cleanup_group(request)
            self.assertEqual(-signal.SIGKILL, process.wait(timeout=3))
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
            process.communicate(timeout=3)

    def test_actual_thread_status_bytes_are_archived(self):
        host = HostStack.__new__(HostStack)
        host.identity = read_process(os.getpid(), os.getuid())
        host.directory, host.state = self.root, {}
        host.verify("observed")
        paths = list((self.root / "observed-threads").glob("*-status.txt"))
        self.assertEqual(set(host.state["observed"]["untraced_threads"]), {int(p.name.split("-")[0]) for p in paths})
        for path in paths:
            verify_status(int(path.name.split("-")[0]), os.getuid(), path.read_text())
            tid = path.name.split("-")[0]
            stat = path.with_name(tid + "-stat.txt").read_text()
            fields = native_hologram_host_stack.process_fields(int(tid), stat)
            self.assertEqual({"start_ticks": fields[19], "user_ticks": int(fields[11]),
                              "system_ticks": int(fields[12])}, host.state["observed"]["thread_cpu"][tid])
        self.assertEqual(os.sysconf("SC_CLK_TCK"), host.state["observed"]["clock_ticks_per_second"])

    def cleanup_fixture(self):
        host = HostStack.__new__(HostStack)
        host.directory, host.state, host.safe_to_resume = self.root, {}, False
        host.identity = {"pid": 3427}
        host.process = Mock(pid=9001)
        host.process.poll.return_value = None
        host.process.wait.side_effect = [subprocess.TimeoutExpired("sudo", 13), 0]
        host.supervisor_start = "100"
        host.nonce, host.driver_argv = "bound-nonce", ["/usr/bin/timeout", "--foreground", "10s", "/usr/bin/gdb"]
        host.stop, host.marker = self.root / "cleanup-stop", self.root / "cleanup-group.json"
        group = {"pid": 9002, "pgid": 9002, "sid": 9001, "uid": 0, "start_ticks": "101",
                 "nonce": host.nonce, "argv": host.driver_argv, "parents": [{"pid": 9001, "start_ticks": "100"}]}
        host.marker.write_text(json.dumps(group))
        host.verify = Mock()
        return host, group

    def test_timeout_cleanup_targets_observed_root_group_then_proves_end(self):
        host, group = self.cleanup_fixture()
        member = {"pid": 9003, "start_ticks": "102", "state": "S"}
        with patch("native_hologram_host_stack.os.getpgid", return_value=7000), \
             patch("native_hologram_host_stack.os.geteuid", return_value=1001), \
             patch("native_hologram_host_stack.shutil.which", return_value="/usr/bin/kill"), \
             patch("native_hologram_host_stack.group_members", side_effect=[[member], []]), \
             patch("native_hologram_host_stack.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=b"", stderr=b"")) as signaler:
            host.finish_debugger()
        argv = signaler.call_args.args[0]
        self.assertEqual(["sudo", "-n", "--"], argv[:3])
        self.assertEqual("--cleanup-group", argv[-2])
        request = json.loads(Path(argv[-1]).read_text())
        self.assertEqual(9002, request["group"]["pgid"])
        self.assertEqual([member], request["members"])
        self.assertFalse(any(arg.startswith("-900") or arg == "-3427" for arg in argv))
        self.assertTrue(host.safe_to_resume)
        host.verify.assert_called_once_with("after")
        self.assertTrue(host.stop.exists())

    def test_unbound_group_never_receives_privileged_signal(self):
        host, group = self.cleanup_fixture()
        group["parents"][0]["start_ticks"] = "99"
        host.marker.write_text(json.dumps(group))
        with patch("native_hologram_host_stack.subprocess.run") as signaler, self.assertRaises(ValueError):
            host.finish_debugger()
        signaler.assert_not_called()
        host.verify.assert_not_called()
        self.assertFalse(host.safe_to_resume)

    def test_untraced_target_cannot_hide_a_still_live_debugger(self):
        host, group = self.cleanup_fixture()
        member = {"pid": 9003, "start_ticks": "102", "state": "S"}
        with patch("native_hologram_host_stack.os.getpgid", return_value=7000), \
             patch("native_hologram_host_stack.shutil.which", return_value="/usr/bin/kill"), \
             patch("native_hologram_host_stack.group_members", return_value=[member]), \
             patch("native_hologram_host_stack.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=b"", stderr=b"")), \
             self.assertRaisesRegex(RuntimeError, "remains active"):
            host.finish_debugger()
        self.assertFalse(host.safe_to_resume)
        host.verify.assert_not_called()

    def test_unrelated_proc_exit_does_not_abort_group_cleanup(self):
        entry = Mock()
        entry.name = "9999"
        entry.__truediv__ = Mock(return_value=Mock(read_text=Mock(side_effect=ProcessLookupError(3, "No such process"))))
        with patch("native_hologram_host_stack.Path.iterdir", return_value=iter([entry])):
            self.assertEqual([], group_members({"pgid": 9002, "sid": 9001, "start_ticks": "100"}))

    def restored_guest(self):
        directory = self.root / "guest-stack"
        directory.mkdir()
        value = {"source_sha": self.source, "serial": "emulator-5554", "status": "FAILED",
                 "adb_uid_before": "2000", "adb_uid_after": "2000", "started_unix_ns": 1, "finished_unix_ns": 2}
        path = directory / "collection.json"
        path.write_text(json.dumps(value))
        return path, value

    def test_restored_guest_with_no_started_host_capture_can_resume_other_probes(self):
        self.restored_guest()
        verify_resumable(self.root, self.source, "emulator-5554")

    def test_missing_or_unproved_guest_cleanup_blocks_other_probes(self):
        with self.assertRaises(FileNotFoundError):
            verify_resumable(self.root, self.source, "emulator-5554")
        path, value = self.restored_guest()
        for change in ({"adb_uid_after": "0"}, {"restore_error": "debugger completion missing"},
                       {"instrumentation_cleanup_error": "still running"}, {"request": {"pid": 1}},
                       {"finished_unix_ns": None}, {"source_sha": "2" * 40}):
            path.write_text(json.dumps(dict(value, **change)))
            with self.subTest(change=change), self.assertRaises(ValueError):
                verify_resumable(self.root, self.source, "emulator-5554")

    def test_started_host_capture_without_final_report_cannot_resume(self):
        self.restored_guest()
        directory = self.root / "host-stack"
        directory.mkdir()
        (directory / "gdb-stdout.txt").write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "no completion report"):
            verify_resumable(self.root, self.source, "emulator-5554")
        host = HostStack.__new__(HostStack)
        host.directory, host.safe_to_resume = directory, False
        host.state = {"source_sha": self.source, "status": "CAPTURING"}
        host.save()
        with self.assertRaisesRegex(ValueError, "detach unproved"):
            verify_resumable(self.root, self.source, "emulator-5554")

    def test_only_completed_detached_host_report_admits_other_probes(self):
        self.restored_guest()
        host = HostStack.__new__(HostStack)
        host.directory = self.root / "host-stack"
        host.directory.mkdir()
        host.safe_to_resume = True
        host.state = {"source_sha": self.source, "status": "FAILED", "finished_host_monotonic_nanos": 10}
        host.save()
        verify_resumable(self.root, self.source, "emulator-5554")
        host.safe_to_resume = False
        host.save()
        with self.assertRaises(ValueError):
            verify_resumable(self.root, self.source, "emulator-5554")


if __name__ == "__main__":
    unittest.main()
