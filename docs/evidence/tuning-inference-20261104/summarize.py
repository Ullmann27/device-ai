"""Validate every paired sample and summarize all archived host scenarios."""
import json
import statistics
import sys
from pathlib import Path

directory = Path(sys.argv[1])
summary = {}
expected_checksums = {}
for name in ("before", "candidate-a-after", "after"):
    rows = [json.loads(line) for line in (directory / f"{name}.jsonl").read_text().splitlines()]
    assert len(rows) == 164, (name, "incomplete run")
    measurements = rows[1:-1]
    assert len(measurements) == 9 * 9 * 2
    scenarios = sorted({(r["vocab"], r["kind"], r["temperature"], r["topK"]) for r in measurements})
    assert len(scenarios) == 9
    report = {"environment": rows[0], "scenarios": []}
    for scenario in scenarios:
        members = [r for r in measurements if (r["vocab"], r["kind"], r["temperature"], r["topK"]) == scenario]
        result = dict(zip(("vocab", "kind", "temperature", "topK"), scenario))
        for implementation in ("sorted_reference", "production"):
            samples = [r for r in members if r["implementation"] == implementation]
            assert sorted(r["repetition"] for r in samples) == list(range(9))
            expected_checksums.setdefault(scenario, samples[0]["checksum"])
            assert all(r["checksum"] == expected_checksums[scenario] for r in samples)
            values = {}
            for metric in ("wallNs", "cpuNs", "allocatedBytes"):
                per_op = [r[metric] / r["operations"] for r in samples]
                values[metric + "PerOp"] = {
                    "median": statistics.median(per_op), "min": min(per_op), "max": max(per_op)
                }
            values["operationsPerBatch"] = samples[0]["operations"]
            result[implementation] = values
        report["scenarios"].append(result)
    summary[name] = report
print(json.dumps(summary, indent=2))
