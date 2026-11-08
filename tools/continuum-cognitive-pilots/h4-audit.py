#!/usr/bin/env python3
"""Independent causal/receipt oracle. Does not import candidate or generator."""
import csv
import itertools
import json
import math
from pathlib import Path
import sys


def predictions(world, probe):
    return [bool((mask & probe["requiredRoots"]) == probe["requiredRoots"] and
                 (mask & probe["blockedRoots"]) == 0) for mask in world["causes"]]


def compatible(world, trace):
    candidates = []
    probes = {p["id"]: p for p in world["probes"]}
    for index in range(len(world["causes"])):
        errors = sum(value is not None and predictions(world, probes[probe])[index] != value
                     for probe, value in trace)
        if errors <= 1:
            candidates.append(f"h{index}")
    return candidates


def response(world, probe, step):
    if step == 0 and world["stratum"] in (1, 3):
        return None
    value = predictions(world, probe)[world["truth"]]
    return not value if step == 1 and world["stratum"] in (2, 3) else value


def diagnosable(world):
    # Oracle upper bound: knows hidden outcome schedule, unlike every strategy.
    for count in range(1, 4):
        for plan in itertools.permutations(world["probes"], count):
            if sum(p["cost"] for p in plan) > 9:
                continue
            trace = [(probe["id"], response(world, probe, step)) for step, probe in enumerate(plan)]
            if compatible(world, trace) == [f'h{world["truth"]}']:
                return True
    return False


def audit(worlds_path, trace_path):
    worlds = {w["id"]: w for w in json.loads(Path(worlds_path).read_text())["worlds"]}
    rows = list(csv.DictReader(Path(trace_path).open(), delimiter="\t"))
    assert len(rows) == len(worlds) and len({r["id"] for r in rows}) == len(worlds)
    totals = dict(correct=0, wrong=0, unsupported=0, abstain=0, justifiedAbstain=0,
                  avoidableAbstain=0, rawWrong=0, probes=0, simulatedCost=0, gainBits=0)
    cases = {}
    for row in rows:
        world = worlds[row["id"]]
        probes = {p["id"]: p for p in world["probes"]}
        trace = []
        for pair in filter(None, row["transcript"].split(";")):
            probe_id, value = pair.split(":")
            assert value in ("0", "1", "?") and probe_id in probes
            trace.append((probe_id, None if value == "?" else value == "1"))
        assert len(trace) <= 3 and len(set(p for p, _ in trace)) == len(trace)
        cost = sum(probes[p]["cost"] for p, _ in trace)
        assert cost <= 9 and float(row["cost"]) == cost
        for step, (probe_id, value) in enumerate(trace):
            assert value == response(world, probes[probe_id], step), "fabricated synthetic observation"
        feasible = compatible(world, trace)
        assert row["feasible"].split(",") == feasible
        truth = f'h{world["truth"]}'
        assert truth in feasible, "error-model violation"
        accepted = row["answer"] != "ABSTAIN"
        supported = feasible == [row["answer"]]
        totals["unsupported"] += accepted and not supported
        correct = accepted and supported and row["answer"] == truth
        totals["correct"] += correct
        totals["wrong"] += accepted and row["answer"] != truth
        if not accepted:
            totals["abstain"] += 1
            possible = diagnosable(world)
            totals["avoidableAbstain" if possible else "justifiedAbstain"] += 1
        totals["rawWrong"] += row["raw"] not in (truth, "ABSTAIN")
        totals["probes"] += len(trace)
        totals["simulatedCost"] += cost
        totals["gainBits"] += math.log2(len(world["causes"]) / len(feasible))
        cases[row["id"]] = correct
    totals["gainBitsPerProbe"] = totals["gainBits"] / max(1, totals["probes"])
    totals["gainBitsPerCost"] = totals["gainBits"] / max(1, totals["simulatedCost"])
    return {"totals": totals, "cases": cases}


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: h4-audit.py worlds.json trace.tsv result.json")
    report = audit(sys.argv[1], sys.argv[2])
    Path(sys.argv[3]).write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report["totals"], indent=2))
