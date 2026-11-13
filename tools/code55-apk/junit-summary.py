"""Summarize actual Gradle JUnit XML."""
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

directory = Path(sys.argv[1])
files = sorted(directory.glob("TEST-*.xml"))
if not files:
    raise SystemExit("No JUnit XML results")
totals = dict(tests=0, failures=0, errors=0, skipped=0)
failed = []
for path in files:
    root = ET.parse(path).getroot()
    if root.tag != "testsuite":
        raise SystemExit("Unexpected JUnit root: " + path.name)
    for key in totals:
        totals[key] += int(root.get(key, "0"))
    for case in root.findall("testcase"):
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
}
print(json.dumps(receipt, ensure_ascii=False, sort_keys=True))
if totals["tests"] <= 0 or totals["failures"] or totals["errors"]:
    raise SystemExit(1)
