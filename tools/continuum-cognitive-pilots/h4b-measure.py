#!/usr/bin/env python3
"""Runs the H4-B replay arms and evaluates the preregistered acceptance criteria."""
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import platform
import statistics
import subprocess
import sys

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("h4b_score", Path(__file__).with_name("h4b-score.py"))
scorer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scorer)

CANDIDATE_ARMS = ["candidate", "ablation-prefix3", "ablation-truncate-only"]
TEST_SEED, TEST_SIZE = 77419, 180
GATE_MIN_GAIN, GATE_P, GATE_P95_NS = 20, .05, 20_000_000


def exact_one_sided(wins, losses):
    discordant = wins + losses
    return sum(math.comb(discordant, k) for k in range(wins, discordant + 1)) / 2 ** discordant if discordant else 1.0


def compare(left, right):
    """Paired comparison of the CORRECT indicator, left minus right."""
    wins = sum(1 for case, category in left["cases"].items() if category == "CORRECT" and right["cases"][case] != "CORRECT")
    losses = sum(1 for case, category in left["cases"].items() if category != "CORRECT" and right["cases"][case] == "CORRECT")
    return {"wins": wins, "losses": losses, "correctDelta": wins - losses, "oneSidedExactP": exact_one_sided(wins, losses)}


def run_arm(jar, arm, stories_tsv, log):
    command = ["timeout", "120", "java", "-Xmx256m", "-cp", str(jar.resolve()),
               "at.heinz.deviceai.cognitive.H4BReturnPilot", str(stories_tsv.resolve()), arm]
    with log.open("w") as stream:
        process = subprocess.Popen(command, stdout=stream)
        _, status, usage = os.wait4(process.pid, 0)
    code = os.waitstatus_to_exitcode(status)
    if code:
        raise RuntimeError(f"{arm}: exit {code}")
    with log.open() as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    walls = [int(row["wall_ns"]) for row in rows]
    cpus = [int(row["cpu_ns"]) for row in rows]
    if any(value < 0 for value in cpus):
        raise RuntimeError("Thread CPU measurement unavailable")
    return rows, {"wallNs": sum(walls), "cpuNs": sum(cpus),
                  "p95StoryWallNs": sorted(walls)[math.ceil(len(walls) * .95) - 1], "maxProcessRssKiB": usage.ru_maxrss}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--original", required=True, type=Path, help="jar compiled from the unchanged git ref")
    parser.add_argument("--candidate", type=Path, help="jar compiled from the worktree with H4-B arms")
    parser.add_argument("--split", required=True, choices=["dev", "test"])
    parser.add_argument("--rounds", type=int)
    parser.add_argument("data", type=Path)
    parser.add_argument("out", type=Path)
    args = parser.parse_args()
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
    if args.out.resolve().is_relative_to(root.resolve()):
        raise ValueError("Measurement output must be outside source")
    source_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    working_tree = subprocess.check_output(["git", "status", "--porcelain"], text=True).strip()
    dataset = json.loads((args.data / "stories.json").read_text(encoding="utf-8"))
    if dataset["split"] != args.split:
        raise ValueError("dataset split does not match --split")
    rounds = args.rounds or (4 if args.split == "test" else 1)
    if args.split == "test":
        if working_tree:
            raise RuntimeError("Freeze and commit the candidate before opening TEST")
        if dataset["seed"] != TEST_SEED or len(dataset["stories"]) != TEST_SIZE or args.candidate is None or rounds != 4:
            raise ValueError("Acceptance gate requires the preregistered 180-story TEST, both jars and 4 rounds")
    arms = [("original", args.original)] + ([(arm, args.candidate) for arm in CANDIDATE_ARMS] if args.candidate else [])
    args.out.mkdir(parents=True, exist_ok=True)
    stories_tsv = args.data / "stories.tsv"
    runs, audits = [], {}
    for round_index in range(rounds):
        rotation = arms[round_index % len(arms):] + arms[:round_index % len(arms)]
        for arm, jar in rotation:
            log = args.out / f"{round_index}-{arm}.tsv"
            rows, usage = run_arm(jar, arm, stories_tsv, log)
            if arm not in audits:
                audits[arm] = scorer.audit(args.data / "stories.json", log)
            else:
                with (args.out / f"0-{arm}.tsv").open() as stream:
                    first = list(csv.DictReader(stream, delimiter="\t"))
                assert len(first) == len(rows)
                for left, right in zip(first, rows):
                    assert all(left[key] == right[key] for key in left if key not in ("cpu_ns", "wall_ns")), "non-deterministic arm"
            runs.append({"round": round_index, "arm": arm, "log": log.name, **usage})
    summary = {}
    for arm, _ in arms:
        samples = [run for run in runs if run["arm"] == arm]
        summary[arm] = {**audits[arm]["totals"],
                        "medianWallNs": statistics.median(run["wallNs"] for run in samples),
                        "medianCpuNs": statistics.median(run["cpuNs"] for run in samples),
                        "maxProcessRssKiB": max(run["maxProcessRssKiB"] for run in samples),
                        "maxP95StoryWallNs": max(run["p95StoryWallNs"] for run in samples)}
    comparisons = {}
    if args.candidate:
        for arm in CANDIDATE_ARMS:
            comparisons[f"{arm}-vs-original"] = compare(audits[arm], audits["original"])
        comparisons["candidate-vs-ablation-prefix3"] = compare(audits["candidate"], audits["ablation-prefix3"])
        comparisons["candidate-vs-ablation-truncate-only"] = compare(audits["candidate"], audits["ablation-truncate-only"])
    gate = None
    if args.split == "test":
        main_comparison = comparisons["candidate-vs-original"]
        candidate, original = summary["candidate"], summary["original"]
        checks = {
            "gainAtLeast20": main_comparison["correctDelta"] >= GATE_MIN_GAIN,
            "pBelow05": main_comparison["oneSidedExactP"] < GATE_P,
            "wrongNotAboveOriginal": candidate["wrongBindings"] <= original["wrongBindings"],
            "zeroWrongOnNoReferent": candidate["wrongOnNoReferent"] == 0,
            "p95StoryWithin20ms": all(run["p95StoryWallNs"] <= GATE_P95_NS for run in runs if run["arm"] == "candidate"),
        }
        gate = {"checks": checks, "result": "PASS" if all(checks.values()) else "FAIL"}
    report = {
        "schemaVersion": 1, "sourceCommit": source_commit, "workingTree": working_tree, "split": args.split,
        "platform": platform.platform(),
        "jars": {arm: hashlib.sha256(jar.read_bytes()).hexdigest() for arm, jar in arms},
        "datasetSha256": hashlib.sha256((args.data / "stories.json").read_bytes()).hexdigest(),
        "summary": summary, "perStratum": {arm: audits[arm]["perStratum"] for arm, _ in arms},
        "comparisons": comparisons, "gate": gate, "runs": runs,
        "cases": {arm: audits[arm]["cases"] for arm, _ in arms},
        "energyJoules": None, "device": "NOT_RUN", "localModel": "NOT_RUN",
    }
    (args.out / "comparison.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"summary": summary, "comparisons": comparisons, "gate": gate}, indent=2))
    if gate and gate["result"] != "PASS":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
