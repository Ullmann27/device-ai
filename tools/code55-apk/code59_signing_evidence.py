"""Recheck downloaded evidence for the fixed Code59 and manual Code60 profiles."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

import native_hologram_evidence
import native_routines
import native_web_offline
import validator


def require(condition, message):
    if not condition:
        raise ValueError(message)


def release_profile(code=59):
    require(type(code) is int and code in (59, 60), "Unsupported release profile")
    return {"code": str(code), "version": {59: "0.3.58", 60: "0.3.59"}[code],
            "apk": f"Continuum-Code{code}-UNSIGNED-NOT-INSTALLABLE.apk",
            "evidence": f"code{code}-evidence", "prefix": f"code{code}"}


def required_host_cases(code=59):
    release_profile(code)
    composer = "at.heinz.deviceai.ContinuumCompactComposerUiTest"
    required = [(composer, name) for name in (
        "shortLandscapeKeepsEditorAndSendFullyVisible",
        "heightChangesKeepDraftSelectionAndOriginalControls",
        "narrowLargeTextKeepsEditingAndOriginalSend",
        "overflowPreservesActionsLongPressesAndDisabledState")]
    if code == 60:
        required += [("at.heinz.deviceai.ContinuumCommandNavigationUiTest", name) for name in (
            "sevenRealToolsAndFilePickerKeepAndroidReadOnlyPermission",
            "realRailSelectionRestoresOnlyTheSelectedContextAndKeepsTheSameAvatar",
            "rapidSelectionsRejectAnOlderQueuedReadAndItsPersistentPointer",
            "missingConversationLeavesVisibleChatContextAndSelectionIntact",
            "runningLocalRequestIsCancelledButTheChatWaitsForItsActualEnd",
            "pauseInvalidatesQueuedSelectionWithoutChangingTheStoredActiveChat",
            "newChatRetainsThePreviousSavedContextAndCanReopenIt",
            "theSingleRealComposerLivesOutsideBothScrollablePresentations",
            "responsiveInstrumentsStayOnCanvasBeforeFirstMeasureAndAfterRendererCallbacks",
            "conversationKeepsOneMountedPresenceAndBoundFeedWithoutHiddenTargets",
            "largeTextAndKeyboardKeepPresenceAndTheSingleEditorReachable",
        )]
        required += [("at.heinz.deviceai.ui.ContinuumChatHistoryTest", name) for name in (
            "indexContainsOnlyStoredMessagesInActualRecencyOrder",
            "readRestoresExactlyOneConversationWithoutChangingSelectionOrAppending",
            "missingEmptyAndTraversalSelectionsLeaveTheExistingConversationIntact",
            "navigationDoesNotPersistAResetOrLeakTheOtherChatsContext",
            "titleSurvivesTheRestoreWindowAndAHistoryRestart",
            "appendUpdatesOnlyItsCachedConversationUntilAnExplicitRefresh",
            "firstOwnerMessageReplacesGreetingButLaterMessagesDoNotRenameIt",
            "initialIndexOpensOnlyTheHundredMostRecentlyModifiedTranscripts",
        )]
        required += [("at.heinz.deviceai.ui.CinematicWorkstationLayoutTest", name) for name in (
            "conversationStripContainsOriginalPosedFaceAndCompleteRestRings",
            "conversationPresenceRetainsAValidMonitorFrameWithoutGhostControls",
            "originalSphereSurroundsTheSolidPosedHead",
            "completeMonitorFlightClearsActualPosedFaceAndSideWindows",
            "compactBadgesRemainReadableAtMaximumFontScale",
            "instrumentsFlankLargeStagesAndReflowBelowTheHeadOnSmallFold",
            "sevenNativeDestinationsSitAboveTheSceneWithSharedTouchGeometry",
            "formerDockFunctionsRemainReachable",
            "resizingAndLargeTextKeepPositiveBoundsAndAllowScrolling",
            "compactWorkstationKeepsShorterGeometryAndAllNativeRoutes",
        )]
    return tuple(required)


def pairs(values):
    result = {}
    for key, value in values:
        require(key not in result, "Duplicate JSON field")
        result[key] = value
    return result


def read_json(path):
    def invalid(value):
        raise ValueError("Invalid JSON constant: " + value)
    return json.loads(Path(path).read_text(), object_pairs_hook=pairs, parse_constant=invalid)


def one(directory, name):
    found = list(Path(directory).rglob(name))
    require(len(found) == 1 and found[0].is_file() and not found[0].is_symlink(),
            "Missing or ambiguous proof: " + name)
    return found[0]


def fields(path):
    result = {}
    for line in path.read_text().splitlines():
        key, separator, value = line.partition("=")
        require(separator and key not in result, "Invalid receipt fields")
        result[key] = value
    return result


def source(directory, sha, tree):
    require((directory / "source-sha.txt").read_text().strip() == sha, "Native source mismatch")
    require((directory / "source-tree.txt").read_text().strip() == tree, "Native tree mismatch")


def instrumentation(path, sha, tree):
    source(path.parent, sha, tree)
    text = path.read_text()
    values = pairs(re.findall(r"^INSTRUMENTATION_RESULT: ([^=\r\n]+)=(.*?)\r?$", text, re.M))
    require(re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)\r?$", text, re.M) == ["-1"],
            "Native instrumentation did not complete")
    require("INSTRUMENTATION_FAILED" not in text and values.get("status") == "ok",
            "Native instrumentation failed")
    return values


def junit(directory, minimum, *, skipped=False, required=()):
    files = sorted(Path(directory).rglob("TEST-*.xml"))
    require(files and len({p.parent for p in files}) == 1, "Ambiguous JUnit directory")
    policy = read_json(Path(__file__).with_name("known-junit-skips.json"))
    allowed = {(name, case) for name, cases in policy.items() for case in cases}
    identities, passed, omitted = set(), set(), []
    for path in files:
        suite = ET.parse(path).getroot()
        cases = suite.findall("testcase")
        require(suite.tag == "testsuite" and int(suite.get("tests", "-1")) == len(cases),
                "Incomplete JUnit cases")
        require(not suite.findall(".//failure") and not suite.findall(".//error"), "JUnit failed")
        require(int(suite.get("failures", "-1")) == int(suite.get("errors", "-1")) == 0,
                "JUnit failure counters")
        skip_count = 0
        for case in cases:
            identity = (case.get("classname"), case.get("name"))
            require(all(identity) and identity not in identities, "Duplicate JUnit case")
            identities.add(identity)
            if case.find("skipped") is not None:
                require(skipped and identity in allowed, "Unapproved skipped case")
                omitted.append(identity)
                skip_count += 1
            else:
                passed.add(identity)
        require(int(suite.get("skipped", "-1")) == skip_count, "JUnit skip count mismatch")
    require(len(passed) >= minimum and set(required) <= passed, "Missing executed regression coverage")
    return {"tests": len(identities), "executed": len(passed), "failures": 0, "errors": 0,
            "skipped": len(omitted), "skipped_cases": omitted, "suite_files": len(files)}


def check_apk(directory, sha, tree, tools, repo, output, code=59):
    profile = release_profile(code)
    apk = one(directory, profile["apk"])
    require(fields(one(directory, "BUILD-INFO.txt")) == {
        "source": sha, "source_tree": tree, "versionCode": profile["code"], "versionName": profile["version"],
        "package": "at.heinz.deviceai.continuumlocal", "abi": "arm64-v8a",
        "status": "UNSIGNED_NOT_INSTALLABLE", "fold7_test": "NOT_RUN"}, "APK receipt mismatch")
    digest = validator.digest_file(apk)
    require(one(directory, "SHA256SUMS.txt").read_text() ==
            digest + "  " + profile["evidence"] + "/" + apk.name + "\n", "APK checksum mismatch")
    with tempfile.TemporaryDirectory(prefix="source-audit-", dir=output) as temporary:
        audit = Path(temporary)
        assets = repo / "app/src/main/assets"
        require(assets.is_dir() and not assets.is_symlink(), "Invalid source asset directory")
        shutil.copytree(assets, audit / "app/src/main/assets", symlinks=True)
        manifest = repo / "app/owner-face-models.lock.json"
        shutil.copyfile(manifest, audit / "app/owner-face-models.lock.json")
        generated = audit / "app/build/generated/owner-face-model-assets/faceid"
        generated.mkdir(parents=True)
        lock = read_json(manifest)
        require({m["id"] for m in lock["models"]} == {"yunet", "sface"}, "Unexpected locked models")
        with zipfile.ZipFile(apk) as zipped:
            require(zipped.read("assets/faceid/model-manifest.json") == manifest.read_bytes(),
                    "Packaged model manifest differs from source")
            (generated / "model-manifest.json").write_bytes(manifest.read_bytes())
            for model in lock["models"]:
                name = model["filename"]
                require(re.fullmatch(r"[A-Za-z0-9_.-]+[.]onnx", name), "Unsafe model filename")
                payload = zipped.read("assets/faceid/" + name)
                require(len(payload) == model["byteLength"] and
                        hashlib.sha256(payload).hexdigest() == model["sha256"], "Locked model hash mismatch")
                (generated / name).write_bytes(payload)
            expected = {"assets/" + p.relative_to(audit / "app/src/main/assets").as_posix()
                        for p in (audit / "app/src/main/assets").rglob("*") if p.is_file()}
            expected |= {"assets/faceid/" + p.name for p in generated.iterdir()}
            require({p.filename for p in zipped.infolist() if p.filename.startswith("assets/")
                     and not p.is_dir()} == expected, "Unexpected packaged assets")
        archive = validator.check_archive(apk, audit, output)
    libraries = read_json(one(directory, "NATIVE-LIBRARIES.json"))
    require(libraries == {"source_sha": sha, "status": "PASS",
                         "libraries": read_json(output / "native-libraries.json")}, "ELF proof mismatch")
    with zipfile.ZipFile(apk) as zipped:
        vcs = zipped.read("META-INF/version-control-info.textproto").decode()
        require(re.findall(r'revision: "([a-f0-9]{40})"', vcs) == [sha], "Embedded source mismatch")
        protected = {
            "assets/avatar3d/continuum-avatar.glb": "c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a",
            "assets/cinema3d/continuum-orb.glb": "fdfe54ce9aa2ed6af5ec6866c1078a4a34ece511e66b29cd1b3672f83229079c",
            "assets/cinema3d/live-monitor.filamat": "1b24ad728cecce23e9fca904c45370eb35ea5f9e2c0cd85ed3914f7490e27ac3"}
        for name, expected in protected.items():
            require(hashlib.sha256(zipped.read(name)).hexdigest() == expected, "Original asset changed")
    badging = subprocess.run([str(tools / "aapt2"), "dump", "badging", str(apk)],
                             text=True, capture_output=True, check=True).stdout
    require(re.findall(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'",
                       badging, re.M) == [("at.heinz.deviceai.continuumlocal", profile["code"], profile["version"])],
            "APK identity mismatch")
    require(re.findall(r"^native-code: (.+)$", badging, re.M) == ["'arm64-v8a'"], "APK ABI mismatch")
    require(re.findall(r"^(?:minSdkVersion|sdkVersion):'([^']+)'$", badging, re.M) == ["26"], "Wrong minSdk")
    require(re.findall(r"^targetSdkVersion:'([^']+)'$", badging, re.M) == ["36"], "Wrong targetSdk")
    require(not re.search(r"^application-debuggable(?:\s|$)", badging, re.M), "Debuggable release")
    subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "-v", "4", str(apk)],
                   check=True, capture_output=True)
    signature = subprocess.run([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)],
                               text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    validator.check_unsigned_diagnostic(signature.returncode, signature.stdout)
    return apk, {"source": sha, "tree": tree, "sha256": digest, "bytes": apk.stat().st_size,
                 "archive": archive, "zipalign16KiB": "PASS", "signing": "NOT_USED"}


def native(directory, sha, tree):
    require(read_json(directory / "probe-exits.json") ==
            {"source_sha": sha, "hologram": 0, "routines": 0, "app": 0}, "Native probe failed")
    pdf = instrumentation(one(directory, "native-pdf.txt"), sha, tree)
    require(pdf.get("sdk") == "35" and all(pdf.get(k) == "1" for k in
            ("provider_queries", "provider_opens", "page_count")) and
            int(pdf.get("rendered_ink_pixels", "0")) > 0, "Native PDF proof incomplete")
    filament_path = one(directory, "native-filament.txt")
    filament = instrumentation(filament_path, sha, tree)
    require(filament.get("last_phase") == "finished" and filament.get("sdk") == "35" and
            filament.get("context", "").startswith("SYNTHETIC FILAMENT TEST;"), "Filament probe incomplete")
    require(all(filament.get(k) == "2" for k in ("native_quads", "native_materials", "native_textures"))
            and filament.get("upload_completions") == "1", "Filament resources incomplete")
    require(all(int(filament.get(k, "0")) > 0 for k in ("rendered_frames", "presented_frames", "output_frames"))
            and all(filament.get(k) == "true" for k in ("content_cleared", "panels_released", "engine_released")),
            "Filament frames or cleanup missing")
    reports = {name: read_json(directory / name / "RESULT.json") for name in ("app", "routines")}
    for report in reports.values():
        require(report["status"] == "PASS" and not report.get("cleanup_errors") and not report.get("error"),
                "Native app or routine proof failed")
        require(report["source_sha"] == sha and report["source_tree"] == tree and
                report["package"] == "at.heinz.deviceai.mentorpaired" and report["sdk"] == 35 and
                report["abi"] == "x86_64", "Native report identity mismatch")
    require(reports["app"]["apk_sha256"] == reports["routines"]["apk_sha256"], "Mixed native test APKs")
    hologram = native_hologram_evidence.verify_hologram(
        directory / "hologram", sha, tree, reports["app"]["apk_sha256"])
    routine = native_routines.verify(directory / "routines", reports["routines"], sha, tree)
    require(routine == reports["routines"]["routine_checks"], "Routine receipt mismatch")
    app_dir, app = directory / "app", reports["app"]
    web = native_web_offline.verify(app_dir, app, sha, tree)
    require(web == app["offline_web"], "Offline receipt mismatch")
    required = ["fresh-empty-no-permissions", "setup-dismissed", "camera-declined", "conversation",
                "conversation-keyboard-dismissed", "start-roundtrip", "rotation", "process-restart", "offline-restart"]
    require([c["name"] for c in app["checks"]] == required and
            all(c["status"] == "PASS" for c in app["checks"]), "Native lifecycle coverage missing")
    windows = {}
    for label in required + ["conversation-input", "landscape-conversation", "landscape-start-roundtrip"]:
        _, windows[label] = native_routines.text_nodes(app_dir, label)
    pid = windows[required[0]]["app_pid_before"]
    require(all(windows[k]["app_pid_before"] == pid for k in required[:-2]), "Unexpected process replacement")
    require(windows["process-restart"]["app_pid_before"] != pid and
            windows["offline-restart"]["app_pid_before"] != windows["process-restart"]["app_pid_before"],
            "Actual process restart missing")
    portrait, landscape = windows["start-roundtrip"], windows["rotation"]
    require(portrait["screenshot_width"] == landscape["screenshot_height"] and
            portrait["screenshot_height"] == landscape["screenshot_width"], "Rotation proof missing")
    frame = windows["landscape-conversation"]
    size = (int(frame["screenshot_width"]), int(frame["screenshot_height"]))
    nodes = list(ET.parse(app_dir / "landscape-conversation.xml").iter("node"))
    for predicate in (lambda n: n.get("class") == "android.widget.EditText",
                      lambda n: n.get("content-desc") == "Nachricht senden"):
        require(sum(predicate(n) and n.get("enabled") == "true" and
                    bool(native_web_offline.visible(n, size)) for n in nodes) == 1,
                "Landscape composer or Send missing")
    return {"pdf": "PASS", "filament": "PASS", "hologram": hologram,
            "routines": routine, "app": "PASS", "offline_web": web, "physical_device": "NOT_RUN"}


def verify(root, sha, tree, tools, repo, code=59):
    profile = release_profile(code)
    require(re.fullmatch("[a-f0-9]{40}", sha) and re.fullmatch("[a-f0-9]{40}", tree), "Invalid source IDs")
    before = validator.source_identity(repo, os.environ.copy(), sha, tree)
    for path in root.rglob("*.json"):
        read_json(path)
    required = required_host_cases(code)
    host = junit(root / "host", 5832, skipped=True, required=required)
    context = junit(root / "context", 400, required=[
        ("at.heinz.deviceai.cognitive.OwnerDialogueOutputTest", "ownerFactoryRejectsUncapturedOverflowInsteadOfClipping"),
        ("at.heinz.deviceai.cognitive.LocalLongAnswerFlowTest", "nativeTokenLimitCannotBecomeACompleteAnswer")])
    renderer = junit(root / "renderer", 32)
    lint = ET.parse(one(root / "host", "lint-results-debug.xml")).getroot()
    require(lint.tag == "issues" and not any(issue.get("severity") in ("Error", "Fatal")
            for issue in lint.findall("issue")), "Android lint failed")
    apk, proof = check_apk(root / "unsigned", sha, tree, tools, repo, root, code)
    runtime = native(root / "native", sha, tree)
    require(validator.source_identity(repo, os.environ.copy(), sha, tree) == before, "Source changed during proof")
    require(validator.digest_file(apk) == proof["sha256"], "APK changed during proof")
    result = {"status": "PASS", "source": sha, "tree": tree, "android": host,
              "context": context, "renderer": renderer, "native": runtime, "apk": proof}
    (root / (profile["prefix"] + "-unsigned-path.txt")).write_text(str(apk.resolve()) + "\n")
    validator.write_json(root / (profile["prefix"] + "-apk-proof.json"), proof)
    validator.write_json(root / (profile["prefix"] + "-junit.json"), host)
    validator.write_json(root / (profile["prefix"] + "-evidence.json"), result)
    return result


if __name__ == "__main__":
    require(len(sys.argv) in (5, 6), "Expected evidence, SHA, tree, build tools and optional fixed profile")
    result = verify(Path(sys.argv[1]), sys.argv[2], sys.argv[3], Path(sys.argv[4]), Path.cwd(), int(sys.argv[5]) if len(sys.argv) == 6 else 59)
    print(json.dumps({"status": result["status"], "source": result["source"],
                      "android": result["android"], "unsigned_sha256": result["apk"]["sha256"]}))
