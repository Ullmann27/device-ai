"""Behavior/numerics contracts for the isolated CDXA1 research model."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

import numpy as np

AVAILABLE = importlib.util.find_spec("cdxa1_pointer") is not None
if AVAILABLE:
    from cdxa1_pointer import Input, PointerMLP, target_pointers


class AvailabilityTest(unittest.TestCase):
    def test_neural_pointer_implementation_exists(self):
        self.assertTrue(AVAILABLE, "CDXA1 neural pointer implementation is missing")


@unittest.skipUnless(AVAILABLE, "implementation absent before RED")
class NeuralPointerTest(unittest.TestCase):
    def test_tokenization_preserves_unicode_source_and_offsets(self):
        x = Input("Ömer trägt einen Hut.  Zoë lebt in Łódź!", "Was trägt Ömer?")
        self.assertEqual("".join(t.text for t in x.tokens), x.evidence)
        for t in x.tokens:
            self.assertEqual(x.evidence[t.start:t.end], t.text)
        self.assertEqual(len(x.sentences), 2)
        self.assertEqual(target_pointers(x, "Ömer trägt einen Hut."), [0, 1, 2, 3, x.eos])
        self.assertEqual(target_pointers(x, "UNBEKANNT"), [x.unknown, x.eos])
        with self.assertRaises(ValueError):
            target_pointers(x, "Ömer wohnt in Wien.")

    def test_derivatives_include_pointer_unknown_and_eos_without_mutation(self):
        x = Input("Ada wohnt in Graz. Bea trägt einen Hut.", "Wo wohnt Ada?")
        m = PointerMLP(seed=91, hidden=16)
        original = m.parameters().copy()
        for previous, target in [(None, 0), (3, x.eos), (None, x.unknown), (x.unknown, x.eos)]:
            features = x.features(previous)
            loss, grad = m.loss_gradient(features, target)
            self.assertTrue(np.isfinite(loss))
            sample = np.argsort(np.abs(grad))[-12:]
            for i in sample:
                plus = original.copy(); plus[i] += 1e-5
                minus = original.copy(); minus[i] -= 1e-5
                m.load_parameters(plus); lp = m.loss_gradient(features, target)[0]
                m.load_parameters(minus); lm = m.loss_gradient(features, target)[0]
                self.assertAlmostEqual(grad[i], (lp-lm)/2e-5, delta=1e-7)
            m.load_parameters(original)
            self.assertTrue(np.array_equal(m.parameters(), original))

    def test_no_forced_copy_transition_or_hardcoded_eos(self):
        x = Input("Ada wohnt in Graz.", "Wo wohnt Ada?")
        m = PointerMLP(seed=91, hidden=16)
        m.load_parameters(np.zeros_like(m.parameters()))
        g = m.generate(x, max_tokens=5)
        self.assertEqual(g["pointers"], [0]*5)
        self.assertEqual(g["text"], "Ada Ada Ada Ada Ada")
        self.assertFalse(g["ended"])

    def test_actual_gradient_step_reduces_the_same_loss(self):
        x = Input("Ada wohnt in Graz.", "Wo wohnt Ada?")
        m = PointerMLP(seed=91, hidden=16)
        features = x.features(None)
        before, gradient = m.loss_gradient(features, 0)
        m.load_parameters(m.parameters()-.01*gradient)
        self.assertLess(m.loss_gradient(features, 0)[0], before)

    def test_serialization_rejects_nonfinite_or_mismatched_shapes(self):
        m = PointerMLP(seed=91, hidden=16)
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory)/"new-model.json"
            m.save(p, {"dataSha256":"a"*64})
            restored = PointerMLP.restore(p)
            self.assertTrue(np.array_equal(m.parameters(), restored.parameters()))
            x = Input("Ada lebt in Graz.", "Wo lebt Ada?")
            before = restored.parameters().copy(); restored.generate(x)
            self.assertTrue(np.array_equal(before, restored.parameters()))
            with self.assertRaises(FileExistsError): m.save(p, {})
            payload = json.loads(p.read_text()); payload["parameters"][0] = float("nan")
            p.write_text(json.dumps(payload))
            with self.assertRaises(ValueError): PointerMLP.restore(p)
        with self.assertRaises(ValueError): m.load_parameters(np.ones(3))

    def test_limits_reject_without_truncating(self):
        for evidence, question in [("", "Wo?"), ("A\nB", "Wo?"), ("x"*1025, "Wo?"), ("Ada.", "x"*257)]:
            with self.assertRaises(ValueError): Input(evidence, question)
        with self.assertRaises(ValueError):
            Input("Ada.", "Wo?").features(10000)


if __name__ == "__main__":
    unittest.main()
