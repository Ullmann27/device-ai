#!/usr/bin/env python3
"""Runs the frozen H4 comparison and evaluates unchanged acceptance criteria."""
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
spec = importlib.util.spec_from_file_location("h4_audit", Path(__file__).with_name("h4-audit.py"))
auditor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(auditor)


def main():
    if len(sys.argv) != 4:
        raise SystemExit("usage: h4-measure.py H4.jar WORLD_DIR OUT_DIR")
    jar, data, out = map(Path, sys.argv[1:])
    source_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    working_tree = subprocess.check_output(["git", "status", "--porcelain"], text=True).strip()
    if working_tree:
        raise RuntimeError("Freeze and commit the candidate before opening TEST")
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
    if out.resolve().is_relative_to(root.resolve()):
        raise ValueError("Measurement output must be outside source")
    dataset = json.loads((data / "worlds.json").read_text())
    if dataset["split"] != "test" or dataset["seed"] != 92861 or len(dataset["worlds"]) != 192:
        raise ValueError("Acceptance gate requires the preregistered 192-world TEST")
    out.mkdir(parents=True, exist_ok=True)
    arms = ["sequential", "batch", "certificate", "uniform", "myopic"]
    runs = []
    audits = {}
    for pair in range(4):
        for arm in arms[pair:] + arms[:pair]:
            log = out / f"{pair}-{arm}.tsv"
            command = ["timeout", "120", "java", "-Xmx256m", "-cp", str(jar.resolve()),
                       "at.heinz.deviceai.cognitive.H4DiagnosisPilot", str((data / "worlds.tsv").resolve()), arm]
            with log.open("w") as stream:
                process = subprocess.Popen(command, stdout=stream)
                _, status, usage = os.wait4(process.pid, 0)
                process.returncode = os.waitstatus_to_exitcode(status)
            if process.returncode:
                raise RuntimeError(f"{arm}: exit {process.returncode}")
            with log.open() as stream:
                rows = list(csv.DictReader(stream, delimiter="\t"))
            if arm not in audits:
                audits[arm] = auditor.audit(data / "worlds.json", log)
            else:
                first = out / f"0-{arm}.tsv"
                with first.open() as stream:
                    old = list(csv.DictReader(stream, delimiter="\t"))
                for left, right in zip(old, rows):
                    assert all(left[key] == right[key] for key in left if key not in ("cpu_ns", "wall_ns"))
                assert len(rows) == len(old)
            walls = [int(row["wall_ns"]) for row in rows]
            cpus = [int(row["cpu_ns"]) for row in rows]
            if any(value < 0 for value in cpus):
                raise RuntimeError("Thread CPU measurement unavailable")
            runs.append({"pair": pair, "arm": arm, "wallNs": sum(walls), "cpuNs": sum(cpus),
                         "p95WorldWallNs": sorted(walls)[math.ceil(len(walls) * .95) - 1],
                         "maxProcessRssKiB": usage.ru_maxrss,
                         "log": log.name})
    comparisons = {}
    h4 = audits["certificate"]
    for arm in ("sequential", "batch", "uniform"):
        other = audits[arm]
        wins = sum(correct and not other["cases"][case] for case, correct in h4["cases"].items())
        losses = sum(not correct and other["cases"][case] for case, correct in h4["cases"].items())
        discordant = wins + losses
        p_value = sum(math.comb(discordant, k) for k in range(wins, discordant + 1)) / 2**discordant
        comparisons[arm] = {"wins": wins, "losses": losses, "correctDelta": wins - losses,
                            "oneSidedExactP": p_value, "pass": wins - losses >= 8 and p_value < .05}
    summary = {}
    for arm in arms:
        samples = [run for run in runs if run["arm"] == arm]
        summary[arm] = {**audits[arm]["totals"],
                        "medianWallNs": statistics.median(run["wallNs"] for run in samples),
                        "medianCpuNs": statistics.median(run["cpuNs"] for run in samples),
                        "maxProcessRssKiB": max(run["maxProcessRssKiB"] for run in samples),
                        "maxP95WorldWallNs": max(run["p95WorldWallNs"] for run in samples)}
    gate = all(row["pass"] for row in comparisons.values()) and \
        summary["certificate"]["wrong"] == summary["certificate"]["unsupported"] == 0 and \
        all(run["p95WorldWallNs"] <= 250_000_000 for run in runs)
    report = {
        "schemaVersion": 1, "sourceCommit": source_commit, "workingTree": working_tree,
        "platform": platform.platform(), "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "datasetSha256": hashlib.sha256((data / "worlds.json").read_bytes()).hexdigest(),
        "summary": summary, "comparisons": comparisons, "h4aGate": "PASS" if gate else "FAIL",
        "runs": runs, "audits": audits, "energyJoules": None, "device": "NOT_RUN",
    }
    (out / "comparison.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"summary": summary, "comparisons": comparisons, "gate": report["h4aGate"]}, indent=2))
    if not gate:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
