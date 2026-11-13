"""Summarize actual Gradle JUnit XML."""
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

strict = "--strict-skips" in sys.argv[2:]
policy = json.loads(Path(__file__).with_name("known-junit-skips.json").read_text())
allowed = {(name, case) for name, cases in policy.items() for case in cases}
directory = Path(sys.argv[1])
files = sorted(directory.glob("TEST-*.xml"))
if not files:
    raise SystemExit("No JUnit XML results")
totals = dict(tests=0, failures=0, errors=0, skipped=0)
failed = []
skipped = []
cases_seen = 0
for path in files:
    root = ET.parse(path).getroot()
    if root.tag != "testsuite":
        raise SystemExit("Unexpected JUnit root: " + path.name)
    for key in totals:
        totals[key] += int(root.get(key, "0"))
    cases = root.findall("testcase")
    if int(root.get("tests", "0")) != len(cases):
        raise SystemExit("Incomplete JUnit cases: " + path.name)
    cases_seen += len(cases)
    for case in cases:
        if case.find("skipped") is not None:
            skipped.append((case.get("classname"), case.get("name")))
        problems = case.findall("failure") + case.findall("error")
        if problems:
            failed.append({
                "class": case.get("classname"),
                "case": case.get("name"),
                "message": problems[0].get("message", "")[:600],
            })
receipt = {
    "source_sha": os.environ["GITHUB_SHA"],
    "suite_files": len(files),
    **totals,
    "failed_cases": failed,
    "skipped_cases": skipped,
    "unexpected_skips": [case for case in skipped if case not in allowed],
    "executed": cases_seen - len(skipped),
}
print(json.dumps(receipt, ensure_ascii=False, sort_keys=True))
if totals["skipped"] != len(skipped):
    raise SystemExit("Inconsistent skipped count")
if cases_seen <= len(skipped) or totals["failures"] or totals["errors"] or (
    strict and receipt["unexpected_skips"]
):
    raise SystemExit(1)
