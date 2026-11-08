#!/usr/bin/env python3
"""Controls for the H4-B scorer: each synthetic log row must land in exactly the intended category."""
import csv
import importlib.util
import json
from pathlib import Path
import sys
import tempfile

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("h4b_score", Path(__file__).with_name("h4b-score.py"))
scorer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scorer)

STORIES = {
    "schemaVersion": 1, "split": "unit", "seed": 0, "stories": [
        {"id": "s-topic", "stratum": "long", "turns": [{"text": "a", "topic": 0}, {"text": "d", "topic": None},
                                                       {"text": "b", "topic": 1}],
         "returnText": "r", "key": {"kind": "TOPIC", "turn": 0}},
        {"id": "s-amb", "stratum": "ambiguous", "turns": [{"text": "a", "topic": 0}, {"text": "b", "topic": 1},
                                                          {"text": "c", "topic": 2}],
         "returnText": "r", "key": {"kind": "AMBIGUOUS", "turns": [0, 1]}},
        {"id": "s-none", "stratum": "none", "turns": [{"text": "a", "topic": 0}, {"text": "b", "topic": 1}],
         "returnText": "r", "key": {"kind": "NONE"}},
    ]}


def run(rows):
    with tempfile.TemporaryDirectory() as folder:
        stories = Path(folder) / "stories.json"
        stories.write_text(json.dumps(STORIES), encoding="utf-8")
        log = Path(folder) / "log.tsv"
        with log.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream, delimiter="\t")
            writer.writerow(["id", "arm", "kind", "target", "turn_threads", "wall_ns", "cpu_ns"])
            for row in rows:
                writer.writerow(row + ["1", "1"])
        return scorer.audit(stories, log)


def expect(description, rows, wanted):
    result = run(rows)
    assert result["cases"] == wanted, f"{description}: {result['cases']} != {wanted}"
    print("ok", description)


base_amb = ["s-amb", "x", "NOMATCH", "", "t0,t1,t2"]
base_none = ["s-none", "x", "NOMATCH", "", "t0,t1"]
expect("pure binding to key topic is CORRECT",
       [["s-topic", "x", "RESOLVED", "t0", "t0,t9,t1"], base_amb, base_none],
       {"s-topic": "CORRECT", "s-amb": "MISSED", "s-none": "NOMATCH_CORRECT"})
expect("binding to the other topic is WRONG",
       [["s-topic", "x", "RESOLVED", "t1", "t0,t9,t1"], base_amb, base_none],
       {"s-topic": "WRONG", "s-amb": "MISSED", "s-none": "NOMATCH_CORRECT"})
expect("thread that absorbed two topics is impure and WRONG",
       [["s-topic", "x", "RESOLVED", "t0", "t0,t9,t0"], base_amb, base_none],
       {"s-topic": "WRONG", "s-amb": "MISSED", "s-none": "NOMATCH_CORRECT"})
expect("distractor absorbed into the key thread stays CORRECT",
       [["s-topic", "x", "RESOLVED", "t0", "t0,t0,t1"], base_amb, base_none],
       {"s-topic": "CORRECT", "s-amb": "MISSED", "s-none": "NOMATCH_CORRECT"})
expect("ambiguous including the key topic is AMBIGUOUS_UNDER",
       [["s-topic", "x", "AMBIGUOUS", "t0|t1", "t0,t9,t1"], base_amb, base_none],
       {"s-topic": "AMBIGUOUS_UNDER", "s-amb": "MISSED", "s-none": "NOMATCH_CORRECT"})
expect("ambiguous without the key topic is WRONG",
       [["s-topic", "x", "AMBIGUOUS", "t1|t9", "t0,t9,t1"], base_amb, base_none],
       {"s-topic": "WRONG", "s-amb": "MISSED", "s-none": "NOMATCH_CORRECT"})
expect("label collision (two ids for one label) counts as unresolved candidate",
       [["s-topic", "x", "AMBIGUOUS", "t0/t5|t1", "t0,t9,t1"], base_amb, base_none],
       {"s-topic": "WRONG", "s-amb": "MISSED", "s-none": "NOMATCH_CORRECT"})
expect("ambiguous key: both candidates listed is AMBIGUOUS_CORRECT",
       [["s-topic", "x", "NOMATCH", "", "t0,t9,t1"], ["s-amb", "x", "AMBIGUOUS", "t1|t0", "t0,t1,t2"], base_none],
       {"s-topic": "MISSED", "s-amb": "AMBIGUOUS_CORRECT", "s-none": "NOMATCH_CORRECT"})
expect("ambiguous key: confident binding to one of them is OVERCOMMIT",
       [["s-topic", "x", "NOMATCH", "", "t0,t9,t1"], ["s-amb", "x", "RESOLVED", "t1", "t0,t1,t2"], base_none],
       {"s-topic": "MISSED", "s-amb": "OVERCOMMIT", "s-none": "NOMATCH_CORRECT"})
expect("ambiguous key: binding elsewhere is WRONG",
       [["s-topic", "x", "NOMATCH", "", "t0,t9,t1"], ["s-amb", "x", "RESOLVED", "t2", "t0,t1,t2"], base_none],
       {"s-topic": "MISSED", "s-amb": "WRONG", "s-none": "NOMATCH_CORRECT"})
expect("no referent: any binding or suggestion is WRONG and counted for the no-referent gate",
       [["s-topic", "x", "NOMATCH", "", "t0,t9,t1"], base_amb, ["s-none", "x", "AMBIGUOUS", "t0|t1", "t0,t1"]],
       {"s-topic": "MISSED", "s-amb": "MISSED", "s-none": "WRONG"})
result = run([["s-topic", "x", "NOMATCH", "", "t0,t9,t1"], base_amb, ["s-none", "x", "RESOLVED", "t0", "t0,t1"]])
assert result["totals"]["wrongOnNoReferent"] == 1 and result["totals"]["wrongBindings"] == 1
print("ok no-referent wrong binding feeds both gate counters")
try:
    run([["s-topic", "x", "NOMATCH", "", "t0,t9,t1"], base_amb])
except ValueError:
    print("ok incomplete log is rejected")
else:
    raise AssertionError("incomplete log accepted")
try:
    run([["s-topic", "x", "NOMATCH", "", "t0,t9"], base_amb, base_none])
except ValueError:
    print("ok turn/thread mismatch is rejected")
else:
    raise AssertionError("turn mismatch accepted")
print("all scorer controls passed")
