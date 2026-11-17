#!/usr/bin/env python3
"""Post-inspection supplemental adversarial cases; no production-source edits."""
import copy
import importlib.util
import os
from pathlib import Path
import struct
import unittest
import validate_hinge_v2 as v2


class HingeV2Challenge(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.legacy_path = Path(os.environ["HINGE_LEGACY"])
        cls.legacy = v2.load_legacy(cls.legacy_path)
        cls.original = Path(os.environ["HINGE_ORIGINAL"]).read_bytes()
        cls.candidate = Path(os.environ["HINGE_CANDIDATE"]).read_bytes()
        cls.g = cls.legacy.Glb(cls.candidate)
        cls.jaw = cls.g.names.index("Jaw")

    def changed(self, change):
        doc = copy.deepcopy(self.g.doc)
        change(doc)
        return v2.encode(doc, self.g.binary)

    def rebase(self, axis, amount):
        def change(doc):
            doc["nodes"][self.jaw]["translation"][axis] += amount
            for child in doc["nodes"][self.jaw]["children"]:
                doc["nodes"][child]["translation"][axis] -= amount
        return self.changed(change)

    def reject(self, data, original=None):
        with self.assertRaises((ValueError, KeyError, IndexError, TypeError, struct.error)):
            v2.validate(self.original if original is None else original, data, self.legacy)

    def mutate_morph(self, name, upper):
        face = self.g.doc["nodes"][self.g.names.index("Face")]
        mesh = self.g.doc["meshes"][face["mesh"]]
        primitive = mesh["primitives"][0]
        points = self.g.accessor(primitive["attributes"]["POSITION"])
        world = self.g.world(self.g.names.index("Face"))
        vertex = next(i for i, p in enumerate(points)
                      if (self.legacy.transform(world, p)[1] > .32 if upper
                          else self.legacy.transform(world, p)[1] < .20))
        accessor = self.g.doc["accessors"][primitive["targets"][mesh["extras"]["targetNames"].index(name)]["POSITION"]]
        view = self.g.doc["bufferViews"][accessor["bufferView"]]
        at = view.get("byteOffset", 0) + accessor.get("byteOffset", 0) + vertex*view.get("byteStride", 12)
        binary = bytearray(self.g.binary)
        struct.pack_into("<f", binary, at+4, .0002)
        return v2.encode(self.g.doc, binary)

    def test_01_actual_candidate_preserves_fixed_region(self):
        self.assertEqual("PASS_SEPARATE_HINGE_AND_FIXED_REGION_V2",
                         v2.validate(self.original, self.candidate, self.legacy)["status"])

    def test_02_original_hinge_does_not_get_free_v2_pass(self):
        self.reject(self.original)

    def test_03_low_hinge_with_preserved_neutral_is_rejected(self):
        self.reject(self.rebase(1, -.04))

    def test_04_above_eye_hinge_with_preserved_neutral_is_rejected(self):
        self.reject(self.rebase(1, .04))

    def test_05_front_hinge_with_preserved_neutral_is_rejected(self):
        self.reject(self.rebase(2, .15))

    def test_06_lateral_hinge_with_preserved_neutral_is_rejected(self):
        self.reject(self.rebase(0, .01))

    def test_07_missing_child_compensation_is_rejected(self):
        self.reject(self.changed(lambda d: d["nodes"][self.jaw]["translation"].__setitem__(1, .06)))

    def test_08_translated_tooth_is_rejected(self):
        tooth = next(i for i, n in enumerate(self.g.names) if n.startswith("Tooth"))
        self.reject(self.changed(lambda d: d["nodes"][tooth]["translation"].__setitem__(0, .05)))

    def test_09_changed_parent_is_rejected(self):
        def change(d):
            d["nodes"][self.g.parents[self.jaw]]["children"].remove(self.jaw)
            d["nodes"][self.g.names.index("Neck")]["children"].append(self.jaw)
        self.reject(self.changed(change))

    def test_10_nonfinite_hinge_is_rejected(self):
        self.reject(self.changed(lambda d: d["nodes"][self.jaw]["translation"].__setitem__(1, float("nan"))))

    def test_11_skinned_jaw_dependency_is_rejected(self):
        self.reject(self.changed(lambda d: d["skins"][0]["joints"].append(self.jaw)))

    def test_12_changed_eye_order_is_rejected(self):
        eye = self.g.names.index("EyeLeft")
        self.reject(self.changed(lambda d: d["nodes"][eye]["translation"].__setitem__(0, -.04)))

    def test_13_upper_face_mouth_deformation_is_rejected(self):
        self.reject(self.mutate_morph("AA", True))

    def test_14_lower_face_blink_deformation_is_rejected(self):
        self.reject(self.mutate_morph("BlinkLeft", False))

    def test_15_original_landmark_cannot_be_reauthored(self):
        changed = bytearray(self.original)
        changed[-1] ^= 1
        self.reject(self.candidate, bytes(changed))

    def test_16_original_bad_control_family_remains_rejected(self):
        path = self.legacy_path.with_name("test_validate_glb.py")
        spec = importlib.util.spec_from_file_location("old_fixture", path)
        module = importlib.util.module_from_spec(spec)
        import sys
        sys.modules["validate_glb"] = self.legacy
        spec.loader.exec_module(module)
        for mutation in [lambda d: d["nodes"][5].update(translation=[0, -9, 0]),
                         lambda d: d["nodes"][3].update(translation=[.03, 2, .03]),
                         lambda d: d["nodes"][4].update(translation=[-.03, .3, 9]),
                         lambda d: d["nodes"][0].update(scale=[1, -1, 1]),
                         lambda d: d["nodes"][0].update(scale=[100, 100, 100]),
                         lambda d: d["nodes"][2].update(rotation=[]),
                         lambda d: d["nodes"][5].update(translation=[0, .22, 9])]:
            with self.subTest(mutation=mutation):
                self.assertEqual("FAIL", self.legacy.validate(module.fixture(mutation))["status"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
