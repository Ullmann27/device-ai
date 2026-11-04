import copy
import importlib.util
import math
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("gradient_controller", Path(__file__).with_name("run_gradient_access.py"))
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


def fixture(profile):
    probes = RUN.CORE.probe_bytes()[0]
    reg = {"probesSha256": RUN.CORE.sha(RUN.CORE.probe_bytes()[1])}
    counts = RUN.tensor_counts(profile)
    rows = [{"kind": "START", "profile": profile, "optimizerConstructed": "false",
             "parameters": RUN.COUNTS[profile], "parameterBytes": RUN.COUNTS[profile] * 4,
             "smallCheckpointSha256": RUN.CORE.MODELS["S0"], "wideCheckpointSha256": RUN.CORE.MODELS["W0"],
             "tokenizerSha256": RUN.CORE.TOKENIZER, "probesSha256": reg["probesSha256"],
             "initialParameterIdentity": "a" * 64}]
    for probe in probes:
        rows.append({"kind": "INITIAL_PARITY", "profile": profile, "probe": probe["id"],
                     "pass": "true", "deepWideBitwiseParity": "true", "maximumProbabilityDelta": 0.})
    for probe in probes[:4]:
        active_count = 0
        for name, count in counts.items():
            inactive = profile == "D0" and name in {"l2." + n for n in RUN.INACTIVE}
            zero_parameter = profile == "D0" and name in {"l2." + n for n in RUN.ACTIVE}
            gn = 0. if inactive else 1.
            pn = 0. if zero_parameter else 1.
            active_count += not inactive
            rows.append({"kind": "GRADIENT", "profile": profile, "probe": probe["id"], "tensor": name,
                         "parameters": count, "allFinite": "true", "gradientL2": gn, "parameterL2": pn,
                         "gradientMaxAbs": gn, "nonzeroGradients": 0 if inactive else 1,
                         "relativeGradientL2": gn / pn if pn else "UNDEFINED_ZERO_PARAMETER_NORM"})
        rows.append({"kind": "LOSS", "profile": profile, "probe": probe["id"], "inputIds": "510,65",
                     "targets": "65,511", "targetCount": 2, "lossF32": 2., "meanNll": 2.,
                     "totalGradientL2": math.sqrt(active_count), "nonzeroGradients": active_count})
        if profile == "W0":
            for name, count in RUN.tensor_counts("S0").items():
                rows.append({"kind": "CHAIN_TENSOR", "profile": profile, "probe": probe["id"],
                             "tensor": name, "coordinates": count, "maximumAbsoluteError": 0.})
            rows.append({"kind": "CHAIN", "profile": profile, "probe": probe["id"], "pass": "true",
                         "coordinates": 132160, "maximumAbsoluteError": 0., "relativeL2Error": 0.})
        if profile == "D0":
            rows.append({"kind": "DEPTH_ACCESS", "profile": profile, "probe": probe["id"],
                         "commonGradientBitwiseParity": "true", "inactiveUpstreamAllZero": "true",
                         "allThreeOutputPathsNonzero": "true", "inactiveUpstreamParameters": 1838592,
                         "newLayerParameters": 3149824})
        epsilon = struct.unpack(">f", struct.pack(">f", .001))[0]
        for name in (("nf", "l2.wo", "l2.w2") if profile == "D0" else ("nf",)):
            rows.append({"kind": "DIRECTION", "profile": profile, "probe": probe["id"], "tensor": name,
                         "restored": "true", "pass": "true", "epsilon": .001, "analyticDerivative": 1.,
                         "positiveNll": 2. + epsilon, "negativeNll": 2. - epsilon,
                         "numericalDerivative": 1., "absoluteError": 0., "limit": .0205})
    rows.append({"kind": "DONE", "profile": profile, "allArraysRestored": "true", "inputsUnchanged": "true",
                 "optimizerUpdates": 0, "serializedCheckpoints": 0, "finalParameterIdentity": "a" * 64,
                 "directionChecks": 12 if profile == "D0" else 4, "lossProbes": 4})
    return reg, rows


def raw(rows):
    return ("\n".join(row["kind"] + "".join("\t" + k + "=" + str(v) for k, v in row.items() if k != "kind") for row in rows) + "\n").encode()


