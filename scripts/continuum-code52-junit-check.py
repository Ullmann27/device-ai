#!/usr/bin/env python3
"""Source-bound Code52 JUnit gate: never claim Android PASS from a missing or red test."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BASELINE_MIN = 4200
MAX_HISTORIC_SKIPS = 18
REQUIRED_CASES = {
    "at.heinz.deviceai.cognitive.OwnerAppLookupIntentTest#negatedSideEffectsDoNotBlockReadOnlyInstalledAppLookup",
    "at.heinz.deviceai.cognitive.OwnerAppActionFlowTest#readOnlyDiscoveryNeverSuggestsImplicitOpeningOrExecutesLaunch",
    "at.heinz.deviceai.cognitive.PreviousBatteryObservationTest#freshComparisonMustMeasureExactlyOnceAndUseSessionEvidence",
    "at.heinz.deviceai.cognitive.TurnScopedInternetModeTest#offlineLocalDialogueIsNotWebRevocationAndWebWorkStillIs",
    "at.heinz.deviceai.BootstrapBrainTest#positiveBatteryRemeasureWithComparisonIsNotBlockedAsHistoricalRecall",
    "at.heinz.deviceai.BootstrapBrainNegationDeEnTest#affirmativeRequestsStillReachTheirReadOnlyTool",
}
REQUIRED_CLASSES = {
    "at.heinz.deviceai.avatar3d.AvatarSurfaceRadianceTest",
    "at.heinz.deviceai.WhatsAppDraftPolicyTest",
    "at.heinz.deviceai.ui.HollywoodFuiWindowUiTest",
    "at.heinz.deviceai.chief.ChiefBotTeamTest",
}


def verify(directory: Path) -> dict[str, int]:
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise ValueError("No JUnit XML found")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    seen: set[str] = set()
    classes: set[str] = set()
    for file in files:
        suite = ET.parse(file).getroot()
        if suite.tag != "testsuite":
            raise ValueError(f"Bad suite root: {file.name}")
        cases = suite.findall("testcase")
        observed = {
            "tests": len(cases),
            "failures": sum(c.find("failure") is not None for c in cases),
            "errors": sum(c.find("error") is not None for c in cases),
            "skipped": sum(c.find("skipped") is not None for c in cases),
        }
        for key, count in observed.items():
            if int(suite.attrib.get(key, "-1")) != count:
                raise ValueError(f"JUnit totals mismatch: {file.name} {key}")
            totals[key] += count
        for case in cases:
            name = case.attrib.get("name", "")
            classname = case.attrib.get("classname", "")
            identity = classname + "#" + name
            if not name or not classname or identity in seen:
                raise ValueError(f"Missing/duplicate JUnit identity: {identity}")
            seen.add(identity)
            classes.add(classname)
    if totals["tests"] < BASELINE_MIN:
        raise ValueError(f"Code52 suite below prior release baseline: {totals}")
    if totals["failures"] or totals["errors"] or totals["skipped"] > MAX_HISTORIC_SKIPS:
        raise ValueError(f"Code52 test gate failed: {totals}")
    if not REQUIRED_CASES <= seen:
        raise ValueError(f"Code52 regression cases missing: {sorted(REQUIRED_CASES - seen)}")
    if not REQUIRED_CLASSES <= classes:
        raise ValueError(f"Code52 feature suites missing: {sorted(REQUIRED_CLASSES - classes)}")
    return totals


if __name__ == "__main__":
    try:
        totals = verify(Path(sys.argv[1]))
        Path(sys.argv[2]).write_text(
            "\n".join(f"{key}={value}" for key, value in totals.items()) + "\n",
            encoding="utf-8",
        )
        print(f"CODE52_ANDROID_JUNIT_PASS {totals}", flush=True)
    except (IndexError, OSError, KeyError, ValueError, ET.ParseError) as error:
        raise SystemExit(f"CODE52_ANDROID_JUNIT_REJECTED: {error}")
