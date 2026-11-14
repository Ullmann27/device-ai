#!/usr/bin/env python3
"""Validate fixed RAM and Chief regression runs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

SOURCES = {
    "red": ("4c7d849a5eb997cd42b64f0dcf6c6b7d32bd7c95", "099dc70b218344fe50d82d16abf7554950c89d5d"),
    "green": ("ca2c59a89864efc6ad54645ff6099d2e2b9e6b17", "4b10c4970c3949f16be6ca6f9eadbb00e49be956"),
}
PACKAGE = "at.heinz.deviceai.localmodel."
HOST = PACKAGE + "LocalModelHostMemoryUiTest"
THERMAL = PACKAGE + "LocalModelThermalGuardTest"
LOAD = PACKAGE + "LocalModelLoadAdmissionTest"
RED = {
    "loadedChatRechecksLowMemoryBeforeNativeDelegation": ("stream:owner-question", 95),
    "loadedThinkingRechecksLowMemoryBeforeNativeDelegation": ("thinking:worker-question", 96),
    "loadedToolContinuationRechecksLowMemoryBeforeNativeDelegation": ("tools:get_battery_info:{}", 97),
}
METHODS = {
    HOST: list(RED) + [
        "unavailableOrUnreadableMemoryBlocksEveryEntryWithoutUnloading",
        "unavailableThermalStatusStillChecksActualMemory",
        "thermalAdmissionAndRunningCancellationRemainEffective",
        "ownerCancellationStillReachesAnAlreadyRunningNativeGeneration",
        "rejectedConcurrentCallerCannotReplaceTheRunningNativeOwner",
    ],
    THERMAL: [
        "generationDoesNotStartWhileAndroidReportsSevereHeat",
        "runningGenerationStopsWhenAndroidReportsSevereHeat",
        "coolDeviceAndUnknownStatusKeepTheNormalPath",
        "rejectedSecondStartKeepsTheHeatReasonForTheRunningAnswer",
        "severeCallbackDuringSamplingRejectsStaleCoolReadingForEveryEntryPoint",
        "heatBeforeNativeAdmissionStopsOnceAfterStartForEveryEntryPoint",
        "admittedBlockingStartCanBeStoppedWithoutHoldingGuardLock",
        "rejectedNativeStartReleasesOwnershipForCoolOwnerRetry",
        "thermalCancellationKeepsOwnershipUntilNativeCancelReturns",
        "latchedHeatRejectsSuccessWhenNativeCompletionWinsCancellation",
    ],
    LOAD: [
        "severeThermalStateRefusesNativeLoad",
        "androidLowMemoryStateRefusesNativeLoad",
        "modelThatCannotStayResidentInPhysicalRamIsRefused",
        "missingTelemetryFailsClosedInsteadOfGuessing",
        "healthyFold7ClassDeviceStillLoadsTheOriginalGemma",
        "unknownThermalOnOldAndroidDoesNotBlockButMemoryStillChecked",
    ],
    "at.heinz.deviceai.chief.ChiefPatternE2ETest": [
        "s1_requestIsAcknowledgedBeforeWorkThenResultAndCleanTerminalEnd",
        "s1b_plainAnswerEndsWithTerminalStateAndNoToolCall",
        "s2_chiefStaysResponsiveWhileWorkerRunsAndCanCancelIt",
        "s2b_pending_workerHasOwnBriefOwnToolAllowlistAndReportsBack",
        "s3a_registrySchemaRejectsUnknownMissingExtraAndShellArguments",
        "s3b_riskyCallsRequireOwnerApprovalAndAreAnnouncedBeforeExecution",
        "s3c_injectedInstructionsInExternalContentAreTreatedAsDataNotCommands",
        "s4_searchFindsOlderDatedEntryAndRespectsPrivacyAndReachesTheModel",
        "s4b_datedPrivateRecordsAndApprovedSharedFactsUseRealWorkerPrompts",
        "s5_skillGraphOnlyRegisteredToolsDisabledByDefaultDependenciesFirst",
        "s5b_textFileSkillLibraryRechecksApprovalBetweenWorkerStages",
        "s6_approvedRoutineKeepsItsScheduleAndDeliversOnlyNewLowReadingsAcrossReopen",
        "s7_urgentWorkerReportWakesChiefWhileInfoWaitsAndNeitherGrantsTools",
        "s8a_deleteNeedsPolicyPlusExplicitStrongYes",
        "s8b_toolCallBudgetsAndStepLimitBoundTheRun",
        "s8c_resourceGuardRefusesHotOrLowMemoryAndUnmeasuredDevices",
    ],
    "at.heinz.deviceai.chief.ChiefResourceAdmissionUiTest": [
        "s8d_realResourceAdmissionStopsNewToolsButDeliversFinishedReports",
    ],
    "at.heinz.deviceai.ContinuumBotFollowupUiTest": [
        "actualResultCardOpensBoundNewDraftAndNeedsFreshUncheckedConsent",
        "unsupportedToolProposalShowsChiefRejectionWithoutNewDraftOrWork",
        "ownerSessionChangeInvalidatesVisibleApprovalBeforeCallback",
        "stopBetweenAcceptedUiAndQueuedSubmitStartsNoNewModel",
        "closingDuringChiefReviewCannotReopenOrExecuteTheOldDraft",
        "stopDiscardMakesRetainedApprovalButtonInert",
    ],
}
BLOBS = {
    "app/src/uiTest/java/at/heinz/deviceai/localmodel/LocalModelHostMemoryUiTest.kt":
        "58935f9bd0daa92159e7c94bcbf5a7ddd61b4c5e",
    "app/src/test/java/at/heinz/deviceai/localmodel/LocalModelThermalGuardTest.kt":
        "94238f0ba1fad756605e553dcfa6de0c5c8217f3",
    "app/src/test/java/at/heinz/deviceai/localmodel/LocalModelLoadAdmissionTest.kt":
        "6214442a4d9961f7196e6d164c5a766fd12689b5",
    "docs/evidence/omega-apk54-20261111/harness/test-memory.gradle":
        "c88000f50c5fff16025229d651f426f3bc7a49f5",
}
GREEN_BLOBS = {
    "app/src/test/java/at/heinz/deviceai/chief/ChiefPatternE2ETest.kt":
        "9dd89ed992fb7ae3f09ee0de4fae2011badca103",
    "app/src/uiTest/java/at/heinz/deviceai/chief/ChiefResourceAdmissionUiTest.kt":
        "38c0cb13ecf7f7372e162f592c1cfae8ba17deab",
    "app/src/uiTest/java/at/heinz/deviceai/ContinuumBotFollowupUiTest.kt":
        "85481fabcc8ab4b7c7ec964c57adab1ba3d668f8",
    "app/src/main/java/at/heinz/deviceai/localmodel/LocalModelHost.kt":
        "1e20bf39c68b2e36a6fd451a682dc3747461c842",
    "app/src/main/java/at/heinz/deviceai/ContinuumUiCopy.kt":
        "67bf3c93571f067f3bc6a523a2a6a1ae097a0d7a",
    "app/src/main/java/at/heinz/deviceai/ContinuumBotRosterDialog.kt":
        "0caea21a944202aced8ae8c4b8f9304f623545ed",
}
TASK = ":app:testDebugUnitTest"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(directory, *args):
    return subprocess.check_output(
        ["git", "-C", str(directory), *args], text=True, stderr=subprocess.STDOUT
    ).strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def selected(phase):
    groups = {HOST: list(RED)} if phase == "red" else METHODS
    return {(suite, method) for suite, methods in groups.items() for method in methods}


def init_text(phase):
    lines = [
        "gradle.projectsEvaluated {",
        "    def app = gradle.rootProject.findProject(':app')",
        "    if (app == null) throw new GradleException('Missing Android app')",
        "    app.tasks.withType(org.gradle.api.tasks.testing.Test).matching {",
        "        it.name == 'testDebugUnitTest'",
        "    }.configureEach {",
        "        filter {",
    ]
    lines += ["            includeTestsMatching '" + suite + "." + method + "'"
              for suite, method in sorted(selected(phase))]
    lines += ["            failOnNoMatchingTests = true", "        }", "    }", "}", ""]
    return "\n".join(lines)


def identity(phase, source):
    expected_sha, expected_tree = SOURCES[phase]
    require(git(source, "rev-parse", "HEAD") == expected_sha, "Wrong source commit")
    require(git(source, "rev-parse", "HEAD^{tree}") == expected_tree, "Wrong source tree")
    require(not git(source, "status", "--porcelain", "--untracked-files=no"),
            "Tracked source was modified")
    harness = Path(__file__).resolve().parents[2]
    workflow_sha = os.environ["GITHUB_SHA"]
    require(re.fullmatch(r"[0-9a-f]{40}", workflow_sha), "Missing workflow identity")
    require(git(harness, "rev-parse", "HEAD") == workflow_sha, "Wrong harness commit")
    require(not git(harness, "status", "--porcelain", "--untracked-files=no"),
            "Tracked harness was modified")
    observed = {}
    expected_blobs = {**BLOBS, **(GREEN_BLOBS if phase == "green" else {})}
    for name, expected_blob in expected_blobs.items():
        raw = (source / name).read_bytes()
        blob = hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()
        require(blob == expected_blob, "Changed source fixture: " + name)
        observed[name] = {"git_blob": blob, "sha256": hashlib.sha256(raw).hexdigest()}
    return {
        "phase": phase, "source_sha": expected_sha, "source_tree": expected_tree,
        "workflow_sha": workflow_sha, "run_id": os.environ["GITHUB_RUN_ID"],
        "run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "validator_sha256": digest(Path(__file__).resolve()),
        "source_files": observed,
    }


def evaluate(phase, source, evidence):
    destination = evidence / "junit"
    destination.mkdir(exist_ok=True)
    original = sorted((source / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    for path in original:
        shutil.copyfile(path, destination / path.name)
    current = identity(phase, source)
    require(json.loads((evidence / "source.json").read_text(encoding="utf-8")) == current,
            "Run identity changed")
    require((evidence / "tests.init.gradle").read_text(encoding="utf-8") == init_text(phase),
            "Test selection changed")
    require((evidence / "tee.exit").read_text(encoding="ascii").strip() == "0",
            "Original Gradle log was not preserved")
    exit_text = (evidence / "gradle.exit").read_text(encoding="ascii").strip()
    require(exit_text == ("1" if phase == "red" else "0"), "Unexpected Gradle exit")
    log = (evidence / "gradle.log").read_text(encoding="utf-8")
    failed_tasks = re.findall(r"^> Task (\S+) FAILED\s*$", log, re.MULTILINE)
    if phase == "red":
        require(failed_tasks == [TASK], "Failure outside the selected test task")
        require("Execution failed for task '" + TASK + "'." in log,
                "Missing actual test-task failure")
        require("There were failing tests." in log, "Missing JUnit task failure")
        require(re.search(r"^BUILD FAILED in .+$", log, re.MULTILINE),
                "Missing failed-build terminator")
    else:
        require(not failed_tasks, "Unexpected failed task")
        require(re.search(r"^> Task :app:testDebugUnitTest\s*$", log, re.MULTILINE),
                "Selected tests did not execute")
        require(re.search(r"^BUILD SUCCESSFUL in .+$", log, re.MULTILINE),
                "Missing successful-build terminator")
    expected = selected(phase)
    require(len(expected) == (3 if phase == "red" else 47), "Wrong declared method count")
    expected_suites = {suite for suite, _ in expected}
    require(len(original) == len(expected_suites), "Wrong number of original JUnit files")
    found = {}
    suite_names = set()
    observations = []
    for path in original:
        root = ET.fromstring(path.read_bytes())
        require(root.tag == "testsuite", "Unexpected JUnit root")
        suite = root.attrib.get("name")
        require(suite in expected_suites and suite not in suite_names,
                "Unexpected or duplicate suite")
        suite_names.add(suite)
        cases = root.findall("testcase")
        counts = {"tests": len(cases), "failures": 0, "errors": 0, "skipped": 0}
        for case in cases:
            key = (case.attrib.get("classname"), case.attrib.get("name"))
            require(key in expected and key not in found, "Unexpected or duplicate testcase")
            require(key[0] == suite, "Testcase suite mismatch")
            failures, errors, skips = case.findall("failure"), case.findall("error"), case.findall("skipped")
            counts["failures"] += len(failures)
            counts["errors"] += len(errors)
            counts["skipped"] += len(skips)
            require(not errors and not skips, "Error or skipped testcase: " + str(key))
            if phase == "red":
                require(len(failures) == 1, "Expected assertion was not reproduced")
                failure = failures[0]
                require(failure.attrib.get("type") == "java.lang.AssertionError",
                        "Non-assertion failure is not RED")
                label, method_line = RED[key[1]]
                message = "expected:<[" + label + "]> but was:<[" + label + ", " + label + "]>"
                require(failure.attrib.get("message") in (message, "java.lang.AssertionError: " + message),
                        "Wrong assertion result")
                trace = failure.text or ""
                require(trace.startswith("java.lang.AssertionError: " + message + "\n"),
                        "Missing original assertion trace")
                frames = re.findall(
                    r"^\s*at " + re.escape(HOST) + r"\.(\w+)\(LocalModelHostMemoryUiTest.kt:(\d+)\)",
                    trace, re.MULTILINE)
                require(frames and frames[0] == ("changedMemory", "111"),
                        "Wrong failing assertion location")
                require((key[1], str(method_line)) in frames, "Wrong original test entry")
            else:
                require(not failures, "Repaired testcase failed: " + str(key))
            found[key] = True
            observations.append({"class": key[0], "method": key[1],
                                 "outcome": "EXPECTED_ASSERTION" if phase == "red" else "PASS"})
        for count, actual in counts.items():
            require(root.attrib.get(count) == str(actual), "JUnit count mismatch: " + count)
    require(set(found) == expected, "Missing required testcase")
    current.update({
        "status": "RED_REPRODUCED" if phase == "red" else "GREEN_PASSED",
        "tests": len(found), "failures": len(found) if phase == "red" else 0,
        "errors": 0, "skipped": 0, "gradle_exit": int(exit_text),
        "gradle_log_sha256": digest(evidence / "gradle.log"),
        "junit": [{"file": path.name, "sha256": digest(path), "bytes": path.stat().st_size}
                  for path in original],
        "testcases": sorted(observations, key=lambda item: (item["class"], item["method"])),
    })
    return current


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("prepare", "verify"))
    parser.add_argument("--phase", choices=tuple(SOURCES), required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    try:
        if args.command == "prepare":
            snapshot = identity(args.phase, args.source)
            require(not list((args.source / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")),
                    "Stale test results")
            (args.evidence / "tests.init.gradle").write_text(init_text(args.phase), encoding="utf-8")
            write_json(args.evidence / "source.json", snapshot)
        else:
            result = evaluate(args.phase, args.source, args.evidence)
            write_json(args.evidence / "RESULT.json", result)
            print(json.dumps(result, sort_keys=True))
    except Exception as error:
        result = {
            "status": "REJECTED", "phase": args.phase,
            "expected_source_sha": SOURCES[args.phase][0],
            "reason": type(error).__name__ + ": " + str(error),
        }
        write_json(args.evidence / "RESULT.json", result)
        print(json.dumps(result, sort_keys=True), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
