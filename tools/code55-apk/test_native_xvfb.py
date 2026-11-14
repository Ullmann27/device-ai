"""Check child lifecycle, not EGL."""
from contextlib import contextmanager
import copy
import os
from pathlib import Path
import select
import signal
import subprocess
import sys
import unittest

import native_xvfb as display


@contextmanager
def child(ignore_term=False):
    source = ("import signal,time; "
              + ("signal.signal(signal.SIGTERM,signal.SIG_IGN); " if ignore_term else "")
              + "print('started',flush=True); time.sleep(30)")
    process = subprocess.Popen([sys.executable, "-c", source], stdin=subprocess.DEVNULL,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
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


class OwnedDisplayLifecycleTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
