#!/usr/bin/env python3
"""Audit evidence without new measurements."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs/evidence/tuning-r3-20261104"
SOURCE = "app/src/main/java/at/heinz/deviceai/learning/lm/"
BASELINE = "2f20c19b7e37197a6accac0c226b5401e25f1f99"
BINDINGS = {
    "baseline": BASELINE,
    "m1": "6156d048264ffd2cfed38ab7ea11658a64eecd54",
    "m2": "54a4342c778d169f1af22a1b15f8b397b0d751cf",
    "m3a": "54a4342c778d169f1af22a1b15f8b397b0d751cf",
    "m3b": "565b3e0ac34aa7c16b4a6dad4939772140f93a4b",
    "m4-control": "565b3e0ac34aa7c16b4a6dad4939772140f93a4b",
    "m4": "8b30bb6f76aece62696fd627992fa5dcd2394fec",
}
PUBLIC_REVISIONS = {
    BASELINE: ("73b64ec299fd314dfdf07835fc7d8fc0d74b51a2", "f6297e08d03eae74283f43b1ce5abf80c5f7e666"),
    "6156d048264ffd2cfed38ab7ea11658a64eecd54": ("facc6aa2f809ef1094c27dcc693a1b0f22f7942d", "7f9b76ffa2c1d89aaa8316b9ef5af8024e101ceb"),
    "54a4342c778d169f1af22a1b15f8b397b0d751cf": ("7d9fbea5e7b02ca80abd037c4cfb3a0c6a0c4e02", "379556cc4a7c3415f7b0b15e385ced46f47fecb7"),
    "565b3e0ac34aa7c16b4a6dad4939772140f93a4b": ("e5f05b92d71890a9c9ebb6c95649d5ef1e0edea0", "47115c868f23bf7949615f040fc0bcf75f5d3170"),
    "8b30bb6f76aece62696fd627992fa5dcd2394fec": ("2aab7c3392b5205262575ae6ee1cddf4ec0a81b4", "d1250296237190abd71a673a22165e31201b3a25"),
    "a5c1210f88bdbe700226781afd087856af3d34fa": ("40ba0479717c91a206fd55f8134fd610b3c077b2", "833acba6426358347d77df0de30522e1dfd9e82a"),
}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def resolve(local):
    public, tree = PUBLIC_REVISIONS[local]
    for ref in (public, local):
        result = subprocess.run(["git", "rev-parse", "--verify", ref + "^{tree}"], cwd=ROOT, capture_output=True, text=True)
        if result.returncode == 0:
            assert result.stdout.strip() == tree
            return ref
    raise RuntimeError("Fetch public milestone " + public)


def load(path):
    return json.loads(path.read_text())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("r3_summary", Path(__file__).with_name("summarize.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    baseline = resolve(BASELINE)
    resolved = {local: resolve(local) for local in PUBLIC_REVISIONS}
    stages = {}
    for stage, local in BINDINGS.items():
        revision = resolved[local]
        directory = EVIDENCE / stage
        saved = load(directory / "summary.json")
        manifest = load(directory / "manifest.json")
        raw = directory / f"{saved['phase']}-{saved['group']}.jsonl"
        calculated = module.read(raw, saved["phase"], saved["group"])
        if stage not in ("baseline", "m4-control"):
            control_dir = EVIDENCE / ("m4-control" if stage == "m4" else "baseline")
            group = "hex" if stage == "m4" else "all"
            control = module.read(control_dir / f"aa-{group}.jsonl", "aa", group)
            by_name = {case["case"]: case for case in control["cases"]}
            for case in calculated["cases"]:
                original = by_name[case["case"]]
                assert case["fixtureSha256"] == original["fixtureSha256"]
                case["identicalSourceControl"] = original["metrics"]
        calculated.update(sourceSha256=manifest["sourceSha256"], baselineCommit=manifest["resolvedBaseline"],
                          status="VALIDATED_COMPLETE_MATRIX")
        assert saved == calculated, stage + " summary mismatch"
        for name, expected in manifest["sourceSha256"].items():
            side, filename = name.split("/")
            if side == "baseline":
                data = git("show", baseline + ":" + SOURCE + filename)
            elif stage == "m3a" and filename == "LmBpeTokenizer.kt":
                data = (directory / "candidate-LmBpeTokenizer.kt").read_bytes()
            else:
                data = git("show", revision + ":" + SOURCE + filename)
            assert digest(data) == expected, stage + ":" + name
        for name, expected in manifest["harnessSha256"].items():
            if name.endswith("/run.py"):
                revisions = ("6156d048264ffd2cfed38ab7ea11658a64eecd54", "565b3e0ac34aa7c16b4a6dad4939772140f93a4b",
                             "a5c1210f88bdbe700226781afd087856af3d34fa")
                assert expected in {digest(git("show", resolved[ref] + ":" + name)) for ref in revisions}, name
            else:
                assert digest((ROOT / name).read_bytes()) == expected, name
        expected_tests = 26 if stage in ("m4-control", "m4") else 24
        assert f"OK ({expected_tests} tests)" in (directory / "tests.txt").read_text()
        stages[stage] = {"sourceRevision": revision, "publicCompanionSourceCommit": PUBLIC_REVISIONS[local][0], "samples": saved["sampleCount"],
                         "rawSha256": saved["rawSha256"], "summaryRecomputedExactly": True,
                         "sourceAndHarnessVerified": True, "warnings": saved["warnings"]}
    original_runner = (EVIDENCE / "m3a/runner-at-measurement.py.txt").read_bytes()
    assert digest(original_runner) == load(EVIDENCE / "m3a/manifest.json")["harnessSha256"]["tools/tuning-r3-20261104/run.py"]
    final_manifest = load(EVIDENCE / "m4/manifest.json")
    runtime = load(EVIDENCE / "final/runtime/manifest.json")
    assert runtime["status"] == "CHECKS_PASS" and runtime["distinctTestMethods"] == 5
    assert runtime["sourceSha256"] == final_manifest["sourceSha256"]
    assert runtime["sourceReceiptSha256"] == digest((EVIDENCE / "m4/manifest.json").read_bytes())
    assert runtime["harnessSha256"] == digest(Path(__file__).with_name("verify-runtime.py").read_bytes())
    for side in ("baseline", "candidate"):
        assert "OK (5 tests)" in (EVIDENCE / "final/runtime" / f"{side}-tests.txt").read_text()
    archive = load(EVIDENCE / "final/archive-check/manifest.json")
    assert archive["candidateSelection"] == "m3a" and archive["status"] == "CHECKS_PASS"
    assert archive["sourceSha256"] == load(EVIDENCE / "m3a/manifest.json")["sourceSha256"]
    for name, expected in final_manifest["sourceSha256"].items():
        if name.startswith("candidate/"):
            assert digest((ROOT / SOURCE / Path(name).name).read_bytes()) == expected
    changed = git("diff", "--name-only", baseline, "HEAD").decode().splitlines()
    production = [SOURCE + name for name in ("LmBpeTokenizer.kt", "LmModel.kt", "LmTensor.kt")]
    prefixes = ("docs/evidence/tuning-r3-20261104/", "tools/tuning-r3-20261104/")
    documents = ("docs/research/omega/TUNING-CONTINUATION.json", "docs/research/omega/TUNING-R3-20261104.md")
    assert all(path in production or path in documents or path.startswith(prefixes) for path in changed)
    old_model = git("show", baseline + ":" + SOURCE + "LmModel.kt")
    new_model = (ROOT / SOURCE / "LmModel.kt").read_bytes()
    assert old_model.split(b"object LmEval {", 1)[1] == new_model.split(b"object LmEval {", 1)[1]
    historical = {}
    for directory in ("docs/evidence/three-tuning-bots-20261104", "docs/evidence/three-tuning-bots-r2-20261104"):
        before = git("ls-tree", "-r", baseline, "--", directory)
        after = git("ls-tree", "-r", "HEAD", "--", directory)
        assert before and before == after
        historical[directory] = {"files": len(before.splitlines()), "treeListingSha256": digest(before)}
    report = {"status": "PASS", "auditedHead": git("rev-parse", "HEAD").decode().strip(),
              "harnessSha256": digest(Path(__file__).read_bytes()), "timingProcesses": len(stages),
              "samples": sum(stage["samples"] for stage in stages.values()), "stages": stages,
              "distinctTestMethods": 31, "runtimeSuites": "5 original + 5 final executions, 5 distinct methods",
              "archiveFunctionalChecks": "24 methods, no new timing", "historicalReceiptsUnchanged": historical,
              "productionChanged": production, "r1SamplerAndOtherProductionPreserved": True}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: report[key] for key in ("status", "timingProcesses", "samples", "distinctTestMethods")}))


if __name__ == "__main__":
    main()
