"""Validate and summarize every preregistered round-two observation."""
import json
import re
import statistics
import sys
from pathlib import Path

directory = Path(sys.argv[1])
scenarios = [(v, kind, "0.0", 1) for v in (258, 512, 2048, 8192) for kind in ("random", "ties")]
scenarios += [(512, "random", "1.0", 50), (512, "random", "1.0", 1),
              (512, "random", "1.0", 512), (512, "ties", "1.0", 50),
              (2048, "random", "0.8", 40), (2048, "random", "1.0", 2048),
              (8192, "random", "1.0", 50), (8192, "random", "1.0", 8192),
              (512, "random", "NaN", 50), (512, "special", "1.0", 50)]
key = lambda row: (row["vocab"], row["kind"], row["temperature"], row["topK"])
expected_checksums = {}
summary = {}
for phase in ("before", "after"):
    rows, warnings = [], []
    for line in (directory / f"{phase}.jsonl").read_text().splitlines():
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError:
            if not rows and not warnings and re.fullmatch(r"\[\d+\.\d+s\]\[warning\]\[perf,memops\] Cannot use file /tmp/hsperfdata_agent/\d+ because it is locked by another process \(errno = 11\)", line):
                warnings.append(line)
            else:
                raise
    assert len(rows) == 326 and rows[0]["phase"] == phase and rows[-1]["phase"] == phase
    measurements = rows[1:-1]
    assert set(map(key, measurements)) == set(scenarios)
    report = {"environment": rows[0], "jvmStartupWarnings": warnings, "scenarios": []}
    for scenario in scenarios:
        members = [row for row in measurements if key(row) == scenario]
        result = dict(zip(("vocab", "kind", "temperature", "topK"), scenario))
        for implementation in ("round_one_reference", "production"):
            samples = [row for row in members if row["implementation"] == implementation]
            assert sorted(row["repetition"] for row in samples) == list(range(9))
            expected_checksums.setdefault(scenario, samples[0]["checksum"])
            assert all(row["checksum"] == expected_checksums[scenario] for row in samples)
            values = {"operationsPerBatch": samples[0]["operations"]}
            for metric in ("wallNs", "cpuNs", "allocatedBytes"):
                per_op = [row[metric] / row["operations"] for row in samples]
                values[metric + "PerOp"] = {
                    "median": statistics.median(per_op), "min": min(per_op), "max": max(per_op)
                }
            result[implementation] = values
        report["scenarios"].append(result)
    summary[phase] = report
print(json.dumps(summary, indent=2))
