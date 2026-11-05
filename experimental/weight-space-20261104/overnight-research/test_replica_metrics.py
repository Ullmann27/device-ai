import importlib.util
from pathlib import Path
import unittest

import numpy as np

PATH = Path(__file__).with_name("audit_replicas.py")
SPEC = importlib.util.spec_from_file_location("replica_audit", PATH)
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class ReplicaMetricsTest(unittest.TestCase):
    def test_exact_replicas(self):
        values = np.tile(np.array([[1., -2.], [3., 4.]], dtype=np.float32), (1, 8))
        result = AUDIT.replica_metrics(values, 2)
        self.assertEqual(result["exactlyTiedGroups"], 4)
        self.assertEqual(result["spreadRms"], 0)

    def test_independent_hand_calculation(self):
        result = AUDIT.replica_metrics(np.array([[1., 3., 5., 7.]]), 1)
        self.assertAlmostEqual(result["spreadRms"], np.sqrt(5))
        self.assertAlmostEqual(result["valueRms"], np.sqrt(21))
        self.assertEqual(result["maximumReplicaRange"], 6)
        self.assertEqual(result["exactlyTiedGroups"], 0)

    def test_zero_reference_is_defined(self):
        result = AUDIT.replica_metrics(np.zeros((2, 16)), 2)
        self.assertEqual(result["relativeSpreadRms"], 0)

    def test_single_replica(self):
        result = AUDIT.replica_metrics(np.array([[1., 3.]]), 2)
        self.assertEqual(result["replicas"], 1)
        self.assertEqual(result["spreadRms"], 0)

    def test_signed_zero_is_bit_distinct(self):
        result = AUDIT.replica_metrics(np.array([[0., -0.]], dtype=np.float32), 1)
        self.assertEqual(result["exactlyTiedGroups"], 0)
        self.assertEqual(result["spreadRms"], 0)

    def test_invalid_shape(self):
        for values, width in ((np.ones((2, 3)), 2), (np.ones(4), 2), (np.ones((2, 4)), 0)):
            with self.subTest(shape=values.shape, width=width), self.assertRaises(ValueError):
                AUDIT.replica_metrics(values, width)

    def test_nonfinite_rejected(self):
        for value in (np.nan, np.inf, -np.inf):
            with self.subTest(value=value), self.assertRaises(ValueError):
                AUDIT.replica_metrics(np.array([[0., value]]), 1)

    def test_paired_noise_cancels_on_equal_inputs(self):
        base = np.array([[1., 2.], [3., 4.]])
        wide = np.tile(base / 2, (2, 2))
        noise = np.array([[.125, .25, .5, 1.], [.25, .5, 1., 2.]])
        wide[:2] += noise
        wide[2:] -= noise
        self.assertGreater(AUDIT.replica_metrics(wide, 2)["spreadRms"], 0)
        collapsed = AUDIT.collapse_input_replicas(wide, 2, 2)
        np.testing.assert_array_equal(collapsed, np.tile(base, (1, 2)))
        self.assertEqual(AUDIT.replica_metrics(collapsed, 2)["spreadRms"], 0)

    def test_asymmetric_update_breaks_cancellation(self):
        wide = np.ones((4, 4))
        wide[0, 2] += .5
        collapsed = AUDIT.collapse_input_replicas(wide, 2, 2)
        self.assertEqual(collapsed[0, 2], 2.5)
        self.assertGreater(AUDIT.replica_metrics(collapsed, 2)["spreadRms"], 0)

    def test_collapse_rejects_wrong_replication(self):
        with self.assertRaises(ValueError):
            AUDIT.collapse_input_replicas(np.zeros((6, 8)), 2, 2)


if __name__ == "__main__":
    unittest.main()
