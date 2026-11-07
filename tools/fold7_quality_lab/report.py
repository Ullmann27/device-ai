#!/usr/bin/env python3
"""German acceptance report; never promotes simulations to device proof."""
import argparse
import json
import re
import sys
from pathlib import Path

from fold7 import SCHEMA as DEVICE_SCHEMA, compare as compare_device
from intelligence import SCHEMA as IQ_SCHEMA, compare_scores

CHECKS = ("restart", "fold_unfold", "rotation", "inner_display", "cover_display",
          "font_100", "font_150", "font_200", "inference", "long_run",
          "thermal_throttle", "energy_consumption")
VALID = ("PASS", "FAIL", "NOT_RUN")


def load(filename):
    return json.loads(Path(filename).read_text(encoding="utf-8")) if filename else None


def build(telemetry=None, baseline=None, score=None, baseline_score=None,
          apk=None, manual=None, source_sha=None, unit=None):
    if telemetry and telemetry.get("schema") != DEVICE_SCHEMA:
        raise ValueError("Telemetry schema mismatch")
    if score and score.get("schema") != IQ_SCHEMA:
        raise ValueError("Benchmark schema mismatch")
    checklist = {k: "NOT_RUN" for k in CHECKS}
    if manual is not None:
        if set(manual) - set(CHECKS) or any(v not in VALID for v in manual.values()):
            raise ValueError("Invalid checklist")
        checklist.update(manual)
    device_real = bool(telemetry and telemetry.get("provenance") == "device_adb_read_only")
    pkg = bool(device_real and telemetry.get("identity", {}).get("package_installed"))
    signature = bool(apk and apk.get("status") == "VERIFIED_LOCAL_APK"
                     and apk.get("package_match") is True and apk.get("certificate_match") is True)
    gates = {"device_telemetry": "MEASURED" if device_real else "NOT_RUN",
             "target_package": "PRESENT" if pkg else "NOT_VERIFIED",
             "original_apk_signature": "VERIFIED" if signature else "NOT_VERIFIED",
             "model_inference": "NOT_VERIFIED" if not score or not score.get("inference_execution_verified") else "RECEIPT_REPORTED",
             "android_host_tests": "NOT_RUN", "software_unit_tests": "NOT_RUN"}
    unit_source_bound = False
    if unit:
        if unit.get("schema") != "continuum.lab.unit.v1" or unit.get("provenance") != "host_python_unittest":
            raise ValueError("Invalid host unittest evidence")
        unit_source_bound = bool(isinstance(source_sha, str) and
                                 re.fullmatch(r"[0-9a-f]{40}", source_sha) and
                                 unit.get("source_sha") == source_sha and
                                 unit.get("source_verified") is True and
                                 unit.get("source_unchanged") is True)
        gates["software_unit_tests"] = "NOT_VERIFIED"
        if unit_source_bound:
            gates["software_unit_tests"] = ("PASS" if unit.get("status") == "PASS" and
                                              unit.get("total", 0) > 0 and unit.get("failed") == 0
                                              else "FAIL")
    comparison = compare_device(baseline, telemetry) if baseline and telemetry else None
    model_comparison = compare_scores(baseline_score, score) if baseline_score and score else None
    failed = [k for k, v in checklist.items() if v == "FAIL"]
    missing = [k for k, v in checklist.items() if v == "NOT_RUN"]
    release_ready = pkg and signature and not failed and not missing and gates["model_inference"] != "NOT_VERIFIED"
    # Manual checks alone are not cryptographic proof; require external review regardless.
    report_status = "REQUIRES_INDEPENDENT_SIGNOFF" if release_ready else "NOT_READY"
    return {"source_sha": source_sha, "overall": report_status, "gates": gates,
            "software_test_counts": {k: unit.get(k) for k in ("total", "passed", "failed", "skipped")} if unit else None,
            "software_test_source": {"source_sha": unit.get("source_sha"),
                                     "bound_to_report": unit_source_bound} if unit else None,
            "manual_self_declared": checklist, "manual_fail": failed, "manual_missing": missing,
            "telemetry_comparison": comparison, "benchmark": {
                "provenance": score.get("provenance") if score else None,
                "totals": score.get("totals") if score else None,
                "comparison": model_comparison},
            "warning": "Manual checklist entries are operator-declared, not tool-verified"}


