#!/usr/bin/env python3
"""Independent scorer for H4-B replay logs. It only sees story structure and harness output."""
import csv
import json
from pathlib import Path
import sys

CATEGORIES = ["CORRECT", "WRONG", "OVERCOMMIT", "AMBIGUOUS_CORRECT", "AMBIGUOUS_UNDER", "NOMATCH_CORRECT", "MISSED"]
WRONG_KINDS = ("WRONG", "OVERCOMMIT")


def thread_topics(story, turn_threads):
    """Maps every thread id to the set of story topics its absorbed turns belong to."""
    topics = {}
    if len(turn_threads) != len(story["turns"]):
        raise ValueError(f"{story['id']}: turn/thread count mismatch")
    for turn, thread in zip(story["turns"], turn_threads):
        if thread == "-":
            continue
        topics.setdefault(thread, set()).add(turn["topic"])
    return topics


def topic_of_turn(story, turn_index):
    return story["turns"][turn_index]["topic"]


def pure_topic(topics, thread):
    """Returns the single real topic of a thread, or None if it is mixed or unknown."""
    members = {t for t in topics.get(thread, set()) if t is not None}
    return next(iter(members)) if len(members) == 1 else None


def categorize(story, row):
    kind, target = row["kind"], row["target"]
    topics = thread_topics(story, row["turn_threads"].split(","))
    key = story["key"]
    if key["kind"] == "NONE":
        return "NOMATCH_CORRECT" if kind == "NOMATCH" else "WRONG"
    if key["kind"] == "TOPIC":
        wanted = topic_of_turn(story, key["turn"])
        if kind == "NOMATCH":
            return "MISSED"
        if kind == "RESOLVED":
            return "CORRECT" if pure_topic(topics, target) == wanted else "WRONG"
        candidates = [pure_topic(topics, ids) if "/" not in ids else None for ids in target.split("|")]
        return "AMBIGUOUS_UNDER" if wanted in candidates else "WRONG"
    if key["kind"] == "AMBIGUOUS":
        wanted = {topic_of_turn(story, turn) for turn in key["turns"]}
        if kind == "NOMATCH":
            return "MISSED"
        if kind == "RESOLVED":
            return "OVERCOMMIT" if pure_topic(topics, target) in wanted else "WRONG"
        candidates = {pure_topic(topics, ids) if "/" not in ids else None for ids in target.split("|")}
        return "AMBIGUOUS_CORRECT" if candidates == wanted else "WRONG"
    raise ValueError(f"unknown key kind {key['kind']}")


def audit(stories_path, log_path):
    document = json.loads(Path(stories_path).read_text(encoding="utf-8"))
    stories = {story["id"]: story for story in document["stories"]}
    with open(log_path, encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    if [row["id"] for row in rows] != list(stories):
        raise ValueError("log does not cover exactly the story set in order")
    arms = {row["arm"] for row in rows}
    if len(arms) != 1:
        raise ValueError(f"mixed arms in one log: {sorted(arms)}")
    cases = {}
    per_stratum = {}
    for row in rows:
        story = stories[row["id"]]
        category = categorize(story, row)
        cases[row["id"]] = category
        bucket = per_stratum.setdefault(story["stratum"], {c: 0 for c in CATEGORIES})
        bucket[category] += 1
    totals = {c: sum(1 for v in cases.values() if v == c) for c in CATEGORIES}
    totals["wrongBindings"] = sum(totals[c] for c in WRONG_KINDS)
    totals["wrongOnNoReferent"] = sum(1 for sid, c in cases.items() if stories[sid]["stratum"] == "none" and c in WRONG_KINDS)
    totals["acceptable"] = totals["CORRECT"] + totals["AMBIGUOUS_CORRECT"] + totals["NOMATCH_CORRECT"]
    return {"arm": arms.pop(), "stories": len(rows), "totals": totals, "perStratum": per_stratum, "cases": cases}


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: h4b-score.py stories.json log.tsv")
    result = audit(sys.argv[1], sys.argv[2])
    print(json.dumps({k: v for k, v in result.items() if k != "cases"}, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    if sys.flags.optimize:
        raise SystemExit("run without -O; assertions are part of the audit")
    main()
