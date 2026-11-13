#!/usr/bin/env python3
"""Print bounded QA receipts only."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

for argument in sys.argv[1:]:
    folder = Path(argument)
    path = folder / "report.json"
    if not path.is_file():
        print(json.dumps({"folder": str(folder), "status": "MISSING_EVIDENCE"}))
        continue
    report = json.loads(path.read_text())
    summary = {key: report.get(key) for key in ("profile", "status", "sourceUnchanged", "cancelled")}
    summary["source_sha"] = report.get("source", {}).get("commit")
    summary["checks"] = []
    for check in report.get("checks", []):
        item = {key: check.get(key) for key in ("id", "status", "reason", "code", "error", "terminationConfirmed")}
        item["tests"] = {key: value for key, value in (check.get("tests") or {}).items()
                         if key not in ("executedCaseHashes", "skippedCaseHashes", "diagnostics")}
        summary["checks"].append(item)
    print(json.dumps(summary, sort_keys=True))
    failures = 0
    for xml in sorted(folder.glob("*.xml")):
        for case in ET.parse(xml).getroot().iter("testcase"):
            for tag in ("failure", "error"):
                for failure in case.findall(tag):
                    failures += 1
                    if failures <= 30:
                        print(json.dumps({"file": case.get("file"), "test": case.get("name"),
                                          "type": failure.get("type"),
                                          "message": (failure.get("message") or "")[:1600],
                                          "detail": "\n".join((failure.text or "").splitlines()[:8])[:3000]}))
    print(json.dumps({"failed_cases": failures, "printed": min(30, failures)}))
