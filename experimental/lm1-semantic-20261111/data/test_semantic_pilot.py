import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import make_semantic_pilot as data
from frozen_tokenizer import Tokenizer, load_tokenizer
from audit_semantic_pilot import action_rule, empirical_ceiling, metrics

REPO = Path(__file__).resolve().parents[3]


class SemanticPilotChecks(unittest.TestCase):
    def test_reproducibility_balance_and_disjointness(self):
        first, rows, manifest = data.build(REPO)
        self.assertEqual((first, rows, manifest), data.build(REPO))
        self.assertEqual(len(rows["train"]), 192)
        self.assertEqual(len(rows["dev"]), 96)
        self.assertFalse(set(data.FAMILIES["train"]) & set(data.FAMILIES["dev"]))
        self.assertFalse({r["prompt"] for r in rows["train"]} & {r["prompt"] for r in rows["dev"]})

    def test_opposing_pairs_defeat_bag_or_length_oracles(self):
        _, rows, _ = data.build(REPO)
        for split in rows.values():
            bag_max = empirical_ceiling(split, lambda r: tuple(sorted(data.bag(r["prompt"]).items())))
            length_max = empirical_ceiling(split, lambda r: r["prefixTokens"])
            self.assertLessEqual(bag_max["maxCorrect"], len(split) // 2)
            self.assertLessEqual(length_max["maxCorrect"], len(split) // 2)

    def test_wrong_tokenizer_rejected(self):
        with self.assertRaisesRegex(ValueError, "identity"):
            Tokenizer(b"not the frozen tokenizer")

    def test_shared_objects_rejected(self):
        objects = copy.deepcopy(data.OBJECTS)
        objects["dev"] = objects["train"][:2]
        with patch.object(data, "OBJECTS", objects), self.assertRaisesRegex(ValueError, "shared object"):
            data.build(REPO)

    def test_shared_families_rejected(self):
        families = copy.deepcopy(data.FAMILIES)
        families["dev"] = families["train"]
        with patch.object(data, "FAMILIES", families), self.assertRaisesRegex(ValueError, "shared family"):
            data.build(REPO)

    def test_refuse_existing_destination(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "pilot"
            data.write_dataset(REPO, dest)
            before = {p.name: p.read_bytes() for p in dest.iterdir()}
            with self.assertRaises(FileExistsError):
                data.write_dataset(REPO, dest)
            self.assertEqual(before, {p.name: p.read_bytes() for p in dest.iterdir()})

    def test_one_class_collapse_is_not_pair_success(self):
        rows = [{"id": "a", "label": "0"}, {"id": "b", "label": "3"}]
        meta = [{"id": "a", "pairId": "p", "familyId": "f"}, {"id": "b", "pairId": "p", "familyId": "f"}]
        report = metrics(rows, meta, ["0", "0"])
        self.assertEqual(report["correct"], 1)
        self.assertEqual(report["pairsBothCorrect"], 0)

    def test_action_order_controls(self):
        example = "nichts tun; die Datei öffnen; nach Koralle suchen; etwas speichern"
        self.assertEqual(action_rule(example), "0")
        self.assertEqual(action_rule(example, True), "3")


if __name__ == "__main__":
    unittest.main()
