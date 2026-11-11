#!/usr/bin/env python3
"""Run declared train-only shortcut controls on open DEV; no LM1 training."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import json
from pathlib import Path
import re
import sys

import numpy as np
import sklearn
from sklearn.feature_extraction.text import CountVectorizer, TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import make_pipeline
from sklearn.tree import DecisionTreeClassifier

from frozen_tokenizer import load_tokenizer
from make_semantic_pilot import bag, build, json_bytes, normalize, sha


def metrics(rows, metadata, predictions):
    by_id = {r["id"]: (r["label"], str(p)) for r, p in zip(rows, predictions)}
    pairs = defaultdict(list)
    families = defaultdict(list)
    for item in metadata:
        pair = by_id[item["id"]]
        pairs[item["pairId"]].append(pair)
        families[item["familyId"]].append(pair)
    for pair in pairs.values():
        if len(pair) != 2 or pair[0][0] == pair[1][0]:
            raise ValueError("invalid opposing pair")
    return {
        "correct": sum(y == p for y, p in by_id.values()), "total": len(rows),
        "accuracy": sum(y == p for y, p in by_id.values()) / len(rows),
        "pairsBothCorrect": sum(all(y == p for y, p in pair) for pair in pairs.values()),
        "pairsTotal": len(pairs),
        "samePredictionPairs": sum(pair[0][1] == pair[1][1] for pair in pairs.values()),
        "predictionCounts": dict(sorted(Counter(str(p) for p in predictions).items())),
        "correctPerClass": {str(k): sum(y == p == str(k) for y, p in by_id.values()) for k in range(4)},
        "byFamily": {k: {"correct": sum(y == p for y, p in values), "total": len(values)} for k, values in sorted(families.items())},
    }


def empirical_ceiling(rows, feature):
    groups = defaultdict(Counter)
    for row in rows:
        groups[feature(row)][row["label"]] += 1
    return {"maxCorrect": sum(max(counts.values()) for counts in groups.values()),
            "total": len(rows), "featureGroups": len(groups),
            "scope": "oracle chooses each feature-group label; descriptive upper bound, not fitted test model"}


ACTION_RE = re.compile(r"nichts tun|öffnen|suchen|speichern", re.I)
ACTION_LABEL = {"nichts tun": "0", "öffnen": "1", "suchen": "2", "speichern": "3"}


def action_rule(text, last=False):
    hits = ACTION_RE.findall(text)
    return ACTION_LABEL[hits[-1 if last else 0].casefold()] if hits else "0"


def audit(repo, data):
    expected, _, generated = build(repo)
    for name, content in expected.items():
        if (data / name).read_bytes() != content:
            raise ValueError("regeneration mismatch: " + name)
    rows = {split: list(csv.DictReader((data / f"{split}.tsv").read_text().splitlines(), delimiter="\t")) for split in ("train", "dev")}
    metas = {split: json.loads((data / f"{split}-metadata.json").read_text()) for split in rows}
    train, dev = rows["train"], rows["dev"]
    x_train, y_train = [r["prompt"] for r in train], [r["label"] for r in train]
    x_dev = [r["prompt"] for r in dev]
    report = {
        "schema": "lm1-semantic-shortcut-audit-v1", "status": "PENDING", "gateThreshold": 0.90,
        "fittingPolicy": "all learned parameters fit TRAIN only; fixed algorithms, no DEV hyperparameter search",
        "modelUpdates": 0, "modelInference": 0, "heldoutDataAccess": False,
        "environment": {"python": sys.version, "numpy": np.__version__, "sklearn": sklearn.__version__},
        "sourceSha256": sha(Path(__file__).read_bytes()),
        "inputHashes": {k: sha((data / k).read_bytes()) for k in expected},
        "controls": {}, "structuralAudit": {}, "tokenAnalysis": {},
    }
    predictions = {"train": {}, "dev": {}}
    majority = sorted(Counter(y_train), key=lambda x: (-Counter(y_train)[x], x))[0]
    rules = {
        "majority": lambda text: majority,
        "legacy_nicht_filter": lambda text: "0" if "nicht" in text.casefold() else "3",
        "negation_parity": lambda text: "0" if len(re.findall(r"\bnicht\b", text.casefold())) % 2 else "3",
        "first_action": lambda text: action_rule(text),
        "last_action": lambda text: action_rule(text, True),
    }
    for name, rule in rules.items():
        report["controls"][name] = {}
        for split in rows:
            pred = [rule(r["prompt"]) for r in rows[split]]
            predictions[split][name] = pred
            report["controls"][name][split] = metrics(rows[split], metas[split], pred)
    classifiers = {
        "word_unigram_logistic": make_pipeline(CountVectorizer(token_pattern=r"(?u)\b\w+\b"), LogisticRegression(C=1.0, max_iter=1000, random_state=20261111)),
        "word_bigram_logistic": make_pipeline(TfidfVectorizer(ngram_range=(1, 2), token_pattern=r"(?u)\b\w+\b"), LogisticRegression(C=1.0, max_iter=1000, random_state=20261111)),
        "char_ngram_logistic": make_pipeline(TfidfVectorizer(analyzer="char", ngram_range=(3, 5)), LogisticRegression(C=1.0, max_iter=1000, random_state=20261111)),
        "word_bigram_naive_bayes": make_pipeline(CountVectorizer(ngram_range=(1, 2), token_pattern=r"(?u)\b\w+\b"), MultinomialNB(alpha=1.0)),
    }
    for name, model in classifiers.items():
        model.fit(x_train, y_train)
        report["controls"][name] = {}
        for split in rows:
            pred = list(map(str, model.predict([r["prompt"] for r in rows[split]])))
            predictions[split][name] = pred
            report["controls"][name][split] = metrics(rows[split], metas[split], pred)
    tok = load_tokenizer(repo)
    def length_features(row):
        p = row["prompt"]
        return [len(p), len(p.encode()), len(tok.encode("Auftrag: " + p + "\n")) + 1,
                len(re.findall(r"\w+", p)), len(re.findall(r"\bnicht\b", p.casefold()))]
    length_model = DecisionTreeClassifier(max_depth=3, random_state=20261111)
    length_model.fit([length_features(r) for r in train], y_train)
    report["controls"]["length_depth3_tree"] = {}
    for split in rows:
        pred = list(map(str, length_model.predict([length_features(r) for r in rows[split]])))
        predictions[split]["length_depth3_tree"] = pred
        report["controls"]["length_depth3_tree"][split] = metrics(rows[split], metas[split], pred)
        report["structuralAudit"][split] = {
            "wordBagCeiling": empirical_ceiling(rows[split], lambda r: tuple(sorted(bag(r["prompt"]).items()))),
            "lengthAndNegationCountCeiling": empirical_ceiling(rows[split], lambda r: tuple(length_features(r))),
            "allPairsSameWordBag": generated["splits"][split]["allPairsSameWordBag"],
            "allPairsSameBpeLength": generated["splits"][split]["allPairsSameBpeLength"],
        }
        report["tokenAnalysis"][split] = {
            "minPrefixTokens": min(m["prefixTokens"] for m in metas[split]),
            "maxPrefixTokens": max(m["prefixTokens"] for m in metas[split]),
            "classPrefixTokens": generated["splits"][split]["classPrefixTokens"],
            "unknownTokenCount": 0,
            "unknownTokenReason": "byte-level vocabulary encodes every UTF-8 byte; does not imply semantic familiarity",
        }
    train_words = set().union(*(bag(r["prompt"]) for r in train))
    dev_words = set().union(*(bag(r["prompt"]) for r in dev))
    report["tokenAnalysis"]["devWordsAbsentFromTrain"] = sorted(dev_words - train_words)
    legacy = repo / "experimental/weight-space-20261110/data/note-contrasts-v1"
    old_prompts = set()
    for split in ("train", "dev"):
        old_prompts.update(normalize(r["prompt"]) for r in csv.DictReader((legacy / f"{split}.tsv").read_text().splitlines(), delimiter="\t"))
    overlap = [r["id"] for split in rows.values() for r in split if normalize(r["prompt"]) in old_prompts]
    if overlap:
        raise ValueError("historical data overlap")
    report["historicalPromptOverlap"] = overlap
    best = max(report["controls"], key=lambda k: report["controls"][k]["dev"]["accuracy"])
    report["bestDevControl"] = best
    report["bestDevAccuracy"] = report["controls"][best]["dev"]["accuracy"]
    report["status"] = "PASS_SHORTCUT_PILOT" if report["bestDevAccuracy"] < report["gateThreshold"] else "REJECT_SHORTCUT_PILOT"
    report["limitations"] = [
        "Passing declared controls does not exclude every possible shortcut.",
        "Template correlations mean row counts are not independent samples.",
        "No neural training or independent holdout inference is part of this audit.",
        "Token lengths use an independent port; native per-row parity is required before training.",
        "The same author designed the data and performed this open pilot audit.",
    ]
    raw = {split: [{"id": row["id"], "expected": row["label"], "predictions": {name: pred[i] for name, pred in predictions[split].items()}} for i, row in enumerate(rows[split])] for split in rows}
    return report, raw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report, raw = audit(args.repo, args.data)
    args.output.mkdir(parents=True, exist_ok=False)
    raw_bytes = json_bytes(raw)
    report["rawPredictionsSha256"] = sha(raw_bytes)
    (args.output / "predictions.json").write_bytes(raw_bytes)
    (args.output / "report.json").write_bytes(json_bytes(report))
    print(json.dumps({"status": report["status"], "bestDevControl": report["bestDevControl"], "bestDevAccuracy": report["bestDevAccuracy"], "controls": {k: {"train": v["train"]["correct"], "dev": v["dev"]["correct"], "devPairs": v["dev"]["pairsBothCorrect"]} for k, v in report["controls"].items()}}, sort_keys=True))


if __name__ == "__main__":
    main()
