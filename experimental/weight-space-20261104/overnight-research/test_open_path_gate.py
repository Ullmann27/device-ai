import copy
import importlib.util
import math
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("open_path_contract", Path(__file__).with_name("run_open_path.py"))
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


def fixture():
    probes = RUN.C.probe_bytes()[0]
    reg = {"probesSha256": RUN.C.sha(RUN.C.probe_bytes()[1])}
    rows = [{"kind": "START", "optimizerConstructed": "false", "parameters": 9712128,
             "initialParameterIdentity": RUN.INITIAL_IDENTITY, "wideCheckpointSha256": RUN.C.MODELS["W0"],
             "smallCheckpointSha256": RUN.C.MODELS["S0"], "tokenizerSha256": RUN.C.TOKENIZER,
             "probesSha256": reg["probesSha256"]}]
    for p in probes:
        rows.append({"kind": "BASE_PARITY", "probe": p["id"], "pass": "true", "deepWideBitwiseParity": "true", "maximumProbabilityDelta": 0.})
    rows.append({"kind": "OPENED", "changedCoordinates": 2560, "woDiagonal": .0001,
                 "w2MappedEntry": .000025, "openedParameterIdentity": "a" * 64})
    for p in probes:
        rows.append({"kind": "OPEN_PARITY", "probe": p["id"], "pass": "true", "maximumProbabilityDelta": .000001})
    for p in probes[:4]:
        for tensor, count in RUN.G.tensor_counts("D0").items():
            rows.append({"kind": "GRADIENT", "probe": p["id"], "tensor": tensor, "parameters": count,
                         "nonzeroGradients": count, "allFinite": "true", "minimumNonzeroAbs": 1.,
                         "maximumAbs": 1., "gradientL2": math.sqrt(count)})
        rows.append({"kind": "LOSS", "probe": p["id"], "allParametersReached": "true",
                     "nonzeroGradientCoordinates": 9712128, "targetCount": 20, "meanLoss": 2.})
    rows.append({"kind": "RESTORED", "pass": "true", "initialParameterIdentity": RUN.INITIAL_IDENTITY,
                 "finalParameterIdentity": RUN.INITIAL_IDENTITY, "completedLossProbes": 4})
    rows.append({"kind": "DONE", "lossProbes": 4, "inputsUnchanged": "true", "optimizerUpdates": 0,
                 "serializedCheckpoints": 0, "modelPromotion": "false"})
    return reg, rows


def raw(rows):
    return ("\n".join(r["kind"] + "".join("\t" + k + "=" + str(v) for k, v in r.items() if k != "kind") for r in rows) + "\n").encode()


class OpenPathGateTest(unittest.TestCase):
    def test_complete_positive_contract(self):
        reg, rows = fixture()
        result = RUN.validate(raw(rows), reg)
        self.assertEqual(len(result["cases"]), 4)
        self.assertTrue(all(c["newlyReachedCoordinates"] == 1838592 for c in result["cases"]))

    def test_failures_cannot_be_pass_flags(self):
        mutations = (("START", "optimizerConstructed", "true"), ("START", "parameters", 10000000),
                     ("BASE_PARITY", "maximumProbabilityDelta", .001), ("BASE_PARITY", "deepWideBitwiseParity", "false"),
                     ("OPENED", "changedCoordinates", 2561), ("OPENED", "woDiagonal", .001),
                     ("OPENED", "openedParameterIdentity", RUN.INITIAL_IDENTITY),
                     ("OPEN_PARITY", "maximumProbabilityDelta", .001), ("OPEN_PARITY", "maximumProbabilityDelta", "nan"),
                     ("GRADIENT", "nonzeroGradients", 0), ("GRADIENT", "allFinite", "false"),
                     ("GRADIENT", "minimumNonzeroAbs", 0.), ("GRADIENT", "gradientL2", "inf"),
                     ("LOSS", "nonzeroGradientCoordinates", 7873536), ("LOSS", "allParametersReached", "false"),
                     ("RESTORED", "finalParameterIdentity", "0" * 64), ("RESTORED", "pass", "false"),
                     ("DONE", "inputsUnchanged", "false"), ("DONE", "optimizerUpdates", 1),
                     ("DONE", "serializedCheckpoints", 1), ("DONE", "modelPromotion", "true"))
        reg, rows = fixture()
        for kind, key, value in mutations:
            changed = copy.deepcopy(rows)
            next(r for r in changed if r["kind"] == kind)[key] = value
            with self.subTest(kind=kind, key=key), self.assertRaises(ValueError):
                RUN.validate(raw(changed), reg)

    def test_missing_or_unordered_output(self):
        reg, rows = fixture()
        for changed in (rows[:-1], rows + [rows[-1]], [rows[1], rows[0], *rows[2:]]):
            with self.assertRaises(ValueError):
                RUN.validate(raw(changed), reg)

    def test_bad_registration_launches_nothing(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "registration.json"
            path.write_text("{}")
            with patch.object(RUN.C, "launch", side_effect=AssertionError("unexpected launch")) as launch:
                with self.assertRaisesRegex(ValueError, "registration digest"):
                    RUN.execute(path, "0" * 64, {}, Path(temporary) / "run")
            launch.assert_not_called()
            self.assertFalse((Path(temporary) / "run").exists())


if __name__ == "__main__":
    unittest.main()
