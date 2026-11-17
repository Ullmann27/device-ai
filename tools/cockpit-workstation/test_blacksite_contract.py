"""Mutate the actual generated asset."""
import copy
import unittest
from verify_unified_assets import Glb, ASSETS, BLACKSITE_BATCHES, blacksite_contract


class BlacksiteContractTest(unittest.TestCase):
    def setUp(self):
        self.glb = Glb(ASSETS / "cinema3d/cockpit-workstation.glb")
        self.glb.doc = copy.deepcopy(self.glb.doc)

    def test_generated_batches_pass(self):
        report = blacksite_contract(self.glb, required=True)
        self.assertEqual("PASS", report["state"])
        self.assertEqual(5, report["material_batches"])

    def test_missing_room_is_rejected(self):
        _, room = self.glb.node("cockpit-room")
        room["extras"].pop("blacksite_schema")
        with self.assertRaises(AssertionError):
            blacksite_contract(self.glb, required=True)

    def test_side_parent_is_rejected(self):
        index, _ = self.glb.node(next(iter(BLACKSITE_BATCHES)))
        side, _ = self.glb.node("cockpit-side-monitors")
        self.glb.parent[index] = side
        with self.assertRaises(AssertionError):
            blacksite_contract(self.glb, required=True)

    def test_front_occluder_is_rejected(self):
        _, node = self.glb.node("bunker-concrete-shell")
        if "matrix" in node:
            node["matrix"][14] += 3.
        else:
            translation = node.setdefault("translation", [0., 0., 0.])
            translation[2] += 3.
        with self.assertRaises(AssertionError):
            blacksite_contract(self.glb, required=True)

    def test_unbatched_primitives_are_rejected(self):
        _, node = self.glb.node("bunker-titanium-structure")
        primitives = self.glb.doc["meshes"][node["mesh"]]["primitives"]
        primitives.append(copy.deepcopy(primitives[0]))
        with self.assertRaises(AssertionError):
            blacksite_contract(self.glb, required=True)

    def test_transparency_cannot_hide_geometry_failures(self):
        _, node = self.glb.node("bunker-concrete-shell")
        primitive = self.glb.doc["meshes"][node["mesh"]]["primitives"][0]
        self.glb.doc["materials"][primitive["material"]]["alphaMode"] = "BLEND"
        with self.assertRaises(AssertionError):
            blacksite_contract(self.glb, required=True)


if __name__ == "__main__":
    unittest.main()
