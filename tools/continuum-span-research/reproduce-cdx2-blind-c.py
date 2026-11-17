#!/usr/bin/env python3
"""Reproduce the consumed frozen Blind-C using actual Kotlin predict-only processes.

No training, checkpoint/source edits, candidate tuning or gold input to prediction.
Compile the existing candidate and existing probe/verifier together; cached app
classes and a full Android build are unnecessary. Reports are HOST_JVM only.
"""
import argparse
import copy
import hashlib
import itertools
import json
import math
from pathlib import Path
import resource
import statistics
import subprocess
import time

PINS = {
    "tools/continuum-span-research/Cdx2PointerCandidate.kt": "9c7d33b64d7fb1b13e3ceb22f2b538f3e4e62608d477f7e9868ce1f3b79c5f2e",
    "app/src/main/java/at/heinz/deviceai/learning/span/EvidenceBoundNaturalLocationProbe.kt": "7b29fbef554c3771c1d8ed735aa683429952f4dddabc2662fbca13d0e5d87a4a",
    "docs/evidence/issue340-20261101/m2/frozen-dev/checkpoint.json": "629c8319ede2aba3cd60f0448b6469034c8550530e0326739b79c26bfc40939d",
    "docs/evidence/issue340-20261101/m2/blind-c/corpus.json": "3bd5bddb76a50d020c9216db9eebf7ba9b717967a7b0947b3415f3a2c0fd5a1c",
    "docs/evidence/issue340-20261101/m2/blind-c/inputs.json": "167de32908d1d4bde41c78024e315dbd3780606dd9be64b0b93bfb765f70ae6d",
    "docs/evidence/issue340-20261101/m2/blind-c/MANIFEST.json": "df5eaaf87077190394927fb72020d17b21d0d894cb7649da641cc3682d9693f5",
}
MODES = ("RAW", "VERIFIED", "RULE")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="fresh evidence directory")
    parser.add_argument("--build-dir", type=Path, required=True, help="fresh scratch classes directory")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--java", default="/workspace/post-fable-toolchain/jdk21/bin/java")
    parser.add_argument("--kotlinc", default="/workspace/post-fable-toolchain/bin/kotlinc")
    parser.add_argument("--jars", type=Path, default=Path("/workspace/continuum-toolchain"))
    parser.add_argument("--node", default="node")
    parser.add_argument("--previous", type=Path, default=Path("/workspace/continuum-checks/issue340-blind-c-evaluation"))
    args = parser.parse_args()
    repo, out, build = args.repo.resolve(), args.output.resolve(), args.build_dir.resolve()
    assert not out.exists() and not build.exists(), "Fresh output/build directories required"
    for name, digest in PINS.items():
        assert sha(repo / name) == digest, "Immutable pin changed: " + name
    frozen = repo / "docs/evidence/issue340-20261101/m2"
    # Snapshot every frozen input, including TRAIN/DEV; these are never executed.
    immutable = {str(p.relative_to(repo)): sha(p) for p in frozen.rglob("*") if p.is_file()}
    out.mkdir(parents=True)
    build.mkdir(parents=True)
    for name in ("requests", "outputs", "logs", "controls"):
        (out / name).mkdir()
    sources = [repo / name for name in PINS if name.endswith(".kt")]
    checkpoint = frozen / "frozen-dev/checkpoint.json"
    corpus = frozen / "blind-c/corpus.json"
    deps = [args.jars / "json.jar", args.jars / "stdlib.jar"]
    scorer = repo / "scripts/cdx2-blind-score.mjs"
    commands = []

    def run(command, log_name, timeout=90):
        started = time.perf_counter()
        process = subprocess.run([str(x) for x in command], capture_output=True, text=True, timeout=timeout)
        (out / "logs" / (log_name + ".stdout")).write_text(process.stdout)
        (out / "logs" / (log_name + ".stderr")).write_text(process.stderr)
        commands.append({"name": log_name, "command": [str(x) for x in command],
                         "exitCode": process.returncode, "wallMillis": (time.perf_counter() - started) * 1000})
        assert process.returncode == 0, "Command failed; evidence retained: " + log_name
        return process

    run([args.java, "-version"], "java-version")
    run([args.kotlinc, "-version"], "kotlin-version")
    run([args.node, "--version"], "node-version")
    run([args.node, "--test", repo / "scripts/cdx2-blind-score.test.mjs"], "scorer-tests")
    run([args.kotlinc, "-cp", ":".join(map(str, deps)), *sources, "-d", build], "compile")
    classpath = ":".join(map(str, [build, *deps]))
    # Only this label-free file is loaded before all 55 outputs have been saved.
    inputs = json.loads((frozen / "blind-c/inputs.json").read_text())["cases"]
    assert [c["id"] for c in inputs] == [f"C{i:03}" for i in range(1, 56)]
    assert all(set(c) == {"id", "question", "sources"} for c in inputs)
    rows, latencies = [], []
    cpu0 = resource.getrusage(resource.RUSAGE_CHILDREN)
    started = time.perf_counter()
    for request in inputs:
        cid = request["id"]
        req, output = out / "requests" / (cid + ".json"), out / "outputs" / (cid + ".json")
        write(req, request)
        run([args.java, "-cp", classpath, "at.heinz.deviceai.learning.span.Cdx2PointerCandidate",
             "predict", checkpoint, req, output], cid, timeout=30)
        assert json.loads(req.read_text()) == request
        prediction = json.loads(output.read_text())
        # Preserve producer SPAN/NOANSWER objects as-is; no labels or gold adaptation.
        rows.append({"id": cid, **{mode: prediction[mode] for mode in MODES}})
        latencies.append(prediction["rawMillis"])
    cpu1 = resource.getrusage(resource.RUSAGE_CHILDREN)
    timing = {"environment": "HOST_JVM_NOT_ANDROID", "processCount": 55,
              "wholePredictionRunWallSeconds": time.perf_counter() - started,
              "childUserCpuSeconds": cpu1.ru_utime - cpu0.ru_utime,
              "childSystemCpuSeconds": cpu1.ru_stime - cpu0.ru_stime,
              "rawPredictionMillisMedian": statistics.median(latencies),
              "rawPredictionMillisP95NearestRank": sorted(latencies)[math.ceil(.95 * len(latencies)) - 1],
              "rawPredictionMillisSum": sum(latencies),
              "note": "Each case starts a fresh JVM; rawMillis excludes verifier/rule work."}
    write(out / "predictions.json", {"cases": rows})
    run([args.node, scorer, corpus, out / "predictions.json", out / "independent-score.json",
         PINS[str(corpus.relative_to(repo))]], "independent-score")
    score = json.loads((out / "independent-score.json").read_text())
    # Source-copy positive and malformed-evidence negatives, after fixed evaluation.
    controls = []
    for label in ("positive-source-copy", "mutated-source-stale-hash", "invalid-revision"):
        request = copy.deepcopy(inputs[0])
        if label == "mutated-source-stale-hash":
            request["sources"][0]["text"] += " changed"
        if label == "invalid-revision":
            request["sources"][0]["revision"] = 0
        req, output = out / "controls" / (label + "-request.json"), out / "controls" / (label + "-output.json")
        write(req, request)
        run([args.java, "-cp", classpath, "at.heinz.deviceai.learning.span.Cdx2PointerCandidate",
             "predict", checkpoint, req, output], label, timeout=30)
        actual = json.loads(output.read_text())
        passed = all(actual[m]["decision"] == ("SPAN" if label == "positive-source-copy" else "NOANSWER") for m in MODES)
        if label == "positive-source-copy":
            for mode in MODES:
                p = actual[mode]
                s = next(s for s in request["sources"] if s["sourceId"] == p["sourceId"])
                copied = s["text"].encode("utf-16-le")[2*p["start"]:2*p["endExclusive"]].decode("utf-16-le")
                passed = passed and copied == p["text"] and s["revision"] == p["revision"] and s["sha256"] == p["sha256"]
        controls.append({"name": label, "passed": passed})
        assert passed, "Control failed: " + label
    comparison = {"status": "PREVIOUS_OUTPUTS_UNAVAILABLE", "previousDirectory": str(args.previous)}
    if (args.previous / "outputs/C001.json").exists():
        disagreements = []
        previous_rows = []
        for row in rows:
            previous = json.loads((args.previous / "outputs" / (row["id"] + ".json")).read_text())
            previous_rows.append({"id": row["id"], **{m: previous[m] for m in MODES}})
            for mode in MODES:
                if previous[mode] != row[mode]:
                    disagreements.append({"id": row["id"], "mode": mode})
        write(out / "previous-predictions-adapted.json", {"cases": previous_rows})
        run([args.node, scorer, corpus, out / "previous-predictions-adapted.json",
             out / "previous-independent-score.json", PINS[str(corpus.relative_to(repo))]], "previous-independent-score")
        previous_score = json.loads((out / "previous-independent-score.json").read_text())
        comparison.update(status="COMPARED_ALL_55_ALL_3_MODES", comparedDecisions=165,
                          disagreements=disagreements,
                          metricsIdentical=score["splits"] == previous_score["splits"],
                          previousExecutionSha256=sha(args.previous / "execution.json"),
                          previousReportSha256=sha(args.previous / "report.json"))
        assert not disagreements and comparison["metricsIdentical"]
    paired = {}
    for split in score["splits"]:
        records = [r for r in score["records"] if r["split"] == split]
        paired[split] = {}
        for a, b in itertools.combinations(MODES, 2):
            paired[split][a + "_vs_" + b] = {
                "predictionDisagreementIds": [r["id"] for r in records if r[a]["prediction"] != r[b]["prediction"]],
                "aOnlyCorrect": [r["id"] for r in records if r[a]["exact"] and not r[b]["exact"]],
                "bOnlyCorrect": [r["id"] for r in records if r[b]["exact"] and not r[a]["exact"]]}
    unchanged = {name: sha(repo / name) == value for name, value in immutable.items()}
    assert all(unchanged.values())
    report = {"schema": "SYNAPSE_II_M2_REPRODUCTION_V1", "status": "COMPLETED_REPRODUCTION_CONSUMED_BLIND_C",
              "freshBlindTest": False, "deviceStatus": "DEVICE_NOT_RUN", "promotion": "FORBIDDEN_RESEARCH_ONLY",
              "sourceGitHeadAtRun": subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip(),
              "frozenPins": PINS, "sourceAndToolSha256": {str(p.relative_to(repo)): sha(p) for p in [*sources, scorer, Path(__file__).resolve()]},
              "dependencySha256": {str(p): sha(p) for p in deps},
              "checkpointBytes": checkpoint.stat().st_size, "modelParameterCount": 11,
              "caseCount": len(rows), "predictionInputs": "id/question/sources only; no expected/split/category",
              "splitMetrics": score["splits"], "paired": paired, "timing": timing,
              "classifierNll": {"status": "NOT_RECORDED", "reason": "Frozen predict CLI emits argmax tuple, no classifier distribution or gold probability."},
              "controls": controls, "previousOutputComparison": comparison,
              "immutability": {"allFrozenFilesUnchanged": all(unchanged.values()), "files": unchanged},
              "scorerTests": {"tests": 11, "passed": 11, "negativeTupleMutations": 100,
                              "evidence": "logs/scorer-tests.stdout"},
              "limitations": ["Consumed Blind-C reproduced without tuning; no fresh generalization evidence.",
                              "Handwritten grammar plus 11 learned weights, not repaired CDX1 generation.",
                              "Host JVM evidence; no Android/Fold7/app build or device claim."]}
    write(out / "execution.json", commands)
    write(out / "reproduction-report.json", report)
    files = sorted(p for p in out.rglob("*") if p.is_file())
    (out / "SHA256SUMS").write_text("".join(f"{sha(p)}  {p.relative_to(out)}\n" for p in files))
    print(json.dumps({"report": str(out / "reproduction-report.json"), "splitMetrics": score["splits"],
                      "timing": timing, "comparison": comparison, "controls": controls}, indent=2))


if __name__ == "__main__":
    main()
