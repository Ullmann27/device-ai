"""Build and publish only isolated evidence."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BRANCH = "work/blacksite-room-20261113"
ASSETS = (
    "app/src/main/assets/cinema3d/cockpit-workstation.glb",
    "app/src/main/assets/cinema3d/unified-assets-manifest.json",
    "app/src/main/assets/cinema3d/cockpit-world.json",
)
PRESERVED = (
    "tools/cockpit-workstation/source/pr801-workstation.glb",
    "tools/cockpit-workstation/source/pr801-workstation.json",
    "app/src/main/assets/avatar3d/continuum-avatar.glb",
    "app/src/main/assets/cinema3d/continuum-orb.glb",
    "app/src/main/assets/cinema3d/live-monitor.filamat",
    "app/src/main/assets/cinema3d/document-workspace.glb",
)
EVIDENCE = "docs/evidence/blacksite-room-20261113"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def run(command, log=None):
    print("RUN", json.dumps([str(arg) for arg in command]), flush=True)
    if log:
        with log.open("w") as stream:
            result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
        if result.returncode:
            print(log.read_text()[-12000:], flush=True)
            raise RuntimeError("Command failed: " + str(command[0]))
    else:
        subprocess.run(command, cwd=ROOT, check=True)


def source_guard(source):
    assert git("rev-parse", source) == source
    remote = git("ls-remote", "--heads", "origin", "refs/heads/" + BRANCH).split()
    assert len(remote) == 2 and remote[0] == source, "Source branch advanced; publishing refused"


def only_changes(allowed):
    names = set(git("diff", "--name-only", "HEAD").splitlines())
    names.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    assert names <= set(allowed), ("Unexpected source mutation", sorted(names - set(allowed)))



RECOVERY_SOURCE = "95df8a74818c8f0c6f632066221afee234bdf762"
RECOVERY_ZIP_SHA = "cb3ec00e4e3ba77462440b479cafa65d83f8bdf85f1afe699b8ed6ce0f927d7e"


def recover_render(work, preserved):
    archive = Path(os.environ["BLACKSITE_RECOVER_ZIP"])
    assert sha(archive) == RECOVERY_ZIP_SHA, "Recovery archive digest mismatch"
    destination = work / "rendered-from-95df8a74"
    destination.mkdir()
    with zipfile.ZipFile(archive) as zipped:
        entries = zipped.infolist()
        assert len(entries) <= 40 and sum(item.file_size for item in entries) <= 32_000_000
        for item in entries:
            path = Path(item.filename)
            assert not path.is_absolute() and ".." not in path.parts
            assert path.parts[0] in ("blacksite-evidence", "blacksite-toolchain.json")
            assert (item.external_attr >> 16) & 0o170000 != 0o120000
        zipped.extractall(destination)
    original = destination / "blacksite-evidence"
    receipt = json.loads((original / "receipt.json").read_text())
    assert receipt["status"] == "HOST_ROOM_RENDERED" and receipt["sourceStable"] is True
    assert receipt["buildSourceCommit"] == RECOVERY_SOURCE
    assert receipt["preservedAssets"] == preserved
    assert re.fullmatch(r"[a-f0-9]{40}", receipt["assetCommit"])
    expected = receipt["files"]
    actual = {str(path.relative_to(original)) for path in original.rglob("*") if path.is_file()}
    assert actual == set(expected) | {"receipt.json"}, "Recovery evidence file set differs"
    for name, digest in expected.items():
        assert sha(original / name) == digest, ("Recovery evidence digest", name)
    preview = original / "preview"
    manifest = json.loads((preview / "preview-manifest.json").read_text())
    assert manifest["sourceCommit"] == receipt["assetCommit"]
    assert manifest["sourceStableDuringRun"] is True and manifest["sourceWorkingTree"] == ""
    for name, digest in manifest["sourceFilesSha256"].items():
        assert sha(ROOT / name) == digest, ("Render input changed", name)
    for name, digest in manifest["toolFilesSha256"].items():
        assert sha(ROOT / "tools/cockpit-workstation" / name) == digest, ("Render tool changed", name)
    for name in ("build_unified_assets.py", "verify_unified_assets.py", "test_blacksite_contract.py"):
        path = "tools/cockpit-workstation/" + name
        assert git("rev-parse", RECOVERY_SOURCE + ":" + path) == git("rev-parse", "HEAD:" + path)
    assert json.loads((original / "geometry.json").read_text()) == json.loads((work / "geometry.json").read_text())
    return preview, receipt


def inspect_clip(preview, work):
    clip = preview / "fold-monitor-motion.mp4"
    probe = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-count_frames",
        "-show_entries", "stream=width,height,avg_frame_rate,nb_read_frames,duration",
        "-of", "json", str(clip)], text=True))
    stream, = probe["streams"]
    assert stream["nb_read_frames"] == "60" and stream["avg_frame_rate"] == "20/1"
    assert abs(float(stream["duration"]) - 3.) < .0001
    contact = work / "encoded-motion-contact-sheet.png"
    run(["ffmpeg", "-v", "error", "-y", "-i", str(clip),
         "-vf", r"select=not(mod(n\,4)),scale=354:-2,tile=5x3",
         "-frames:v", "1", "-update", "1", str(contact)])
    probe.update({
        "scope": "Decoded host export; no device FPS measurement",
        "clipSha256": sha(clip), "contactSheetSha256": sha(contact),
        "contactFrameIndices": list(range(0, 60, 4)),
    })
    (work / "encoded-motion-probe.json").write_text(json.dumps(probe, indent=2) + "\n")


def main():
    source = os.environ["GITHUB_SHA"]
    assert re.fullmatch(r"[a-f0-9]{40}", source)
    assert os.environ["GITHUB_REPOSITORY"] == "Ullmann27/device-ai"
    assert os.environ["GITHUB_REF"] == "refs/heads/" + BRANCH
    assert git("rev-parse", "HEAD") == source
    assert not git("status", "--porcelain", "--untracked-files=normal")
    source_guard(source)
    work = Path(os.environ["RUNNER_TEMP"]) / "blacksite-evidence"
    work.mkdir(parents=True, exist_ok=False)
    preserved = {path: sha(ROOT / path) for path in PRESERVED}
    (work / "source.json").write_text(json.dumps({
        "sourceCommit": source, "sourceTree": git("rev-parse", "HEAD^{tree}"),
        "branch": BRANCH, "preservedAssets": preserved,
        "evidenceClass": "HOST_ROOM_BUILD_NOT_DEVICE_VERIFICATION",
    }, indent=2) + "\n")
    run(["python3", "tools/cockpit-workstation/verify_scene.py"], work / "seed-validation.log")
    run(["blender", "-b", "-t", "2", "--python-exit-code", "1", "--python",
         "tools/cockpit-workstation/build_unified_assets.py", "--", "--workstation-only"],
        work / "build.log")
    run(["python3", "tools/cockpit-workstation/verify_unified_assets.py", "--write-manifest",
         "--require-blacksite"], work / "geometry.json")
    run(["python3", "tools/cockpit-workstation/verify_unified_assets.py", "--require-blacksite"],
        work / "geometry-recheck.json")
    run(["python3", "-m", "unittest", "discover", "-s", "tools/cockpit-workstation",
         "-p", "test_blacksite_contract.py", "-v"], work / "mutation-tests.log")
    assert preserved == {path: sha(ROOT / path) for path in PRESERVED}, "Original asset changed"
    only_changes(ASSETS)
    source_guard(source)
    run(["git", "config", "user.name", "github-actions[bot]"])
    run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"])
    run(["git", "add", "--", *ASSETS])
    staged = set(git("diff", "--cached", "--name-only").splitlines())
    assert staged and staged <= set(ASSETS)
    run(["git", "commit", "-m", "Build source-bound BLACKSITE room assets"])
    asset_commit = git("rev-parse", "HEAD")
    assert not git("status", "--porcelain", "--untracked-files=normal")
    recovered = None
    if os.environ.get("BLACKSITE_RECOVER_ZIP"):
        preview, recovered = recover_render(work, preserved)
    else:
        preview = work / "preview"
        run(["python3", "tools/cockpit-workstation/preview.py", "--repo-root", str(ROOT),
             "--output", str(preview), "--kotlin-lib-dir", os.environ["KOTLIN_LIB_DIR"],
             "--json-jar", os.environ["JSON_JAR"], "--android-jar", os.environ["ANDROID_JAR"],
             "--samples", "32", "--video", "--video-samples", "12", "--content", "example",
             "--qa-lock", str(Path(os.environ["RUNNER_TEMP"]) / "blacksite-render.lock")],
            work / "preview.log")
    manifest = json.loads((preview / "preview-manifest.json").read_text())
    render_commit = recovered["assetCommit"] if recovered else asset_commit
    assert manifest["sourceCommit"] == render_commit and manifest["sourceStableDuringRun"] is True
    assert manifest["sourceWorkingTree"] == ""
    assert manifest["Filament"] == "NOT_RUN" and manifest["Fold7"] == "NOT_RUN"
    renders = json.loads((preview / "blender-manifest.json").read_text())
    assert len(renders["frames"]) == 66
    assert all(frame["blacksiteVisibility"]["state"] == "PASS" for frame in renders["frames"])
    assert len(list(preview.glob("*.png"))) == 6
    assert (preview / "fold-monitor-motion.mp4").is_file()
    inspect_clip(preview, work)
    assert git("rev-parse", "HEAD") == asset_commit
    assert not git("status", "--porcelain", "--untracked-files=normal")
    source_guard(source)
    toolchain = Path(os.environ["RUNNER_TEMP"]) / "blacksite-toolchain.json"
    assert toolchain.is_file()
    shutil.copyfile(toolchain, work / "toolchain.json")
    receipt = {
        "status": "HOST_ROOM_RENDER_RECOVERED" if recovered else "HOST_ROOM_RENDERED",
        "buildSourceCommit": source, "renderSourceCommit": render_commit,
        "renderBuildSourceCommit": recovered["buildSourceCommit"] if recovered else source,
        "recoveryArtifactId": 14004669910 if recovered else None,
        "recoveryZipSha256": RECOVERY_ZIP_SHA if recovered else None,
        "renderInputEquivalence": "ALL_TRACKED_HASHES_MATCH" if recovered else "FRESH_RENDER",
        "assetCommit": asset_commit, "assetTree": git("rev-parse", "HEAD^{tree}"),
        "sourceStable": True, "stills": 6, "exportFrames": 60, "exportFps": 20,
        "durationSeconds": 3, "nativePanelConnection": "NOT_EXERCISED",
        "Filament": "NOT_RUN", "Fold7": "NOT_RUN", "deviceFps": "NOT_MEASURED",
        "preservedAssets": preserved,
        "files": {str(path.relative_to(work)): sha(path) for path in sorted(work.rglob("*")) if path.is_file()},
    }
    (work / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    destination = ROOT / EVIDENCE
    assert not destination.exists(), "Existing evidence must not be overwritten"
    shutil.copytree(work, destination)
    allowed = [str(path.relative_to(ROOT)) for path in destination.rglob("*") if path.is_file()]
    only_changes(allowed)
    run(["git", "add", "--force", "--", *allowed])
    staged = set(git("diff", "--cached", "--name-only").splitlines())
    assert staged == set(allowed), ("Evidence staging mismatch", sorted(set(allowed) - staged), sorted(staged - set(allowed)))
    run(["git", "commit", "-m", "Record BLACKSITE host geometry and motion evidence"])
    assert git("merge-base", source, "HEAD") == source
    source_guard(source)
    run(["git", "push", "origin", "HEAD:refs/heads/" + BRANCH])
    print("BLACKSITE_PUBLISHED", json.dumps({
        "head": git("rev-parse", "HEAD"), "assetCommit": asset_commit,
        "buildSourceCommit": source, "evidence": EVIDENCE,
        "device": "NOT_RUN", "nativePanelConnection": "NOT_EXERCISED",
    }), flush=True)


if __name__ == "__main__":
    main()
