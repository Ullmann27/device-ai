#!/usr/bin/env python3
"""Check public G1 fixtures using the unchanged, hash-pinned PR573 scorer."""
import argparse
from collections import Counter, defaultdict
from copy import deepcopy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


SCORER_SHA256 = "51ea7af14bb65d12ba7c4c385152bb8856c23e3c0af48fca1e8e90f740840722"
DIMENSIONS = {
    "memory", "generalization", "causal_reasoning", "metacognition",
    "long_term_planning", "continual_learning",
}


class PackContract(unittest.TestCase):
    suite = None
    scorer = None

    def answers(self):
        return [{"id": c["id"], "answer": deepcopy(c["expected"])}
                for c in self.suite["cases"]]

    def grade(self, responses):
        return self.scorer.grade(self.suite, responses, "synthetic_selftest", "NONE", "NONE")

    def test_structure_and_public_status(self):
        self.scorer.validate_suite(self.suite)
        self.assertEqual(self.suite["split"], "development")
        cases = self.suite["cases"]
        self.assertEqual(len(cases), 24)
        self.assertEqual(Counter(c["dimension"] for c in cases),
                         Counter({d: 4 for d in DIMENSIONS}))
        self.assertEqual(len({c["prompt"] for c in cases}), 24)
        for case in cases:
            self.assertTrue(case["id"].startswith("G1-"))
            self.assertTrue(case["rationale"] and case["limit"] and case["prompt"])
            self.assertTrue(case["expected"])
            self.assertTrue(all(type(v) in (str, bool, int, float)
                                for v in case["expected"].values()))

    def test_contrastive_pairs(self):
        pairs = defaultdict(list)
        for case in self.suite["cases"]:
            pairs[case["pair_id"]].append(case)
        self.assertEqual(len(pairs), 12)
        for pair in pairs.values():
            self.assertEqual(len(pair), 2)
            self.assertEqual(pair[0]["dimension"], pair[1]["dimension"])
            self.assertEqual(set(pair[0]["expected"]), set(pair[1]["expected"]))
            self.assertNotEqual(pair[0]["expected"], pair[1]["expected"])

    def test_prompt_only_boundary(self):
        exported = self.scorer.export_prompts(self.suite)
        self.assertEqual(len(exported["cases"]), 24)
        for case, original in zip(exported["cases"], self.suite["cases"]):
            self.assertEqual(set(case), {"id", "domain", "prompt"})
            self.assertEqual(case["prompt"], original["prompt"])
        self.assertEqual(exported["split"], "development")

    def test_synthetic_answers_are_not_model_evidence(self):
        report = self.grade(self.answers())
        self.assertEqual(report["totals"]["pass"], 24)
        self.assertEqual(report["totals"]["fail"], 0)
        self.assertEqual(report["provenance"], "synthetic_selftest")
        self.assertFalse(report["inference_execution_verified"])
        self.assertEqual(self.scorer.compare_scores(report, report)["status"], "NOT_COMPARABLE")

    def test_missing_and_incorrect_answers(self):
        missing = self.grade([])
        self.assertEqual(missing["totals"]["not_run"], 24)
        self.assertIsNone(missing["totals"]["accuracy_attempted_pct"])
        wrong = self.grade([{"id": c["id"], "answer": {}} for c in self.suite["cases"]])
        self.assertEqual(wrong["totals"]["fail"], 24)
        swapped = []
        pairs = defaultdict(list)
        for c in self.suite["cases"]:
            pairs[c["pair_id"]].append(c)
        for a, b in pairs.values():
            swapped.extend([{"id": a["id"], "answer": b["expected"]},
                            {"id": b["id"], "answer": a["expected"]}])
        self.assertEqual(self.grade(swapped)["totals"]["fail"], 24)

    def test_each_field_is_required_and_type_exact(self):
        for case in self.suite["cases"]:
            for key, value in case["expected"].items():
                with self.subTest(case=case["id"], field=key):
                    mutated = dict(case["expected"])
                    mutated[key] = None
                    result = self.grade([{"id": case["id"], "answer": mutated}])
                    self.assertEqual(result["totals"]["fail"], 1)
                    del mutated[key]
                    result = self.grade([{"id": case["id"], "answer": mutated}])
                    self.assertEqual(result["totals"]["fail"], 1)
                    if type(value) is bool:
                        mutated[key] = int(value)
                        result = self.grade([{"id": case["id"], "answer": mutated}])
                        self.assertEqual(result["totals"]["fail"], 1)

    def test_duplicate_unknown_and_corrupted_input(self):
        answer = self.answers()[0]
        with self.assertRaises(ValueError):
            self.grade([answer, answer])
        with self.assertRaises(ValueError):
            self.grade([{"id": "UNKNOWN", "answer": {}}])
        corrupted = deepcopy(self.suite)
        corrupted["cases"][0]["prompt"] += " changed"
        with self.assertRaises(ValueError):
            self.scorer.validate_suite(corrupted)

    def test_formal_labels_by_enumeration(self):
        expected = {c["id"]: c["expected"] for c in self.suite["cases"]}
        start = "ABCD"
        reverse = start[::-1]
        rotate = start[1:] + start[:1]
        self.assertEqual(expected["G1-GEN-02A"]["result"], reverse[1:] + reverse[:1])
        self.assertEqual(expected["G1-GEN-02B"]["result"], rotate[::-1])
        observed = [u for u in (0, 1) if u == 0]
        intervened = [u for u in (0, 1)]
        self.assertEqual(expected["G1-CAU-01A"]["probability"], sum(observed) / len(observed))
        self.assertEqual(expected["G1-CAU-01B"]["probability"], sum(intervened) / len(intervened))
        model_predictions = {"M1": 0, "M2": 1}
        compatible = {m: y for m, y in model_predictions.items() if y == 0}
        self.assertEqual(set(compatible.values()), {int(expected["G1-CAU-02A"]["result"])})
        self.assertEqual(len(set(model_predictions.values())), 2)
        self.assertEqual(expected["G1-CAU-02B"], {"result": "ABSTAIN", "identified": False})
        edges = {"S": [("A", 2), ("C", 1)], "A": [("B", 2)], "B": [("G", 2)],
                 "C": [("D", 1)], "D": [("E", 1)], "E": [("G", 4)]}
        paths = []
        pending = [(["S"], 0)]
        while pending:
            path, cost = pending.pop()
            if path[-1] == "G":
                paths.append((cost, ">".join(path)))
            for target, step in edges.get(path[-1], []):
                pending.append((path + [target], cost + step))
        for suffix, budget in (("A", 6), ("B", 5)):
            feasible = sorted(p for p in paths if p[0] <= budget)
            answer = ({"cost": feasible[0][0], "plan": feasible[0][1]} if feasible
                      else {"cost": -1, "plan": "ABSTAIN"})
            self.assertEqual(expected["G1-PLAN-01" + suffix], answer)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scorer", required=True, type=Path,
                        help="Unmodified intelligence.py from pinned PR573; no download is performed")
    parser.add_argument("--output", type=Path, help="New artifact-contract report; never model evidence")
    args = parser.parse_args()
    if args.output and args.output.exists():
        parser.error("Refusing to overwrite an existing report")
    source = args.scorer.read_bytes()
    if hashlib.sha256(source).hexdigest() != SCORER_SHA256:
        parser.error("Scorer SHA-256 mismatch; refusing to execute an unpinned evaluator")
    spec = importlib.util.spec_from_file_location("genesis_existing_quality_scorer", args.scorer)
    if spec is None or spec.loader is None:
        parser.error("Scorer must be an importable Python source file")
    scorer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(scorer)
    data = Path(__file__).with_name("challenges.json")
    PackContract.suite = json.loads(data.read_text(encoding="utf-8"))
    PackContract.scorer = scorer
    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(PackContract))
    report = {
        "status": "PASS" if result.wasSuccessful() else "FAIL",
        "evidence_kind": "artifact_contract_only",
        "model_run": "NOT_RUN",
        "device_run": "NOT_RUN",
        "blind_holdout": False,
        "tests": result.testsRun,
        "failures": len(result.failures),
        "errors": len(result.errors),
        "skipped": len(result.skipped),
        "suite_id": PackContract.suite["suite_id"],
        "data_sha256": hashlib.sha256(data.read_bytes()).hexdigest(),
        "checker_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "scorer_sha256": SCORER_SHA256,
        "python": sys.version.split()[0],
        "limits": "Public DEV, synthetic answers; not an intelligence or independent holdout result.",
    }
    if args.output:
        with args.output.open("x", encoding="utf-8") as stream:
            json.dump(report, stream, indent=2)
            stream.write("\n")
    print(json.dumps(report, sort_keys=True))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
