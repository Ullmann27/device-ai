#!/usr/bin/env python3
"""Create frozen bounded ownership examples without using a model or old corpus."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import itertools
import json
from pathlib import Path
import random

from oracle import self_check, verify

SEED = 2026110402
SCHEDULE_SEEDS = (202611041, 202611042)
FAMILIES = {"copy": 0, "move_query": 1, "move_other": 1, "twice_query": 2,
            "query_then_other": 2, "other_then_query": 2}
PAIRS = {"train": [("Ball", "Ring"), ("Ring", "Stift"), ("Stift", "Schlüssel"), ("Schlüssel", "Ball")],
         "dev": [("Ball", "Stift")], "test": [("Ring", "Schlüssel")]}
TRAIN_PER_ANSWER = {"copy": 16, "move_query": 24, "move_other": 24, "twice_query": 16,
                    "query_then_other": 24, "other_then_query": 24}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def case(pair: tuple[str, str], people: tuple[str, ...], query_index: int, family: str) -> dict:
    owners = {pair[0]: people[0], pair[1]: people[1]}
    query, other = pair[query_index], pair[1 - query_index]
    planned: list[tuple[str, str]] = {
        "copy": [], "move_query": [(query, people[2])], "move_other": [(other, people[2])],
        "twice_query": [(query, people[2]), (query, people[3])],
        "query_then_other": [(query, people[2]), (other, people[3])],
        "other_then_query": [(other, people[3]), (query, people[2])],
    }[family]
    sentences = [f"{people[0]} hält den {pair[0]}.", f"{people[1]} hält den {pair[1]}."]
    events = []
    for obj, recipient in planned:
        sender = owners[obj]
        sentences.append(f"{sender} gibt den {obj} an {recipient}.")
        events.append({"sender": sender, "object": obj, "receiver": recipient})
        owners[obj] = recipient
    prompt = " ".join(sentences) + f" Wer hält den {query}? Antwort:"
    answer = owners[query]
    verified = verify(prompt, answer)
    if verified["events"] != FAMILIES[family]:
        raise ValueError("independent parser disagrees with planned depth")
    if family in {"move_other", "query_then_other"} and verified["lastMentionedPerson"] == answer:
        raise ValueError("critical case fails to defeat last-mention heuristic")
    return {"id": sha(prompt.encode())[:20], "family": family, "depth": len(events),
            "pair": list(pair), "query": query, "prompt": prompt, "answer": answer,
            "initial": [{"object": obj, "owner": people[i]} for i, obj in enumerate(pair)],
            "events": events, "oracle": verified}


def main() -> None:
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=root / "data")
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists() or not output.is_relative_to(root):
        raise ValueError("use a new subdirectory of research/round2/language")
    verifier_checks = self_check()
    splits = {}
    for split, pairs in PAIRS.items():
        pools = {}
        for pair, people, q, family in itertools.product(pairs, itertools.permutations("ABCD"), (0, 1), FAMILIES):
            item = case(pair, people, q, family)
            pools.setdefault((family, item["answer"]), {})[item["id"]] = item
        selected = []
        for family in FAMILIES:
            for answer in "ABCD":
                candidates = list(pools[(family, answer)].values())
                candidates.sort(key=lambda x: sha(f"{SEED}|{split}|{x['id']}".encode()))
                count = TRAIN_PER_ANSWER[family] if split == "train" else (2 if split == "dev" else 4)
                if len(candidates) < count:
                    raise ValueError("insufficient independently verified cases")
                selected.extend(candidates[:count])
        selected.sort(key=lambda x: sha(f"{SEED}|ordering|{x['id']}".encode()))
        splits[split] = selected
    for a, b in itertools.combinations(splits, 2):
        if {x["prompt"] for x in splits[a]} & {x["prompt"] for x in splits[b]}:
            raise ValueError("prompt leakage")
        if {frozenset(x["pair"]) for x in splits[a]} & {frozenset(x["pair"]) for x in splits[b]}:
            raise ValueError("object-pair leakage")
    if [len(splits[s]) for s in ("train", "dev", "test")] != [512, 48, 96]:
        raise ValueError("wrong split sizes")
    output.mkdir(parents=True)
    for split, rows in splits.items():
        stem = "test.sealed" if split == "test" else split
        (output / f"{stem}.jsonl").write_text("".join(json.dumps(x, ensure_ascii=False, sort_keys=True) + "\n" for x in rows))
        (output / f"{stem}.tsv").write_text("".join(f"{x['id']}\t{x['family']}\t{x['depth']}\t{x['answer']}\t{x['prompt']}\n" for x in rows))
    for seed in SCHEDULE_SEEDS:
        mixed = list(range(512)) * 2
        random.Random(seed).shuffle(mixed)
        curriculum = sorted(mixed, key=lambda index: splits["train"][index]["depth"])
        for arm, order in [("mixed", mixed), ("curriculum", curriculum)]:
            if Counter(order) != Counter({i: 2 for i in range(512)}):
                raise ValueError("schedule multiset differs")
            (output / f"schedule-{seed}-{arm}.txt").write_text("\n".join(map(str, order)) + "\n")
    files = {p.name: {"sha256": sha(p.read_bytes()), "bytes": p.stat().st_size} for p in sorted(output.iterdir()) if p.is_file()}
    manifest = {"schema": "lm1-state-binding-v1", "seed": SEED, "scheduleSeeds": SCHEDULE_SEEDS,
                "files": files, "splits": {name: {"count": len(rows), "answers": dict(Counter(x["answer"] for x in rows)),
                   "families": dict(Counter(x["family"] for x in rows)), "objectPairs": [list(p) for p in PAIRS[name]]}
                   for name, rows in splits.items()},
                "verifierChecks": verifier_checks, "wholePromptOverlap": False, "objectPairOverlap": False,
                "generatorSha256": sha(Path(__file__).read_bytes()), "oracleSha256": sha((root / "oracle.py").read_bytes())}
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"output": str(output), "counts": {k: len(v) for k, v in splits.items()},
                      "verifierChecksPassed": len(verifier_checks), "manifestSha256": sha((output / "manifest.json").read_bytes())}, indent=2))


if __name__ == "__main__":
    main()
