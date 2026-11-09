#!/usr/bin/env python3
"""Verify the frozen Code51 Android test inventory, including explicit skip identities."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


EXPECTED_TESTS = 4197
SKIPS = {
    "at.heinz.deviceai.learning.Cre1InferenceEncoderTest#trainedNodeCheckpointMatchesKotlinFeaturesAndScores",
    "at.heinz.deviceai.learning.ContinuumContrastiveParityTest#actualNodeKotlinNodeTrainingParity",
    "at.heinz.deviceai.learning.ContinuumContrastiveParityTest#actualNodeKotlinNodeSwb2TrainingParityAfter350Rounds",
    "at.heinz.deviceai.chief.ChiefPatternE2ETest#s7_pending_agentMessagesUrgentWakesInfoWaits",
    "at.heinz.deviceai.chief.ChiefPatternE2ETest#s4b_pending_profileDiarySharedLayers",
    "at.heinz.deviceai.chief.ChiefPatternE2ETest#s8d_pending_limitGuardIsWiredBeforeWorkerStart",
    "at.heinz.deviceai.chief.ChiefPatternE2ETest#s5b_pending_textFileSkillLibraryLoadedOnDemand",
    "at.heinz.deviceai.chief.ChiefPatternE2ETest#s6_pending_routineReportsOnlyWhenSomethingIsNew",
    "at.heinz.deviceai.chief.ChiefPatternE2ETest#s2b_pending_workerHasOwnBriefOwnToolAllowlistAndReportsBack",
    "at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest#actualPoolMailboxAndTwoFixedNodeContainersReturnOnlyObservations",
    "at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest#cancellationOfRealContainerClosesRunnerAndReportsToChiefMailbox",
    "at.heinz.deviceai.ContinuumVisualEvidenceUiTest#fold7InnerMotionClip",
    "at.heinz.deviceai.chief.FirstThinkingRunE2ETest#schritt4d_handeln_eingeschleusterBefehlInWerkzeugausgabeWirdIgnoriert",
    "at.heinz.deviceai.chief.FirstThinkingRunE2ETest#schritt1b_ziel_turnLifecycleQuittiertGenauEinmal",
    "at.heinz.deviceai.chief.FirstThinkingRunE2ETest#schritt4c_handeln_alsChiefWorkerPoolJobMitWerkzeugScope",
    "at.heinz.deviceai.chief.FirstThinkingRunE2ETest#schritt6b_lernen_hypotheseUndTagebuchEintrag",
    "at.heinz.deviceai.chief.FirstThinkingRunE2ETest#schritt3b_erinnern_geteiltesWissenUeberLayersSharedSearch",
    "at.heinz.deviceai.chief.FirstThinkingRunE2ETest#schritt7b_naechsterSchritt_wirdStrukturiertGeplantUndAngestossen",
}
REQUIRED = {
    "at.heinz.deviceai.chief.ChiefBotTeamTest",
    "at.heinz.deviceai.chief.ChiefBotTeamSkillsTest",
    "at.heinz.deviceai.chief.ChiefBotConversationTest",
    "at.heinz.deviceai.ContinuumBotRosterStorageUiTest",
    "at.heinz.deviceai.ContinuumBotTaskRoutingUiTest",
    "at.heinz.deviceai.ui.HollywoodFuiWindowUiTest",
    "at.heinz.deviceai.JarvisWorkWindowLifecycleUiTest",
    "at.heinz.deviceai.localmodel.LocalModelReloadLifecycleTest",
    "at.heinz.deviceai.cognitive.SituationReturnMorphologyTest",
    "at.heinz.deviceai.avatar3d.AvatarCoreLightingTest",
    "at.heinz.deviceai.ui.JarvisWindowGazeTest",
}


def verify(directory):
    seen, skipped, classes = set(), set(), set()
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in sorted(Path(directory).glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite":
            raise ValueError(f"Unexpected JUnit root: {path.name}")
        cases = suite.findall("testcase")
        observed = {
            "tests": len(cases),
            "failures": sum(c.find("failure") is not None for c in cases),
            "errors": sum(c.find("error") is not None for c in cases),
            "skipped": sum(c.find("skipped") is not None for c in cases),
        }
        for key, count in observed.items():
            if int(suite.attrib[key]) != count:
                raise ValueError(f"JUnit count mismatch: {path.name}/{key}")
            totals[key] += count
        for case in cases:
            classname, name = case.attrib["classname"], case.attrib["name"]
            identity = classname + "#" + name
            if not classname or not name or identity in seen:
                raise ValueError(f"Missing/duplicate test identity: {identity}")
            seen.add(identity)
            classes.add(classname)
            if case.find("skipped") is not None:
                skipped.add(identity)
    if totals != dict(tests=EXPECTED_TESTS, failures=0, errors=0, skipped=len(SKIPS)):
        raise ValueError(f"Code51 JUnit totals rejected: {totals}")
    if skipped != SKIPS:
        raise ValueError(f"Skip inventory changed: {sorted(skipped ^ SKIPS)}")
    if not REQUIRED <= classes:
        raise ValueError(f"Required suites missing: {sorted(REQUIRED - classes)}")
    return totals


if __name__ == "__main__":
    try:
        result = verify(sys.argv[1])
        Path(sys.argv[2]).write_text(
            f"tests_total={result['tests']}\ntests_failed=0\ntests_errors=0\n"
            f"tests_skipped={result['skipped']}\nlifecycle_jdk21=PASS\n",
            encoding="utf-8",
        )
        print("CODE51_ANDROID_JUNIT_PASS " + json.dumps(result, sort_keys=True))
    except (ValueError, KeyError, OSError, ET.ParseError) as failure:
        raise SystemExit(f"CODE51_ANDROID_JUNIT_REJECTED: {failure}")
