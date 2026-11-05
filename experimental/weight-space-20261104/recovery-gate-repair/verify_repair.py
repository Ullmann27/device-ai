#!/usr/bin/env python3
"""Repeat the source-bound repair checks without running any model process.

Only Python control-flow probes and Git operate here. The patch is applied to
temporary copies of its three pinned sources, never to a live study checkout.
The published-result audit reads only its explicitly allowed text sources.
"""
from __future__ import annotations

import argparse
import ast
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
PIN = "be4e49600fed1db4c697230bb8e42c150dbfbdaf"
PATCH_SHA256 = "c69cb427e4ba0624720380edef9f7f0ce3e418591c10bd0a35f56590ad67e38f"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise RuntimeError(message)


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def run(command: list[str], cwd: Path = REPO, expected_exit: int = 0) -> dict:
    result = subprocess.run(command, cwd=cwd, capture_output=True, text=True, timeout=60)
    require(result.returncode == expected_exit,
            f"unexpected exit {result.returncode}: {command}\n{result.stdout}\n{result.stderr}")
    return {"command": command, "exitCode": result.returncode,
            "stdout": result.stdout, "stderr": result.stderr}


def method_changes(original: bytes, repaired: bytes) -> dict:
    before, after = ast.parse(original), ast.parse(repaired)
    require(len(before.body) == len(after.body), "unexpected module-level change")
    changed = []
    for old, new in zip(before.body, after.body):
        if not isinstance(old, ast.ClassDef) or old.name != "Study":
            require(ast.dump(old) == ast.dump(new), "unexpected change outside Study")
            continue
        require(isinstance(new, ast.ClassDef) and new.name == old.name, "Study replaced")
        old_methods = {node.name: node for node in old.body}
        new_methods = {node.name: node for node in new.body}
        require(len(old_methods) == len(old.body) and len(new_methods) == len(new.body), "duplicate method")
        require(set(new_methods) - set(old_methods) == {"post_capacity_evidence"}, "unexpected added method")
        require(not set(old_methods) - set(new_methods), "existing method removed")
        changed = [name for name, node in old_methods.items()
                   if ast.dump(node) != ast.dump(new_methods[name])]
        require(set(changed) == {"freeze_evidence", "test"}, "training or other existing method changed")
    require(bool(changed), "no reviewed changes found")
    return {"added": ["post_capacity_evidence"], "changed": changed,
            "constructorVerifyRunBenchmarkTrainingSelectionAndFreezeMethodsUnchanged": True}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=HERE / "root-verification")
    args = parser.parse_args()
    output = args.output_dir.resolve()
    require(output.is_relative_to(HERE), "reports must stay inside the allowed repair folder")
    output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((HERE / "source-manifest.json").read_text())
    require(manifest["reviewedCommit"] == PIN, "unreviewed source commit")
    patch_path = HERE / "pr508-post-capacity-gate.patch"
    require(digest(patch_path.read_bytes()) == manifest["patchSha256"] == PATCH_SHA256, "unreviewed patch bytes")
    report = {"schemaVersion": 1, "observedUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "reviewedCommit": PIN, "patchSha256": PATCH_SHA256,
              "verifierSha256": digest(Path(__file__).read_bytes()),
              "actualModelProcesses": 0, "realHoldoutRead": False,
              "foreignBranchModified": False, "sourceChecks": {}, "controlFlowChecks": {}}
    report["artifactReproduction"] = run([sys.executable, str(HERE / "build_patch.py")])
    benchmark = manifest["benchmarkSourcePath"]
    benchmark_raw = subprocess.check_output(["git", "show", PIN + ":" + benchmark], cwd=REPO)
    require(digest(benchmark_raw) == manifest["benchmarkSourceSha256"]
            == digest((REPO / benchmark).read_bytes()), "benchmark contract differs from the pinned source")
    with tempfile.TemporaryDirectory(prefix="root-patch-application-") as temporary:
        target_root = Path(temporary)
        for variant, item in manifest["sources"].items():
            original = (HERE / item["originalFixture"]).read_bytes()
            actual = subprocess.check_output(["git", "show", PIN + ":" + item["sourcePath"]], cwd=REPO)
            require(actual == original and digest(original) == item["sourceSha256"], "original source mismatch")
            blob = hashlib.sha1(b"blob " + str(len(original)).encode() + b"\0" + original).hexdigest()
            require(blob == item["sourceGitBlob"], "Git blob identity mismatch")
            repaired = (HERE / item["patchedFixture"]).read_bytes()
            require(digest(repaired) == item["patchedSourceSha256"], "patched fixture identity mismatch")
            changes = method_changes(original, repaired)
            target = target_root / item["sourcePath"]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(original)
            report["sourceChecks"][variant] = {"originalSha256": digest(original),
                "patchedSha256": digest(repaired), "sourceGitBlob": blob, "methodChanges": changes}
        run(["git", "init", "-q"], cwd=target_root)
        run(["git", "apply", "--check", str(patch_path)], cwd=target_root)
        run(["git", "apply", str(patch_path)], cwd=target_root)
        for item in manifest["sources"].values():
            target = target_root / item["sourcePath"]
            require(target.read_bytes() == (HERE / item["patchedFixture"]).read_bytes(), "applied patch differs")
            compile(target.read_bytes(), str(target), "exec")
    report["exactPatchApplication"] = {"status": "PASS", "sources": 3, "compiledWithoutExecution": True}
    probe = HERE / "independent-tests/probe_post_capacity_gate.py"
    report["probeSha256"] = digest(probe.read_bytes())
    for variant, item in manifest["sources"].items():
        results = {}
        for stage, fixture, sha, exit_code in (
                ("red", item["originalFixture"], item["sourceSha256"], 1),
                ("green", item["patchedFixture"], item["patchedSourceSha256"], 0)):
            report_path = output / (stage + "-" + variant + ".json")
            command = [sys.executable, str(probe), "--variant", variant, "--runner", str(HERE / fixture),
                       "--expected-sha256", sha, "--report", str(report_path)]
            execution = run(command, expected_exit=exit_code)
            result = json.loads(report_path.read_text())
            require(result["probeSha256"] == report["probeSha256"] and result["testedSourceSha256"] == sha,
                    "probe or source changed during verification")
            require(result["passed"] + result["failed"] == 52, "unexpected final suite size")
            if stage == "green":
                require(result["passed"] == 52 and result["failed"] == 0, "repaired gate failed")
            else:
                require(result["failed"] > 0, "original defect not reproduced")
            results[stage] = {"passed": result["passed"], "failed": result["failed"],
                              "report": str(report_path.relative_to(HERE)),
                              "reportSha256": digest(report_path.read_bytes()), "execution": execution}
        report["controlFlowChecks"][variant] = results
    audit_output = output / "published-results.json"
    audit_path = HERE / "result-review/audit_published_results.py"
    execution = run([sys.executable, str(audit_path), "--repo", str(REPO), "--output", str(audit_output)])
    require(audit_output.read_bytes() == (HERE / "result-review/published-results.json").read_bytes(),
            "independent published-result repetition differs")
    audit = json.loads(audit_output.read_text())
    report["publishedResultRepetition"] = {"status": audit["status"],
        "sourceCount": len(audit["sourceInventory"]), "scriptSha256": digest(audit_path.read_bytes()),
        "reportSha256": digest(audit_output.read_bytes()), "byteIdenticalToHelperReport": True, "execution": execution}
    require(digest(patch_path.read_bytes()) == PATCH_SHA256, "patch changed during verification")
    report["status"] = "SOURCE_BOUND_REPAIR_VERIFIED_NOT_APPLIED_TO_PR508"
    (output / "verification.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({"status": report["status"], "patchSha256": PATCH_SHA256,
                      "runners": {key: {stage: {metric: value[stage][metric] for metric in ("passed", "failed")}
                                        for stage in ("red", "green")}
                                  for key, value in report["controlFlowChecks"].items()},
                      "publishedResultSources": report["publishedResultRepetition"]["sourceCount"]}, indent=2))


if __name__ == "__main__":
    main()
