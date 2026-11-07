#!/usr/bin/env python3
"""Descriptive post-evaluation summary; not part of the frozen test fixture."""
from collections import defaultdict
from pathlib import Path
from statistics import median

root = Path(__file__).resolve().parent

def read(name):
    rows = defaultdict(list)
    for line in (root / name).read_text().splitlines():
        if line.startswith("SAMPLE\t"):
            _, label, count, iteration, nanos, allocated = line.split("\t")
            rows[(int(count), label)].append((int(nanos), int(allocated)))
    return rows

baseline = read("baseline-bench.log")
candidate = read("candidate-bench.log")
repeat = read("baseline-bench-repeat.log")
print("| Records | Scenario | Baseline median ms | Candidate median ms | Baseline repeat ms | Candidate / baseline | Baseline allocated MiB | Candidate allocated MiB |")
print("| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |")
for key, first in baseline.items():
    c = candidate[key]
    r = repeat[key]
    b_ns = median(v[0] for v in first)
    c_ns = median(v[0] for v in c)
    r_ns = median(v[0] for v in r)
    b_alloc = median(v[1] for v in first) / 1048576
    c_alloc = median(v[1] for v in c) / 1048576
    print(f"| {key[0]} | {key[1]} | {b_ns / 1e6:.3f} | {c_ns / 1e6:.3f} | {r_ns / 1e6:.3f} | {c_ns / b_ns:.3f} | {b_alloc:.3f} | {c_alloc:.3f} |")
