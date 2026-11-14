import copy
import json
import unittest

from native_hologram_stack import PACKAGE, handoff_deadline, request_response, validate_request, verify_process


class StackIdentityTest(unittest.TestCase):
    def setUp(self):
        self.request = {"schema": 1, "package": PACKAGE, "pid": 4134, "uid": 10195,
                        "nonce": "0123456789abcdef" * 2, "requested_monotonic_nanos": 197612000000}
        self.stat = "4134 (QA app (thread)) S " + " ".join(["0"] * 18 + ["9321"])
        self.status = "Name:\tmentorpaired\nUid:\t10195\t10195\t10195\t10195\n"

    def test_owned_process_has_stable_start_ticks(self):
        self.assertEqual(self.request, validate_request(self.request))
        self.assertEqual("9321", verify_process(self.request, "4134\n", (PACKAGE + "\0").encode(),
                                                self.status, self.stat))

    def test_missing_request_requires_explicit_shell_status(self):
        self.assertIsNone(request_response(44, b""))
        self.assertEqual(self.request, request_response(0, json.dumps(self.request).encode()))
        with self.assertRaises(ValueError):
            request_response(0, b"cat: cache/code59-hologram-diagnostic/stack-request.json: No such file or directory\n")
        with self.assertRaises(RuntimeError):
            request_response(1, b"run-as: package unavailable\n")
        with self.assertRaises(ValueError):
            request_response(0, b" " * 4097)

    def test_delayed_request_cannot_restart_the_java_deadline(self):
        self.assertEqual(30, handoff_deadline(240, 0, 8))
        self.assertEqual(29, handoff_deadline(240, 1, 2))
        self.assertEqual(20, handoff_deadline(20, 0, 2))
        with self.assertRaises(TimeoutError):
            handoff_deadline(240, 0, 31)
        with self.assertRaises(ValueError):
            handoff_deadline(240, None, 1)

    def test_request_cannot_expand_emulator_scope(self):
        for key, value in (("schema", True), ("package", "other.app"), ("pid", True), ("uid", 0), ("uid", 2000),
                           ("uid", 110195), ("nonce", "../other"), ("requested_monotonic_nanos", 0)):
            with self.subTest(key=key, value=value):
                request = copy.deepcopy(self.request)
                request[key] = value
                with self.assertRaises(ValueError):
                    validate_request(request)
        with self.assertRaises(ValueError):
            validate_request(dict(self.request, extra="not permitted"))

    def test_pid_and_cmdline_must_match(self):
        for pid, command in (("4135", PACKAGE), ("4134 4135", PACKAGE), ("4134", PACKAGE + ":worker")):
            with self.subTest(pid=pid, command=command), self.assertRaises(ValueError):
                verify_process(self.request, pid, (command + "\0").encode(), self.status, self.stat)

    def test_all_process_uids_are_bound(self):
        for status in ("Uid:\t0\t0\t0\t0\n", "Uid:\t10195\t10195\t10195\t0\n", "Uid:\t10195\n"):
            with self.subTest(status=status), self.assertRaises(ValueError):
                verify_process(self.request, "4134", PACKAGE.encode(), status, self.stat)

    def test_process_stat_must_be_bound_and_complete(self):
        for stat in (self.stat.replace("4134", "4135", 1), "4134 (QA) S 0", self.stat.rsplit(" ", 1)[0] + " missing"):
            with self.subTest(stat=stat), self.assertRaises(ValueError):
                verify_process(self.request, "4134", PACKAGE.encode(), self.status, stat)


if __name__ == "__main__":
    unittest.main()
