import argparse
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import system_capture as capture


def gfx(rows):
    return "\n".join(["---PROFILEDATA---", "Flags,IntendedVsync,FrameCompleted"] +
                     [f"0,{a},{b}" for a, b in rows] + ["---PROFILEDATA---"])


class SystemCaptureTest(unittest.TestCase):
    def test_battery_uses_scale_and_power_sources(self):
        self.assertEqual(capture.battery_conditions("level: 100\nscale: 200\nUSB powered: true\n"), (50, True))
        self.assertEqual(capture.battery_conditions("level: 100\nscale: 100\nstatus: 5\n"
                         "AC powered: false\nUSB powered: false\nWireless powered: false\n"), (100, False))

    def test_missing_battery_is_not_zero_or_unplugged(self):
        for text in ("", "level: 0", "level: 201\nscale: 200", "level: 0\nscale: 0"):
            self.assertEqual(capture.battery_conditions(text), (None, None))

    def test_storage_units_and_invalid_data(self):
        self.assertEqual(capture.available_bytes("Filesystem 1K-blocks Used Available Use% Mounted on\n"
                         "/dev/block/data 100000 30000 70000 30% /data"), 71680000)
        for text in ("Permission denied", "/dev/block/data 100 0 101 0% /data", "/dev/data 100 0 100 0% /sdcard"):
            self.assertIsNone(capture.available_bytes(text))

    def test_frame_boundary_excludes_old_and_duplicate_frames(self):
        before = gfx([(1_000_000_000, 1_010_000_000)])
        after = gfx([(1_000_000_000, 1_010_000_000), (2_000_000_000, 2_020_000_000),
                     (2_000_000_000, 2_020_000_000), (3_000_000_000, 8_000_000_000)])
        self.assertEqual(capture.new_frames(before, after), ([20.0, 5000.0], None))

    def test_no_baseline_or_new_frames_is_unavailable(self):
        frames = gfx([(1_000_000_000, 1_010_000_000)])
        self.assertEqual(capture.new_frames("", frames), ([], "FRAME_BOUNDARY_UNAVAILABLE"))
        self.assertEqual(capture.new_frames(frames, frames), ([], "NO_NEW_COMPLETED_FRAMES"))

    def test_frame_bound_does_not_silently_truncate(self):
        self.assertEqual(capture.new_frames(gfx([(1, 2)]),
            gfx([(100 + n * 10, 105 + n * 10) for n in range(4097)])), ([], "FRAME_SAMPLE_BOUND_EXCEEDED"))

    def test_requires_consent_without_device_access(self):
        with patch.object(capture.fold7, "adb") as adb:
            with self.assertRaises(ValueError):
                capture.capture(argparse.Namespace(consent_read_only=False))
            adb.assert_not_called()

    def args(self):
        return argparse.Namespace(consent_read_only=True, samples=5, interval=0, protocol_key="p1",
                                  workload_key="w1", serial="private-serial", display="inner", font_scale=1.0,
                                  frame_budget_ms=16.667, warm_state="unknown", background_controlled=False,
                                  scenario="scripted", output="unused.json")

    def test_bounds_rejected_before_device_access(self):
        for key, value in (("samples", 121), ("interval", 31), ("protocol_key", "secret\ntext"),
                           ("frame_budget_ms", float("inf")), ("frame_budget_ms", float("nan"))):
            args = self.args()
            setattr(args, key, value)
            with patch.object(capture.fold7, "adb") as adb:
                with self.assertRaises(ValueError):
                    capture.capture(args)
                adb.assert_not_called()

    def test_untrusted_package_path_never_becomes_command(self):
        with patch.object(capture.fold7, "adb", return_value=(0, "package:/data/app/x;id/base.apk")) as adb:
            self.assertEqual(capture.apk_build_key("device"), "")
            self.assertEqual(adb.call_count, 1)

    def test_apk_build_key_binds_split_hashes_and_os(self):
        answers = [(0, "package:/data/app/a/base.apk\npackage:/data/app/a/split_1.apk\n"),
                   (0, "a" * 64 + "  /data/app/a/base.apk\n"),
                   (0, "b" * 64 + "  /data/app/a/split_1.apk\n"), (0, "os-build")]
        with patch.object(capture.fold7, "adb", side_effect=answers):
            first = capture.apk_build_key("device")
        answers[2] = (0, "c" * 64 + "  /data/app/a/split_1.apk\n")
        with patch.object(capture.fold7, "adb", side_effect=answers):
            second = capture.apk_build_key("device")
        self.assertEqual(len(first), 64)
        self.assertNotEqual(first, second)

    def test_capture_preserves_unavailable_and_private_serial(self):
        args = self.args()
        frame_reads = 0
        def adb(_serial, *command):
            nonlocal frame_reads
            if command[0:2] == ("dumpsys", "gfxinfo"):
                frame_reads += 1
                return 0, gfx([(1, 2)]) if frame_reads == 1 else gfx([(1, 2), (10, 20)])
            if command == ("dumpsys", "battery"):
                return 0, "level: 70\nscale: 100\nAC powered: false\nUSB powered: false\nWireless powered: false"
            if command == ("df", "-k", "/data"):
                return 0, "/dev/data 100 10 90 10% /data"
            raise AssertionError(command)
        legacy = {"observations": [{"pss_kib": 100, "thermal_status": 0, "battery_temperature_c": 30}] * 4 +
                                    [{"pss_kib": None, "thermal_status": 1, "battery_temperature_c": 33}]}
        with patch.object(capture.fold7.shutil, "which", return_value="adb"), \
             patch.object(capture.fold7, "run", return_value=(0, "List of devices attached\nprivate-serial\tdevice\n")), \
             patch.object(capture, "apk_build_key", return_value="build-hash"), \
             patch.object(capture.fold7, "capture", return_value=legacy), \
             patch.object(capture.fold7, "adb", side_effect=adb):
            result = capture.capture(args)
        self.assertNotIn("private-serial", capture.json.dumps(result))
        self.assertEqual(result["metrics"]["OWN_PSS_BYTES"], [102400] * 4)
        self.assertEqual(result["failedSamples"]["OWN_PSS_BYTES"], 1)
        self.assertIsNone(result["conditions"]["thermalStatus"])
        self.assertIsNone(result["conditions"]["batteryTemperatureC"])
        self.assertEqual(result["conditions"]["warmState"], "UNKNOWN")
        self.assertFalse(result["conditions"]["backgroundControlled"])
        self.assertEqual(result["unavailable"]["MODEL_TASK_MILLIS"], "NOT_INSTRUMENTED")


if __name__ == "__main__":
    unittest.main()
