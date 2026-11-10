import argparse
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fold7


class Fold7LabTest(unittest.TestCase):
    def test_host_report_never_claims_connection(self):
        self.assertEqual(fold7.host()["device_connected"], "NOT_CHECKED")

    def test_meminfo(self):
        self.assertEqual(fold7.parse_meminfo("TOTAL  145622  123456  333\n"), 145622)
        self.assertEqual(fold7.parse_meminfo("TOTAL PSS: 4242\n"), 4242)
        self.assertIsNone(fold7.parse_meminfo("garbage"))

    def test_cpu(self):
        data = " 16.1% 123/at.heinz.deviceai.continuumlocal: 9% user + 7% kernel\n"
        self.assertEqual(fold7.parse_cpu(data), 16.1)
        self.assertIsNone(fold7.parse_cpu("33% 12/com.other: 12% user"))

    def test_battery_and_thermal(self):
        self.assertEqual(fold7.parse_battery("temperature: 342"), 34.2)
        self.assertEqual(fold7.parse_thermal("Thermal Status: 3"), 3)
        self.assertIsNone(fold7.parse_thermal("unknown"))

    def test_gfxinfo(self):
        data = "\n".join(["---PROFILEDATA---", "Flags,IntendedVsync,FrameCompleted",
                           "0,1000000000,1010000000", "0,2000000000,2030000000",
                           "1,3000000000,3200000000", "---PROFILEDATA---"])
        result = fold7.parse_gfxinfo(data, 16.667)
        self.assertEqual(result["frame_count"], 2)
        self.assertEqual(result["frame_p95_ms"], 30.0)
        self.assertEqual(result["over_budget_pct"], 50.0)

    def test_gfxinfo_empty(self):
        self.assertIsNone(fold7.parse_gfxinfo("no data", 8.33)["frame_p95_ms"])

    def test_gfxinfo_keeps_completed_frozen_frames(self):
        data = "\n".join(["---PROFILEDATA---", "Flags,IntendedVsync,FrameCompleted",
                           "0,1000000000,1010000000", "0,2000000000,3000000000",
                           "0,4000000000,5500000000", "0,6000000000,11000000000",
                           "---PROFILEDATA---"])
        result = fold7.parse_gfxinfo(data, 16.667)
        self.assertEqual(result["frame_count"], 4)
        self.assertEqual(result["frame_p95_ms"], 5000.0)
        self.assertEqual(result["over_budget_pct"], 75.0)

    def test_gfxinfo_rejects_invalid_timestamps(self):
        data = "\n".join(["---PROFILEDATA---", "Flags,IntendedVsync,FrameCompleted",
                           "0,1000000000,0", "0,1000000000,9223372036854775807",
                           "0,0,1000000", "0,-1000000,1000000",
                           "0,2000000000,1000000000", "0,1000000000,1000000000",
                           "0,1000000000", "0,1000000000,invalid", "1,1,5000000000",
                           "---PROFILEDATA---"])
        result = fold7.parse_gfxinfo(data, 16.667)
        self.assertEqual(result["frame_count"], 0)
        self.assertIsNone(result["frame_p95_ms"])
        self.assertIsNone(result["over_budget_pct"])

    def test_version(self):
        self.assertEqual(fold7.parse_package("versionCode=44 minSdk=26\nversionName=0.3.43"),
                         {"version_code": 44, "version_name": "0.3.43"})

    def test_capture_requires_consent_without_running_adb(self):
        with self.assertRaisesRegex(ValueError, "consent"):
            fold7.capture(argparse.Namespace(consent_read_only=False))

    def test_synthetic_rejected_by_comparison(self):
        b = {"schema": fold7.SCHEMA, "provenance": "synthetic_fixture"}
        self.assertEqual(fold7.compare(b, b)["status"], "NOT_COMPARABLE")

    def test_comparison_refuses_different_conditions(self):
        sample = {"schema": fold7.SCHEMA, "provenance": "device_adb_read_only",
                  "conditions": {"scenario": "a", "workload_confirmed_by_operator": True},
                  "device": {"model": "Fold"}, "identity": {"package_installed": True},
                  "observations": [{"pss_kib": 1}], "gfxinfo": {}}
        other = dict(sample, conditions=dict(sample["conditions"], scenario="b"))
        self.assertEqual(fold7.compare(sample, other)["status"], "NOT_COMPARABLE")

    def test_comparison_missing_metrics_null(self):
        b = {"schema": fold7.SCHEMA, "provenance": "device_adb_read_only",
             "conditions": {"scenario": "a", "workload_confirmed_by_operator": True},
             "device": {"model": "Fold"}, "identity": {"package_installed": True},
             "observations": [{"pss_kib": 200}], "gfxinfo": {}}
        c = dict(b, observations=[{"pss_kib": 150}])
        result = fold7.compare(b, c)
        self.assertEqual(result["deltas"]["pss_kib"]["delta"], -50)
        self.assertIsNone(result["deltas"]["frame_p95_ms"]["delta"])

    def test_no_overwrite(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "run.json"
            fold7.atomic_json(path, {"demo": True})
            with self.assertRaises(FileExistsError):
                fold7.atomic_json(path, {"demo": False})

    def test_apk_identity_is_not_claimed_if_tools_absent(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "a.apk"
            path.write_bytes(b"synthetic")
            with patch.object(fold7.shutil, "which", return_value=None):
                result = fold7.verify_apk(str(path), "a" * 64)
            self.assertEqual(result["status"], "NOT_VERIFIED")


if __name__ == "__main__":
    unittest.main()
