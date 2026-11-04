#!/usr/bin/env python3
"""Read-only task scoring against the independent state interpreter after freeze."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path

from oracle import verify

ROOT = Path(__file__).resolve().parent
CRITICAL = {"move_other", "query_then_other"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, default=ROOT / "run-v1")
    args = parser.parse_args()
    run = args.run.resolve(strict=True)
    if not run.is_relative_to(ROOT):
        raise ValueError("analysis output must remain inside task directory")
    completed = json.loads((run / "completed.json").read_text())
    if not completed["testEvaluated"] or completed["trainingAfterFreeze"]:
        raise ValueError("study not complete or not frozen")
    expected_models = {"baseline"} | {f"{a}-{s}" for a in ("mixed", "curriculum") for s in (202611041, 202611042)}
    if len(completed["models"]) != 5 or {m["name"] for m in completed["models"]} != expected_models:
        raise ValueError("incomplete preregistered model set")
    if len(completed["evaluations"]) != 10:
        raise ValueError("incomplete evaluation receipt set")
    for receipt in completed["evaluations"]:
        for stream in ("stdout", "stderr"):
            if hashlib.sha256((run / receipt[stream]).read_bytes()).hexdigest() != receipt[stream + "Sha256"]:
                raise ValueError("evaluation output identity mismatch")
    if hashlib.sha256((run / "freeze.json").read_bytes()).hexdigest() != completed["freezeSha256"]:
        raise ValueError("freeze identity mismatch")
    truth = {}
    heuristic_scores = {}
    for split, stem in [("dev", "dev"), ("test", "test.sealed")]:
        rows = [json.loads(line) for line in (ROOT / "data" / f"{stem}.jsonl").read_text().splitlines()]
        truth[split] = {r["id"]: r for r in rows}
        for r in rows:
            if verify(r["prompt"], r["answer"]) != r["oracle"]:
                raise ValueError("independent oracle mismatch")
        heuristic_scores[split] = {}
        for name in ["initialOwner", "lastMentionedPerson"]:
            heuristic_scores[split][name] = {"correct": sum(r["oracle"][name] == r["answer"] for r in rows),
                "total": len(rows), "criticalCorrect": sum(r["oracle"][name] == r["answer"] for r in rows if r["family"] in CRITICAL),
                "criticalTotal": sum(r["family"] in CRITICAL for r in rows)}
    observations = {}; summaries = []

    def metrics(rows: list[dict]) -> dict:
        return {"n": len(rows), "freeCorrect": sum(r["freeCorrect"] for r in rows),
                "choiceCorrect": sum(r["choiceCorrect"] for r in rows),
                "choiceWithEosCorrect": sum(r["choiceWithEosCorrect"] for r in rows),
                "immediateEos": sum(r["newTokens"] == 0 and r["stop"] == "EOS" for r in rows),
                "meanCorrectAnswerNll": sum(r["correctAnswerNll"] for r in rows) / len(rows),
                "meanNewTokens": sum(r["newTokens"] for r in rows) / len(rows)}

    for model in completed["models"]:
        for split in ("dev", "test"):
            events = [json.loads(line) for line in (run / f"{model['name']}-{split}.stdout").read_text().splitlines()]
            rows = [x for x in events if x["kind"] == "case"]
            if {r["id"] for r in rows} != set(truth[split]) or len(rows) != len(truth[split]):
                raise ValueError("case set mismatch")
            for row in rows:
                expected = truth[split][row["id"]]
                if row["prompt"] != expected["prompt"] or row["answer"] != expected["answer"] or row["family"] != expected["family"]:
                    raise ValueError("evaluation text/label mismatch")
                if row["freeCorrect"] != (row["text"].strip() == row["answer"]):
                    raise ValueError("free score mismatch")
                choice = min(row["choiceNll"], key=row["choiceNll"].get)
                if choice != row["choice"] or row["choiceCorrect"] != (choice == row["answer"]):
                    raise ValueError("choice score mismatch")
            observations[(model["name"], split)] = {r["id"]: r for r in rows}
            families = sorted({r["family"] for r in rows})
            confusion = {gold: dict(Counter(r["choice"] for r in rows if r["answer"] == gold)) for gold in "ABCD"}
            summaries.append({"model": model["name"], "arm": model["arm"], "seed": model["seed"], "split": split,
                "capacityPass": model["capacity"]["pass"], "overall": metrics(rows),
                "critical": metrics([r for r in rows if r["family"] in CRITICAL]),
                "families": {family: metrics([r for r in rows if r["family"] == family]) for family in families},
                "answerClasses": {answer: metrics([r for r in rows if r["answer"] == answer]) for answer in "ABCD"},
                "choiceConfusion": confusion})
    paired = []; decisions = []
    for summary in summaries:
        if summary["arm"] == "baseline":
            continue
        split = summary["split"]
        current = observations[(summary["model"], split)]
        base = observations[("baseline", split)]
        comparisons = {}
        for score in ("freeCorrect", "choiceCorrect", "choiceWithEosCorrect"):
            comparisons[score] = {"wins": sum(current[i][score] and not base[i][score] for i in base),
                                  "losses": sum(base[i][score] and not current[i][score] for i in base),
                                  "n": len(base)}
        paired.append({"model": summary["model"], "split": split, "scores": comparisons})
        if split == "test":
            for score in ("freeCorrect", "choiceCorrect"):
                base_correct = sum(r[score] for r in base.values())
                overall = summary["overall"]
                critical = summary["critical"]
                delta = (overall[score] - base_correct) / overall["n"]
                beats_heuristics = all(overall[score] > h["correct"] for h in heuristic_scores[split].values())
                passed = delta >= .10 and critical[score] / critical["n"] >= .50 and beats_heuristics
                decisions.append({"model": summary["model"], "arm": summary["arm"], "score": score,
                                  "deltaVsBaseline": delta, "criticalAtLeastHalf": critical[score] / critical["n"] >= .50,
                                  "beatsBothHeuristics": beats_heuristics, "pass": passed})
    mixed = {score: all(d["pass"] for d in decisions if d["arm"] == "mixed" and d["score"] == score)
             for score in ("freeCorrect", "choiceCorrect")}
    output = {"schema": "lm1-state-binding-v1", "completedSha256": hashlib.sha256((run / "completed.json").read_bytes()).hexdigest(),
              "heuristics": heuristic_scores, "summaries": summaries, "pairedVsBaseline": paired,
              "predeclaredPerSeedDecisions": decisions, "primaryMixedBothSeedsPass": mixed,
              "noModelSelection": True, "noPromotion": True,
              "scope": "finite synthetic assignment-overwrite-query task, not general language or simulation"}
    (run / "analysis.json").write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"heuristics": heuristic_scores, "primaryMixedBothSeedsPass": mixed,
                      "test": [{"model": r["model"], "overall": r["overall"], "critical": r["critical"]}
                               for r in summaries if r["split"] == "test"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
