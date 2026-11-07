#!/usr/bin/env python3
"""Small synthetic format fixtures; these are not trained models or capacity tests."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1] / "weight16m_preflight.py"
SPEC = importlib.util.spec_from_file_location("weight16m_preflight", MODULE)
preflight = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(preflight)


def utf(text):
    raw = text.encode("ascii")
    return struct.pack(">H", len(raw)) + raw


def fixture(token_sha, arch=1):
    # Independent explicit tiny layout, not obtained from the production reader.
    shapes = [("tok", 258, 8)]
    if arch == 0:
        shapes += [("pos", 8, 8)]
    shapes += [("l0.n1", 1, 8)]
    if arch == 0:
        shapes += [("l0." + name, 8, 8) for name in ("wq", "wk", "wv", "wo")]
    else:
        shapes += [("l0.wz", 8, 8), ("l0.bz", 1, 8), ("l0.wc", 8, 8),
                   ("l0.wg", 8, 8), ("l0.wo", 8, 8)]
    shapes += [("l0.n2", 1, 8), ("l0.w1", 8, 8), ("l0.b1", 1, 8),
               ("l0.w2", 8, 8), ("l0.b2", 1, 8), ("nf", 1, 8)]
    body = bytearray(struct.pack(">9i", 0x4C4D3143, 1, arch, 258, 8, 1, 1, 8, 8))
    body += utf(token_sha) + struct.pack(">i", 0)
    offsets = {}
    for name, rows, columns in shapes:
        body += utf(name)
        offsets[name] = len(body)
        body += struct.pack(">2i", rows, columns)
        body += struct.pack(">f", 0.25) * (rows * columns)
    return body, offsets


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.tokenizer = self.directory / "synthetic.lmbp"
        self.tokenizer.write_bytes(struct.pack(">2i", 0x4C4D4250, 0))
        self.token_sha = hashlib.sha256(self.tokenizer.read_bytes()).hexdigest()
        self.checkpoint = self.directory / "synthetic.lm1"
        self.body, self.offsets = fixture(self.token_sha)
        self.save()

    def save(self):
        self.checkpoint.write_bytes(self.body + hashlib.sha256(self.body).digest())

    def reject(self, reason):
        with self.assertRaisesRegex(preflight.InvalidCheckpoint, reason):
            preflight.validate(self.checkpoint, self.tokenizer)

    def test_valid_mingru_counts_actual_values_and_reports_limits(self):
        result = preflight.validate(self.checkpoint, self.tokenizer, 2496)
        self.assertEqual(result["parameters"], 2496)
        self.assertEqual(result["tensorCount"], 13)
        self.assertEqual(result["checkpointSha256"], hashlib.sha256(self.checkpoint.read_bytes()).hexdigest())
        self.assertTrue(result["finiteWeights"])
        self.assertEqual(result["trainingMemory"], "UNMEASURED")
        self.assertEqual(result["exactOptimizerResume"], "UNAVAILABLE")

    def test_transformer_positions_and_layout(self):
        self.body, self.offsets = fixture(self.token_sha, arch=0)
        self.save()
        result = preflight.validate(self.checkpoint, self.tokenizer, 2552)
        self.assertEqual(result["architecture"], "TRANSFORMER")
        self.assertEqual(result["tensors"][1]["name"], "pos")

    def test_16m_layout_and_next_depth_raw_size(self):
        config = dict(arch=1, vocab=512, dim=512, layers=5, heads=4, context=256, mlp=2048)
        layout = preflight.tensor_layout(config)
        self.assertEqual(sum(r * c for _, r, c in layout), 16_011_776)
        self.assertEqual(len(layout), 57)
        config["layers"] = 6
        count = sum(r * c for _, r, c in preflight.tensor_layout(config))
        self.assertEqual(count, 19_161_600)
        self.assertGreater(count * 4, preflight.CHECKPOINT_LIMIT)

    def test_internal_checksum_corruption(self):
        raw = bytearray(self.checkpoint.read_bytes())
        raw[-1] ^= 1
        self.checkpoint.write_bytes(raw)
        self.reject("body SHA-256")

    def test_nonfinite_weights_even_with_valid_checksum(self):
        for value in (float("nan"), float("inf"), -float("inf")):
            with self.subTest(value=value):
                struct.pack_into(">f", self.body, self.offsets["tok"] + 8, value)
                self.save()
                self.reject("nonfinite")

    def test_wrong_tensor_shape_with_valid_checksum(self):
        struct.pack_into(">i", self.body, self.offsets["tok"], 257)
        self.save()
        self.reject("name/order/shape")

    def test_wrong_tensor_name_with_valid_checksum(self):
        self.body[self.offsets["tok"] - 3:self.offsets["tok"]] = b"bad"
        self.save()
        self.reject("name/order/shape")

    def test_wrong_tokenizer_binding_with_valid_checksum(self):
        self.body, self.offsets = fixture("0" * 64)
        self.save()
        self.reject("tokenizer binding")

    def test_invalid_tokenizer_forward_reference(self):
        self.tokenizer.write_bytes(struct.pack(">4i", 0x4C4D4250, 1, 256, 0))
        self.reject("merge reference")

    def test_trailing_and_truncated_body(self):
        self.body += b"extra"
        self.save()
        self.reject("trailing checkpoint")
        self.body = self.body[:-6]
        self.save()
        self.reject("truncated")

    def test_header_and_parameter_identity(self):
        for index, value, reason in ((0, 0, "magic"), (1, 2, "version"),
                                     (2, 3, "architecture"), (6, 3, "configuration")):
            with self.subTest(field=index):
                self.body, self.offsets = fixture(self.token_sha)
                struct.pack_into(">i", self.body, index * 4, value)
                self.save()
                self.reject(reason)
        self.body, self.offsets = fixture(self.token_sha)
        self.save()
        with self.assertRaisesRegex(preflight.InvalidCheckpoint, "parameter count"):
            preflight.validate(self.checkpoint, self.tokenizer, 16_011_776)
        with self.assertRaisesRegex(preflight.InvalidCheckpoint, "checkpoint SHA-256"):
            preflight.validate(self.checkpoint, self.tokenizer, expected_checkpoint_sha="0" * 64)

    def test_oversize_file_is_rejected_before_loading_weights(self):
        with self.checkpoint.open("wb") as stream:
            stream.truncate(preflight.CHECKPOINT_LIMIT + 1)
        self.reject("size contract")

    def test_modified_utf_java_null_and_surrogates(self):
        self.assertEqual(preflight.modified_utf8(b"A\xc0\x80\xed\xa0\xbd\xed\xb8\x80"), "A\0\U0001f600")
        with self.assertRaises(preflight.InvalidCheckpoint):
            preflight.modified_utf8(b"\xf0\x9f\x98\x80")

    def test_cli_missing_is_unavailable_and_real_target_is_default(self):
        for path, expected_code, expected_status in ((self.directory / "absent.lm1", 3, "UNAVAILABLE"),
                                                     (self.checkpoint, 2, "FAIL")):
            with self.subTest(path=path), contextlib.redirect_stdout(io.StringIO()) as output:
                code = preflight.main(["--checkpoint", str(path), "--tokenizer", str(self.tokenizer)])
                self.assertEqual(code, expected_code)
                self.assertEqual(json.loads(output.getvalue())["status"], expected_status)


if __name__ == "__main__":
    unittest.main()
