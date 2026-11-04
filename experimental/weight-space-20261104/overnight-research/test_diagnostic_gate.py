import base64
import copy
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("diagnostic_controller", Path(__file__).with_name("run_diagnostics.py"))
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


def fixture(arm=None):
    arm = arm or RUN.arms()[0]
    probes = RUN.probe_bytes()[0]
    reg = {"probesTsvSha256": RUN.sha(RUN.probe_bytes()[1])}
    p = [0.] * 512
    p[65], p[511] = .25, .75
    vector = base64.b64encode(struct.pack(">512f", *p)).decode()
    rows = [{"kind": "START", "arm": arm["id"], **{k: arm[k] for k in ("receiver", "donor", "block")},
             "receiverSha256": RUN.MODELS[arm["receiver"]], "donorSha256": RUN.MODELS[arm["donor"]],
             "tokenizerSha256": RUN.TOKENIZER, "probesSha256": reg["probesTsvSha256"],
             "training": "false", "parameters": 132160 if arm["receiver"][0] == "S" else 6562304,
             "replacedTensors": 0 if arm["block"] == "native" else 1 if arm["block"] in ("tok", "nf") else 11}]
    for probe in probes:
        for stage in RUN.STAGES:
            rows.append({"kind": "TRACE", "arm": arm["id"], "probe": probe["id"], "stage": stage,
                         "replicas": 1 if arm["receiver"][0] == "S" else 8, "groups": 128,
                         "exactlyTiedGroups": 128, "valueRms": 1., "spreadRms": 0.,
                         "relativeSpreadRms": 0., "maximumReplicaRange": 0.})
        rows.append({"kind": "PROBE", "arm": arm["id"], "probe": probe["id"], "inputIds": "510,65",
                     "instrumentationBitwiseParity": "true", "probabilitiesF32BE": vector,
                     "eosId": 511, "bosId": 510, "eosProbability": .75, "topAllowedId": 511,
                     "eosRank": 1, "eosLogit": 2., "bestOtherLogit": 1., "eosMargin": 1., "entropyNats": .5})
        if arm["block"] == "native":
            rows.append({"kind": "GENERATION", "arm": arm["id"], "probe": probe["id"], "truncated": "false",
                         "stop": "EOS", "tokens": 0, "millis": 1, "textBase64": ""})
    rows.append({"kind": "DONE", "arm": arm["id"], "probes": 12, "inputsUnchanged": "true",
                 "receiverArraysUnchanged": "true", "trainingUpdates": 0, "serializedCheckpoints": 0})
    return arm, reg, rows


def raw(rows):
    return ("\n".join(row["kind"] + "".join("\t" + k + "=" + str(v) for k, v in row.items() if k != "kind")
                      for row in rows) + "\n").encode()