class GradientGateTest(unittest.TestCase):
    def test_all_three_complete_profiles(self):
        for profile in RUN.COUNTS:
            reg, rows = fixture(profile)
            with self.subTest(profile=profile):
                result = RUN.validate(raw(rows), profile, reg)
                self.assertEqual(result["parameters"], RUN.COUNTS[profile])
                self.assertEqual(len(result["cases"]), 4)

    def test_counts_derived_from_layout(self):
        self.assertEqual(sum(RUN.tensor_counts("D0")["l2." + n] for n in RUN.INACTIVE), 1838592)
        self.assertEqual(sum(RUN.tensor_counts("D0")["l2." + n] for n in RUN.ACTIVE), 1311232)

    def test_mutated_gates_rejected(self):
        mutations = (("START", "optimizerConstructed", "true"), ("START", "parameters", 10000000),
                     ("START", "smallCheckpointSha256", "0" * 64),
                     ("INITIAL_PARITY", "pass", "false"), ("INITIAL_PARITY", "maximumProbabilityDelta", .00002),
                     ("INITIAL_PARITY", "deepWideBitwiseParity", "false"),
                     ("GRADIENT", "allFinite", "false"), ("GRADIENT", "gradientL2", "nan"),
                     ("GRADIENT", "nonzeroGradients", -1), ("GRADIENT", "relativeGradientL2", 9.),
                     ("LOSS", "meanNll", 3.), ("LOSS", "totalGradientL2", 999.),
                     ("LOSS", "targetCount", 3), ("LOSS", "targets", "65,65"),
                     ("DEPTH_ACCESS", "inactiveUpstreamParameters", 1838593),
                     ("DEPTH_ACCESS", "allThreeOutputPathsNonzero", "false"),
                     ("DEPTH_ACCESS", "commonGradientBitwiseParity", "false"),
                     ("DIRECTION", "pass", "false"), ("DIRECTION", "restored", "false"),
                     ("DIRECTION", "numericalDerivative", 2.), ("DIRECTION", "absoluteError", 1.),
                     ("DIRECTION", "limit", 1.), ("DIRECTION", "epsilon", .1),
                     ("DONE", "finalParameterIdentity", "b" * 64), ("DONE", "optimizerUpdates", 1),
                     ("DONE", "serializedCheckpoints", 1), ("DONE", "directionChecks", 11))
        reg, rows = fixture("D0")
        for kind, key, value in mutations:
            with self.subTest(kind=kind, key=key):
                changed = copy.deepcopy(rows)
                next(row for row in changed if row["kind"] == kind)[key] = value
                with self.assertRaises(ValueError):
                    RUN.validate(raw(changed), "D0", reg)

    def test_chain_gate_not_just_pass_flag(self):
        reg, rows = fixture("W0")
        for key, value in (("coordinates", 132159), ("relativeL2Error", .001), ("maximumAbsoluteError", .001), ("pass", "false")):
            changed = copy.deepcopy(rows)
            next(row for row in changed if row["kind"] == "CHAIN")[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                RUN.validate(raw(changed), "W0", reg)

    def test_depth_flags_must_match_actual_gradients(self):
        reg, rows = fixture("D0")
        row = next(r for r in rows if r["kind"] == "GRADIENT" and r["tensor"] == "l2.w2")
        row.update(gradientL2=0., gradientMaxAbs=0., nonzeroGradients=0)
        loss = next(r for r in rows if r["kind"] == "LOSS")
        loss["nonzeroGradients"] -= 1
        loss["totalGradientL2"] = math.sqrt(loss["nonzeroGradients"])
        with self.assertRaisesRegex(ValueError, "depth flags"):
            RUN.validate(raw(rows), "D0", reg)

    def test_partial_reordered_or_extra_output_rejected(self):
        reg, rows = fixture("S0")
        for changed in (rows[:-1], rows + [rows[-1]], [rows[1], rows[0], *rows[2:]]):
            with self.assertRaises(ValueError):
                RUN.validate(raw(changed), "S0", reg)

    def test_bad_registration_never_launches(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            record = root / "registration.json"
            record.write_text("{}")
            with patch.object(RUN.CORE, "launch", side_effect=AssertionError("launched")) as launch:
                with self.assertRaisesRegex(ValueError, "registration identity"):
                    RUN.execute(record, "0" * 64, {}, root / "run")
            launch.assert_not_called()
            self.assertFalse((root / "run").exists())


if __name__ == "__main__":
    unittest.main()
