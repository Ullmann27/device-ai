import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import report
import fold7


class AcceptanceReportTest(unittest.TestCase):
    def test_missing_is_not_run_and_blocked(self):
        r = report.build(source_sha="2949893")
        self.assertEqual(r["overall"], "NOT_READY")
        self.assertEqual(r["gates"]["device_telemetry"], "NOT_RUN")
        self.assertEqual(r["manual_self_declared"]["fold_unfold"], "NOT_RUN")

    def test_simulated_telemetry_never_proves_real_device(self):
        r = report.build(telemetry={"schema": fold7.SCHEMA, "provenance": "synthetic_fixture",
                                    "identity": {"package_installed": True}})
        self.assertEqual(r["gates"]["device_telemetry"], "NOT_RUN")

    def test_manual_claim_not_equivalent_to_verified(self):
        fake = {key: "PASS" for key in report.CHECKS}
        r = report.build(manual=fake)
        self.assertEqual(r["overall"], "NOT_READY")

    def test_invalid_checklist_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid"):
            report.build(manual={"surprise": "PASS"})
        with self.assertRaisesRegex(ValueError, "Invalid"):
            report.build(manual={"restart": "TRUE"})

    def test_report_distinguishes_categories(self):
        text = report.markdown(report.build())
        self.assertIn("Simulierte Android", text)
        self.assertIn("Echte Gerätetelemetrie", text)
        self.assertIn("Verifizierte Modellinferenz", text)


if __name__ == "__main__":
    unittest.main()

class UnitEvidenceTest(unittest.TestCase):
    def test_report_shows_measured_software_tests(self):
        unit = {"schema": "continuum.lab.unit.v1", "provenance": "host_python_unittest",
                "total": 29, "passed": 29, "failed": 0, "skipped": 0, "status": "PASS"}
        self.assertEqual(report.build(unit=unit)["gates"]["software_unit_tests"], "PASS")

    def test_report_rejects_fake_unit_schema(self):
        with self.assertRaises(ValueError):
            report.build(unit={"schema": "fake", "provenance": "host_python_unittest"})