def markdown(r):
    lines = ["# Continuum Fold7 – unabhängiger Abnahmebericht", "",
             f"Quellstand: `{r['source_sha'] or 'NICHT_ANGEGEBEN'}`", "",
             f"**Freigabestatus: {r['overall']}**", "", "## Nachweiskategorien", ""]
    for key, label in (("software_unit_tests", "Softwaretests (Host)"),
                       ("android_host_tests", "Simulierte Android-/Robolectric-Tests"),
                       ("device_telemetry", "Echte Gerätetelemetrie"),
                       ("target_package", "Originalpaket installiert"),
                       ("original_apk_signature", "Lokale APK-Signatur und Paketkennung"),
                       ("model_inference", "Verifizierte Modellinferenz")):
        lines.append(f"- {label}: **{r['gates'][key]}**")
    if r["software_test_counts"]:
        lines.append("- Host-Software-Testzahlen: " + json.dumps(r["software_test_counts"], ensure_ascii=False))
        lines.append("- Host-Quellzuordnung: " + json.dumps(r["software_test_source"], ensure_ascii=False))
    lines += ["", "## Manuelle Fold7-Abnahme (Selbstauskunft)", ""]
    for key, value in r["manual_self_declared"].items():
        lines.append(f"- {key}: {value}")
    lines += ["", "## Vergleich", ""]
    comp = r["telemetry_comparison"]
    lines.append("- Gerät: " + (comp["status"] if comp else "NOT_RUN"))
    if comp and comp.get("deltas"):
        for k, v in comp["deltas"].items():
            lines.append(f"  - {k}: Basis={v['baseline']}, Kandidat={v['candidate']}, Delta={v['delta']}")
    iq = r["benchmark"]
    lines.append("- Modellqualität: " + (iq["comparison"]["status"] if iq["comparison"] else "NOT_RUN"))
    if iq["totals"]:
        lines.append(f"- Bewertete Aufgaben: {iq['totals']['attempted']}/{iq['totals']['planned']} "
                     f"(richtig {iq['totals']['pass']}, falsch {iq['totals']['fail']}, "
                     f"nicht ausgeführt {iq['totals']['not_run']}); Quelle: {iq['provenance']}")
    lines += ["", "## Veröffentlichungssperren", "",
              "- Ausstehende manuelle Prüfungen: " + (", ".join(r["manual_missing"]) or "keine"),
              "- Fehlerhafte manuelle Prüfungen: " + (", ".join(r["manual_fail"]) or "keine"),
              "- Eine echte Modellausführung wird durch synthetische Sollantworten nicht nachgewiesen.",
              "- Telemetrie beweist weder Filament-GPU-Bild noch die korrekte Falten-/Drehen-Reaktion.",
              "- Manuelle PASS-Angaben sind keine unabhängigen Ausführungsquittungen.", ""]
    return "\n".join(lines)


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--telemetry"); p.add_argument("--baseline")
    p.add_argument("--score"); p.add_argument("--baseline-score")
    p.add_argument("--apk-check"); p.add_argument("--manual"); p.add_argument("--unit-results")
    p.add_argument("--source-sha"); p.add_argument("--output", required=True)
    a = p.parse_args(argv)
    r = build(load(a.telemetry), load(a.baseline), load(a.score), load(a.baseline_score),
              load(a.apk_check), load(a.manual), a.source_sha, load(a.unit_results))
    path = Path(a.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise FileExistsError("Refusing overwrite")
    path.write_text(markdown(r), encoding="utf-8")
    print(json.dumps({"file": str(path), "overall": r["overall"], "gates": r["gates"]},
                     ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
        print(f"REPORT_ERROR: {exc}", file=sys.stderr)
        sys.exit(2)
