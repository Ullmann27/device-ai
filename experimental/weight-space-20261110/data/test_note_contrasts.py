import copy
import json
from pathlib import Path
import tempfile
import unittest

import make_note_contrasts as data


class NoteContrastChecks(unittest.TestCase):
    def test_reproducibility_and_pairs(self):
        first, manifest = data.build()
        self.assertEqual((first, manifest), data.build())
        self.assertNotIn("test.tsv", first)
        metadata = json.loads(first["row-metadata.json"])
        for split, expected in (("train", 128), ("dev", 64)):
            self.assertEqual(expected, len(metadata[split]))
            pairs = {}
            for row in metadata[split]:
                pairs.setdefault(row["pairId"], []).append(row)
            for pair in pairs.values():
                self.assertEqual({"0", "3"}, {row["label"] for row in pair})
                self.assertEqual(1, len({row["promptUtf8Bytes"] for row in pair}))
            for label in ("0", "3"):
                rows = [row for row in metadata[split] if row["label"] == label]
                self.assertEqual(expected//4, sum(row["directivePosition"] == "first" for row in rows))
                self.assertEqual(expected//4, sum(row["lengthLevel"] == "short" for row in rows))

    def test_reject_shared_template(self):
        families = copy.deepcopy(data.FAMILIES)
        families["dev"] = (families["train"][0],)+families["dev"][1:]
        with self.assertRaisesRegex(ValueError, "non-disjoint directive"):
            data.build(families=families)

    def test_reject_shared_content(self):
        objects = copy.deepcopy(data.OBJECTS)
        objects["dev"] = (objects["train"][0],)+objects["dev"][1:]
        with self.assertRaisesRegex(ValueError, "non-disjoint object"):
            data.build(objects=objects)

    def test_reject_invalid_semantic_pair(self):
        families = copy.deepcopy(data.FAMILIES)
        families["train"] = (("remember", "Bitte merken", "Jetzt merken"),)+families["train"][1:]
        with self.assertRaisesRegex(ValueError, "invalid positive/negative"):
            data.build(families=families)

    def test_reject_multiline_content(self):
        objects = copy.deepcopy(data.OBJECTS)
        old = objects["train"][0]
        objects["train"] = ((old[0], old[1]+"\n", old[2]),)+objects["train"][1:]
        with self.assertRaisesRegex(ValueError, "invalid prompt shape"):
            data.build(objects=objects)

    def test_never_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)/"new"
            data.write_dataset(output)
            before = {path.name: path.read_bytes() for path in output.iterdir()}
            with self.assertRaises(FileExistsError):
                data.write_dataset(output)
            self.assertEqual(before, {path.name: path.read_bytes() for path in output.iterdir()})


if __name__ == "__main__":
    unittest.main()