class DiagnosticGateTest(unittest.TestCase):
    def test_all_twenty_valid_arm_structures(self):
        for arm in RUN.arms():
            with self.subTest(arm=arm["id"]):
                arm, reg, rows = fixture(arm)
                self.assertEqual(len(RUN.parse_log(raw(rows), arm, reg)), 12)

    def test_missing_duplicate_extra_or_reordered_rows(self):
        arm, reg, rows = fixture()
        for bad in (rows[:-1], rows + [rows[-1]], [rows[1], rows[0], *rows[2:]], rows[:7] + rows[8:]):
            with self.subTest(length=len(bad)), self.assertRaises(ValueError):
                RUN.parse_log(raw(bad), arm, reg)

    def test_all_identity_and_integrity_mutations_rejected(self):
        arm, reg, rows = fixture()
        mutations = ((0, "receiverSha256", "0" * 64), (0, "donorSha256", "0" * 64),
                     (0, "tokenizerSha256", "0" * 64), (0, "probesSha256", "0" * 64),
                     (0, "training", "true"), (0, "parameters", 6562304),
                     (0, "replacedTensors", 1), (1, "replicas", 8), (1, "stage", "invented"),
                     (1, "spreadRms", "nan"), (1, "exactlyTiedGroups", 129),
                     (7, "instrumentationBitwiseParity", "false"), (7, "probe", "foreign"),
                     (7, "topAllowedId", 65), (7, "eosProbability", .2), (7, "eosRank", 2),
                     (7, "eosId", 65), (7, "eosMargin", "inf"),
                     (8, "tokens", 33), (8, "truncated", "true"), (8, "stop", "CANCELLED"),
                     (-1, "inputsUnchanged", "false"), (-1, "receiverArraysUnchanged", "false"),
                     (-1, "trainingUpdates", 1), (-1, "serializedCheckpoints", 1))
        for index, key, value in mutations:
            with self.subTest(index=index, key=key):
                changed = copy.deepcopy(rows)
                changed[index][key] = value
                with self.assertRaises(ValueError):
                    RUN.parse_log(raw(changed), arm, reg)

    def test_probability_corruption_rejected(self):
        arm, reg, rows = fixture()
        for values in ([0.] * 512, [float("nan")] * 512, [-1.] + [0.] * 511, [.5, .5]):
            changed = copy.deepcopy(rows)
            changed[7]["probabilitiesF32BE"] = base64.b64encode(struct.pack(">" + str(len(values)) + "f", *values)).decode()
            with self.subTest(length=len(values)), self.assertRaises(ValueError):
                RUN.parse_log(raw(changed), arm, reg)

    def test_duplicate_key_rejected(self):
        arm, reg, rows = fixture()
        data = raw(rows).replace(b"START\t", b"START\tarm=S0\t", 1)
        with self.assertRaises(ValueError):
            RUN.parse_log(data, arm, reg)

    def test_initial_probability_parity_fail_closed(self):
        arm, reg, rows = fixture()
        result = RUN.parse_log(raw(rows), arm, reg)
        self.assertTrue(RUN.parity({"S0": result, "W0": result})["pass"])
        other = copy.deepcopy(result)
        first = next(iter(other))
        p = [0.] * 512
        p[65], p[511] = .2501, .7499
        other[first]["probabilitiesF32BE"] = base64.b64encode(struct.pack(">512f", *p)).decode()
        with self.assertRaises(ValueError):
            RUN.parity({"S0": result, "W0": other})

    def test_invalid_registration_starts_nothing(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "registration.json"
            path.write_text("{}")
            output = Path(root) / "run"
            with patch.object(RUN, "launch", side_effect=AssertionError("launched")) as child:
                with self.assertRaisesRegex(ValueError, "registration digest"):
                    RUN.execute(path, "0" * 64, {}, output)
            child.assert_not_called()
            self.assertFalse(output.exists())

    def test_existing_file_never_overwritten(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "record"
            path.write_bytes(b"original")
            with self.assertRaises(FileExistsError):
                RUN.write_new(path, b"changed")
            self.assertEqual(path.read_bytes(), b"original")

    def test_symlink_input_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            original = Path(root) / "original"
            original.write_bytes(b"bytes")
            link = Path(root) / "link"
            link.symlink_to(original)
            with self.assertRaises(ValueError):
                RUN.digest(link)

    def test_receipt_failure_and_binding_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            out, err = Path(root) / "out", Path(root) / "err"
            out.write_bytes(b"output")
            err.write_bytes(b"")
            arm = RUN.arms()[0]
            receipt = {"schemaVersion": 1, "status": "PASS", "arm": arm, "registrationSha256": "r",
                       "command": ["java"], "stdoutSha256": RUN.digest(out), "stderrSha256": RUN.digest(err),
                       "exitCode": 0, "timedOut": False, "elapsedSeconds": 1., "maxRssKiB": 10000}
            RUN.verify_receipt(receipt, arm, "r", ["java"], out, err)
            for key, value in (("status", "FAIL"), ("registrationSha256", "foreign"), ("exitCode", 1),
                               ("timedOut", True), ("elapsedSeconds", 122), ("elapsedSeconds", float("nan")),
                               ("maxRssKiB", 786433), ("stdoutSha256", "0" * 64), ("command", ["train"])):
                with self.subTest(key=key), self.assertRaises(ValueError):
                    RUN.verify_receipt({**receipt, key: value}, arm, "r", ["java"], out, err)


if __name__ == "__main__":
    unittest.main()
