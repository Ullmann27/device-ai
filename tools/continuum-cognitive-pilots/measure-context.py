#!/usr/bin/env python3
"""Paired host-only measurement; requires Unix wait4, Java and both built jars."""
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import sys
import time


def main():
    if len(sys.argv) != 4:
        raise SystemExit("usage: measure-context.py BASELINE.jar CANDIDATE.jar OUT")
    jars = dict(zip(("baseline", "candidate"), map(Path, sys.argv[1:3])))
    out = Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    fixture = Path(__file__).with_name("context-cases.tsv").resolve()
    runs = []
    for pair in range(6):
        order = ("baseline", "candidate") if pair % 2 == 0 else ("candidate", "baseline")
        for arm in order:
            log = out / f"{pair}-{arm}.tsv"
            command = ["java", "-cp", str(jars[arm].resolve()),
                       "at.heinz.deviceai.cognitive.ContextPilot", str(fixture)]
            start = time.monotonic_ns()
            with log.open("w") as stream:
                process = subprocess.Popen(command, stdout=stream)
                _, status, usage = os.wait4(process.pid, 0)
                process.returncode = os.waitstatus_to_exitcode(status)
            if process.returncode:
                raise RuntimeError(f"{arm} exited {process.returncode}")
            lines = log.read_text().splitlines()
            rows = [line.split("\t") for line in lines[1:] if "\t" in line]
            measurements = [list(map(int, line.split(","))) for line in lines if line[:1].isdigit()]
            correct = {}
            for row in rows:
                counts = correct.setdefault(row[1], {"correct": 0, "total": 0})
                counts["total"] += 1
                counts["correct"] += row[-1] == "true"
            runs.append({
                "pair": pair, "arm": arm, "quality": correct,
                "processWallNs": time.monotonic_ns() - start,
                "processCpuSeconds": usage.ru_utime + usage.ru_stime,
                "maxRssKiB": usage.ru_maxrss,
                "routingSamples": measurements,
            })
    summary = {}
    for arm in jars:
        selected = [run for run in runs if run["arm"] == arm]
        summary[arm] = {
            "quality": selected[0]["quality"],
            "medianRoutingWallNsPer2400Calls": statistics.median(
                sample[0] for run in selected for sample in run["routingSamples"]),
            "medianRoutingCpuNsPer2400Calls": statistics.median(
                sample[1] for run in selected for sample in run["routingSamples"]),
            "maxProcessRssKiB": max(run["maxRssKiB"] for run in selected),
        }
        assert all(run["quality"] == selected[0]["quality"] for run in selected)
    document = {
        "schemaVersion": 1, "platform": platform.platform(),
        "fixtureSha256": hashlib.sha256(fixture.read_bytes()).hexdigest(),
        "jarSha256": {arm: hashlib.sha256(path.read_bytes()).hexdigest() for arm, path in jars.items()},
        "energyJoules": None, "device": "HOST_ONLY",
        "limits": "Includes JVM/JIT/GC. RSS units are KiB on Linux. No model inference.",
        "summary": summary, "runs": runs,
    }
    (out / "comparison.json").write_text(json.dumps(document, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
