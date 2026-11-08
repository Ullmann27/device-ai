#!/usr/bin/env python3
"""Prospective synthetic causal worlds; labels never enter the strategy API."""
import hashlib
import json
from pathlib import Path
import random
import sys


def generate(split):
    seed, count = {"dev": (41073, 64), "test": (92861, 192)}[split]
    rng = random.Random(seed)
    worlds = []
    for number in range(count):
        size = rng.randint(2, 6)
        causes = rng.sample(range(1, 8), size)
        if number % 12 == 0:
            causes[-1] = causes[0]
        truth = rng.randrange(size)
        favored = rng.choice([i for i in range(size) if i != truth])
        target = rng.choice(causes)
        bit = 1 << rng.randrange(3)
        signals = [(target, 7 ^ target)] * 3 + [(bit, 0)] * 3
        signals += [(rng.randrange(1, 8), 0) for _ in range(3)]
        rng.shuffle(signals)
        probes = []
        for index, (required, blocked) in enumerate(signals):
            probes.append({
                "id": f"p{index}", "cost": rng.randint(1, 4),
                "requiredRoots": required, "blockedRoots": blocked,
                "predictions": [int(mask & required == required and mask & blocked == 0) for mask in causes],
            })
        worlds.append({
            "id": f"{split}-{number:03d}", "stratum": number % 4,
            "causes": causes, "truth": truth,
            "priors": [100 if i == favored else 1 for i in range(size)], "probes": probes,
        })
    return {"schemaVersion": 1, "origin": "prospective self-authored; not blind",
            "split": split, "seed": seed, "worlds": worlds}


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("dev", "test"):
        raise SystemExit("usage: h4-worlds.py dev|test OUT_DIR")
    document = generate(sys.argv[1])
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    path = out / "worlds.json"
    path.write_text(json.dumps(document, indent=2) + "\n")
    rows = ["id\tstratum\ttruth\tpriors\tprobes"]
    for world in document["worlds"]:
        probes = ";".join(
            f'{p["id"]}:{p["cost"]}:' + "".join(map(str, p["predictions"])) for p in world["probes"])
        rows.append(f'{world["id"]}\t{world["stratum"]}\t{world["truth"]}\t' +
                    ",".join(map(str, world["priors"])) + "\t" + probes)
    (out / "worlds.tsv").write_text("\n".join(rows) + "\n")
    print(json.dumps({"split": document["split"], "worlds": len(document["worlds"]),
                      "jsonSha256": hashlib.sha256(path.read_bytes()).hexdigest()}))


if __name__ == "__main__":
    main()
