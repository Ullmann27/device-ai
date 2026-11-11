#!/usr/bin/env python3
"""Validate raw native predictions and apply preregistered gates."""

import base64
import collections
import math
import numpy as np

SEED = 2026111103
DRAWS = 20000
CONSTRUCTIONS = {
    "recipient": "addressee", "delegation": "addressee",
    "parenthesis": "parentheses", "insertion": "parentheses",
    "relative_request": "relative_clause", "relative_exclusion": "relative_clause",
    "embedded_preference": "subordinate_clause", "embedded_refusal": "subordinate_clause",
}
FIELDS = {
    "INPUT": "file sha256", "CONFIG": "mode parameters context checkpointSha256 tokenizerSha256 updates",
    "LABEL": "label token", "LENGTH": "split id prefixTokens promptTokens bytes",
    "CASE": "split id expected predictedRestricted tokens ended exact firstEos p0 p1 p2 p3 decodedBase64",
    "TEXT": "split id nats bytes bpb tokens", "CAPACITY": "split id tokens eos generated prefixBase64 decodedBase64",
    "RESULT": "updates inputUnchanged elapsedMillis",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def finite(value):
    answer = float(value)
    require(math.isfinite(answer), "nonfinite value")
    return answer


def boolean(value):
    require(value in ("true", "false"), "invalid boolean")
    return value == "true"


def parse(text):
    result = collections.defaultdict(list)
    for line in text.splitlines():
        fields = line.split("\t")
        pairs = [x.split("=", 1) for x in fields[1:]]
        require(all(len(x) == 2 for x in pairs), "malformed fields")
        row = dict(pairs)
        require(len(row) == len(pairs), "duplicate fields")
        require(fields[0] in FIELDS, "unknown record")
        require(set(row) == set(FIELDS[fields[0]].split()), "unexpected fields")
        result[fields[0]].append(row)
    require(result, "empty stdout")
    return result


def wilson(correct, total, z=1.959963984540054):
    require(0 <= correct <= total and total > 0, "invalid counts")
    p = correct / total
    denominator = 1 + z * z / total
    center = (p + z * z / (2 * total)) / denominator
    width = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / denominator
    return [max(0.0, center - width), min(1.0, center + width)]


def metrics(rows, predictions, metadata=None):
    require(len(rows) == len(predictions), "prediction count mismatch")
    gold = [str(row["label"]) for row in rows]
    pred = list(map(str, predictions))
    require(all(p in "0123" and len(p) == 1 for p in pred), "invalid class")
    correct = [a == b for a, b in zip(gold, pred)]
    confusion = [[sum(a == str(i) and b == str(j) for a, b in zip(gold, pred)) for j in range(4)] for i in range(4)]
    result = {"correct": sum(correct), "total": len(rows), "accuracy": sum(correct) / len(rows),
              "wilson95": wilson(sum(correct), len(rows)), "confusion": confusion,
              "predictionCounts": dict(collections.Counter(pred)),
              "incorrectActionWhenNoAction": sum(a == "0" and b != "0" for a, b in zip(gold, pred))}
    if metadata is not None:
        by_id = {row["id"]: (c, p) for row, c, p in zip(rows, correct, pred)}
        pairs, families = collections.defaultdict(list), collections.defaultdict(list)
        for row in metadata:
            require(row["id"] in by_id, "unknown metadata id")
            c, p = by_id[row["id"]]
            pairs[row["pairId"]].append((c, p, row["familyId"]))
            families[row["familyId"]].append(c)
        require(len(by_id) == len(metadata), "metadata count mismatch")
        require(all(len(p) == 2 and p[0][2] == p[1][2] for p in pairs.values()), "invalid contrast pair")
        ordered = sorted(pairs)
        values = [pairs[key] for key in ordered]
        result.update({"pairCorrect": sum(all(x[0] for x in pair) for pair in values),
                       "pairsTotal": len(values),
                       "pairAccuracy": sum(all(x[0] for x in pair) for pair in values) / len(values),
                       "samePredictionPairs": sum(pair[0][1] == pair[1][1] for pair in values),
                       "familyAccuracy": {key: sum(value) / len(value) for key, value in families.items()},
                       "pairIds": ordered,
                       "pairRowAccuracy": [sum(x[0] for x in pair) / 2 for pair in values],
                       "pairSuccess": [int(all(x[0] for x in pair)) for pair in values],
                       "pairFamilies": [pair[0][2] for pair in values]})
    return result


def validate_native(records, sets, texts, metadata, checkpoint_sha):
    require(len(records["CONFIG"]) == 1 and len(records["RESULT"]) == 1, "missing config/result")
    config, finish = records["CONFIG"][0], records["RESULT"][0]
    require(config["mode"] == "evaluate" and config["parameters"] == "132160" and config["updates"] == "0", "wrong configuration")
    require(config["checkpointSha256"] == checkpoint_sha, "checkpoint mismatch")
    require(finish["updates"] == "0" and boolean(finish["inputUnchanged"]), "mutation or incomplete run")
    require(int(finish["elapsedMillis"]) >= 0, "invalid runtime")
    labels = {int(r["label"]): int(r["token"]) for r in records["LABEL"]}
    require(labels == {i: 48 + i for i in range(4)} and len(records["LABEL"]) == 4, "unexpected label tokenization")
    expected = {(split, row["id"]): row for split, rows in sets.items() for row in rows}
    seen, parsed = set(), collections.defaultdict(dict)
    for row in records["CASE"]:
        key = row["split"], row["id"]
        require(key in expected and key not in seen, "missing/duplicate case")
        seen.add(key)
        require(row["expected"] == expected[key]["label"], "wrong gold label")
        probabilities = [finite(row["p" + str(i)]) for i in range(4)]
        eos = finite(row["firstEos"])
        require(all(0 <= p <= 1 for p in probabilities) and 0 <= eos <= 1, "invalid probability")
        require(sum(probabilities) + eos <= 1.00001, "probability mass invalid")
        predicted = max(range(4), key=lambda i: probabilities[i])
        require(int(row["predictedRestricted"]) == predicted, "restricted prediction disagrees")
        tokens = [int(x) for x in row["tokens"].split(",")] if row["tokens"] else []
        ended = boolean(row["ended"])
        require(len(tokens) <= 4 and all(0 <= token < 511 for token in tokens), "invalid token trace")
        require(ended or len(tokens) == 4, "incomplete token trace")
        exact = ended and tokens == [labels[int(row["expected"])]]
        require(boolean(row["exact"]) == exact, "forged exact score")
        decoded = base64.b64decode(row["decodedBase64"], validate=True).decode("utf-8")
        parsed[key[0]][key[1]] = {"restricted": str(predicted), "exact": exact,
            "prematureEos": ended and not tokens, "firstEos": eos,
            "tokens": tokens, "ended": ended, "decoded": decoded}
    require(seen == set(expected), "incomplete cases")
    results = {}
    for split, rows in sets.items():
        predictions = [parsed[split][row["id"]]["restricted"] for row in rows]
        result = metrics(rows, predictions, metadata if split == "semantic" else None)
        result["exact"] = sum(parsed[split][row["id"]]["exact"] for row in rows)
        result["prematureEos"] = sum(parsed[split][row["id"]]["prematureEos"] for row in rows)
        result["firstEosMean"] = sum(parsed[split][row["id"]]["firstEos"] for row in rows) / len(rows)
        result["predictions"] = predictions
        if split == "semantic":
            pairs = collections.defaultdict(list)
            for row in metadata:
                pairs[row["pairId"]].append(parsed[split][row["id"]]["exact"])
            result["exactPairs"] = sum(all(p) for p in pairs.values())
        results[split] = result
    text_metrics, capacity = collections.defaultdict(dict), collections.defaultdict(dict)
    for row in records["TEXT"]:
        split, identity = row["split"], int(row["id"])
        require(split in texts and identity in range(len(texts[split])) and identity not in text_metrics[split], "invalid text row")
        nats, bpb, count = finite(row["nats"]), finite(row["bpb"]), int(row["bytes"])
        require(nats >= 0 and count == len(texts[split][identity].encode()), "text denominator mismatch")
        require(abs(bpb - nats / math.log(2) / count) < 1e-8, "text bpb mismatch")
        text_metrics[split][identity] = {"nats": nats, "bytes": count, "bpb": bpb}
    for row in records["CAPACITY"]:
        split, identity = row["split"], int(row["id"])
        require(split in texts and identity in range(len(texts[split])) and identity not in capacity[split], "invalid capacity row")
        tokens = [int(x) for x in row["generated"].split(",")] if row["generated"] else []
        require(int(row["tokens"]) == len(tokens) <= 32 and all(0 <= token < 511 for token in tokens), "invalid continuation")
        ended = boolean(row["eos"])
        require(ended or len(tokens) == 32, "truncated continuation")
        prefix = base64.b64decode(row["prefixBase64"], validate=True).decode("utf-8")
        require(prefix == texts[split][identity].split(".")[0][:16], "prefix mismatch")
        decoded = base64.b64decode(row["decodedBase64"], validate=True).decode("utf-8")
        capacity[split][identity] = {"tokens": len(tokens), "eos": ended, "decoded": decoded,
            "prefix": prefix, "uniqueTokens": len(set(tokens)),
            "adjacentRepeatFraction": sum(a == b for a, b in zip(tokens, tokens[1:])) / max(1, len(tokens) - 1)}
    for split, reference in texts.items():
        require(set(text_metrics[split]) == set(capacity[split]) == set(range(len(reference))), "incomplete text probes")
        records_for_split = list(text_metrics[split].values())
        results[split] = {"bpb": sum(r["nats"] for r in records_for_split) / math.log(2) / sum(r["bytes"] for r in records_for_split),
                          "perText": text_metrics[split], "continuations": capacity[split]}
    return results


def effect(candidate, comparison):
    require(candidate["pairIds"] == comparison["pairIds"], "pair alignment mismatch")
    rng = np.random.default_rng(SEED)
    count = len(candidate["pairIds"])
    samples = rng.integers(0, count, size=(DRAWS, count))
    families = sorted(set(candidate["pairFamilies"]))
    family_samples = rng.integers(0, len(families), size=(DRAWS, len(families)))
    construction_ids = np.asarray([CONSTRUCTIONS.get(f, f) for f in candidate["pairFamilies"]])
    constructions = sorted(set(construction_ids))
    construction_samples = rng.integers(0, len(constructions), size=(DRAWS, len(constructions)))
    result = {}
    for name, key in (("accuracy", "pairRowAccuracy"), ("pairAccuracy", "pairSuccess")):
        differences = np.asarray(candidate[key]) - np.asarray(comparison[key])
        ci = np.quantile(differences[samples].mean(axis=1), [0.0125, 0.9875])
        family_values = np.asarray([differences[np.asarray(candidate["pairFamilies"]) == family].mean() for family in families])
        family_ci = np.quantile(family_values[family_samples].mean(axis=1), [0.0125, 0.9875])
        leave_out = {family: float(differences[np.asarray(candidate["pairFamilies"]) != family].mean()) for family in families}
        construction_values = np.asarray([differences[construction_ids == group].mean() for group in constructions])
        construction_ci = np.quantile(construction_values[construction_samples].mean(axis=1), [0.0125, 0.9875])
        leave_construction = {group: float(differences[construction_ids != group].mean()) for group in constructions} if len(constructions) > 1 else {}
        result[name] = {"difference": float(differences.mean()), "pairedBootstrap97_5": ci.tolist(),
                        "familyBootstrap97_5": family_ci.tolist(), "leaveOneFamilyOut": leave_out,
                        "fourConstructionBootstrap97_5": construction_ci.tolist(),
                        "leaveOneConstructionOut": leave_construction,
                        "gainAtLeast10pp": bool(differences.mean() >= 0.10 - 1e-12),
                        "pairedLowerAboveZero": bool(ci[0] > 0)}
    return result


def retention_gates(candidate, original, rows):
    c, o = candidate["legacyRetention"], original["legacyRetention"]
    correct = [i for i, r in enumerate(rows) if o["predictions"][i] == r["label"]]
    kept = sum(c["predictions"][i] == rows[i]["label"] for i in correct)
    checks = {"historicalRestrictedLossAtMost5pp": c["accuracy"] >= o["accuracy"] - 0.05 - 1e-12,
              "historicalCorrectCasesAtLeast90PercentRetained": kept >= 0.90 * len(correct)}
    details = {"originalCorrect": len(correct), "keptOriginalCorrect": kept,
               "historicalAccuracyDifference": c["accuracy"] - o["accuracy"]}
    for split in ("legacyText", "novelText"):
        checks[split + "BpbIncreaseAtMost5Percent"] = candidate[split]["bpb"] <= original[split]["bpb"] * 1.05
        limit = 4 if split == "legacyText" else len(original[split]["continuations"])
        old = [original[split]["continuations"][i]["tokens"] for i in range(limit)]
        new = [candidate[split]["continuations"][i]["tokens"] for i in range(limit)]
        checks[split + "MedianLengthAtLeast90Percent"] = float(np.median(new)) >= 0.90 * float(np.median(old))
        checks[split + "EarlyEosIncreaseAtMostOne"] = sum(x <= 2 for x in new) <= sum(x <= 2 for x in old) + 1
        if split == "legacyText":
            checks["originalLongHistoricalProbesPreserved"] = all(b == 32 for a, b in zip(old, new) if a == 32)
        details[split] = {"bpbRelativeChange": candidate[split]["bpb"] / original[split]["bpb"] - 1,
                          "originalLengths": old, "candidateLengths": new}
    for split in ("semantic", "legacyRetention", "novelRetention"):
        checks[split + "PrematureEosIncreaseAtMost5pp"] = candidate[split]["prematureEos"] / candidate[split]["total"] <= original[split]["prematureEos"] / original[split]["total"] + 0.05
    return {"checks": checks, "details": details, "passed": all(checks.values())}


def verdict(models, controls, metadata, legacy_rows):
    require(set(("original", "old_answer", "old_replay", "old_teacher")) <= set(models), "missing historical comparison")
    shortcut_compromised = any(c["accuracy"] >= 0.90 for c in controls.values())
    result = {"holdoutShortcutCompromised": shortcut_compromised,
              "bootstrap": {"draws": DRAWS, "seed": SEED, "unit": "contrast_pair", "confidence": 0.975},
              "candidates": {}}
    comparisons = {name: result["semantic"] for name, result in models.items() if name not in ("A", "B")}
    comparisons.update({"control_" + name: metric for name, metric in controls.items()})
    for name in ("A", "B"):
        if name not in models:
            result["candidates"][name] = {"status": "NOT_RUN", "promotion": False}
            continue
        semantic = models[name]["semantic"]
        effects = {key: effect(semantic, baseline) for key, baseline in comparisons.items()}
        retention = retention_gates(models[name], models["original"], legacy_rows)
        absolute = {"restrictedAtLeast60Percent": semantic["accuracy"] >= 0.60,
                    "completePairsAtLeast40Percent": semantic["pairAccuracy"] >= 0.40,
                    "noFamilyLossGreaterThan25pp": all(value >= models["original"]["semantic"]["familyAccuracy"][family] - 0.25 for family, value in semantic["familyAccuracy"].items()),
                    "notShortcutCompromised": not shortcut_compromised}
        relative = all(metric["gainAtLeast10pp"] and metric["pairedLowerAboveZero"] for comparison in effects.values() for metric in comparison.values())
        passed = all(absolute.values()) and relative and retention["passed"]
        result["candidates"][name] = {"status": "RESEARCH_GATE_PASS" if passed else "REJECT_FOR_PROMOTION",
                                     "absoluteChecks": absolute, "allRelativeChecksPass": relative,
                                     "comparisons": effects, "retention": retention, "promotion": False}
    return result
