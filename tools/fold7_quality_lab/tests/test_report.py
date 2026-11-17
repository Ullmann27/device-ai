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


class UnitEvidenceTest(unittest.TestCase):
    SHA = "a" * 40

    def unit(self):
        return {"schema": "continuum.lab.unit.v1", "provenance": "host_python_unittest",
                "source_sha": self.SHA, "source_verified": True, "source_unchanged": True,
                "total": 29, "passed": 29, "failed": 0, "skipped": 0, "status": "PASS"}

    def test_report_shows_measured_software_tests(self):
        r = report.build(source_sha=self.SHA, unit=self.unit())
        self.assertEqual(r["gates"]["software_unit_tests"], "PASS")

    def test_report_cannot_relabel_missing_or_foreign_source(self):
        for sha in (None, "", "a" * 7, "LOCAL_UNCOMMITTED", "b" * 40):
            with self.subTest(source_sha=sha):
                r = report.build(source_sha=sha, unit=self.unit())
                self.assertEqual(r["gates"]["software_unit_tests"], "NOT_VERIFIED")

    def test_unit_source_must_be_committed_and_match(self):
        for sha in (None, "", "a" * 7, "LOCAL_UNCOMMITTED", "b" * 40):
            with self.subTest(unit_source_sha=sha):
                unit = self.unit()
                unit["source_sha"] = sha
                r = report.build(source_sha=self.SHA, unit=unit)
                self.assertEqual(r["gates"]["software_unit_tests"], "NOT_VERIFIED")

    def test_legacy_or_changed_checkout_cannot_attest_pass(self):
        for field in ("source_verified", "source_unchanged"):
            for value in (None, False, "true"):
                with self.subTest(field=field, value=value):
                    unit = self.unit()
                    unit[field] = value
                    r = report.build(source_sha=self.SHA, unit=unit)
                    self.assertEqual(r["gates"]["software_unit_tests"], "NOT_VERIFIED")

    def test_unbound_counts_remain_visible_without_pass(self):
        unit = self.unit()
        unit["source_sha"] = "LOCAL_UNCOMMITTED"
        r = report.build(source_sha=self.SHA, unit=unit)
        self.assertEqual(r["software_test_counts"]["passed"], 29)
        self.assertIn("LOCAL_UNCOMMITTED", report.markdown(r))
        self.assertIn("Softwaretests (Host): **NOT_VERIFIED**", report.markdown(r))

    def test_bound_failure_remains_failure(self):
        unit = self.unit()
        unit.update(status="FAIL", failed=1, passed=28)
        r = report.build(source_sha=self.SHA, unit=unit)
        self.assertEqual(r["gates"]["software_unit_tests"], "FAIL")

    def test_report_rejects_fake_unit_schema(self):
        with self.assertRaises(ValueError):
            report.build(unit={"schema": "fake", "provenance": "host_python_unittest"})


if __name__ == "__main__":
    unittest.main()
