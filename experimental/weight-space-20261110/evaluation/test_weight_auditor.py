import hashlib
import struct
import unittest

from audit_lm1_weights import compare, layout, read_bytes


def checkpoint(metadata=b'first', changed=False, nonfinite=False):
    config = (1, 258, 8, 1, 1, 32, 16)
    out = bytearray(struct.pack('>9i', 0x4c4d3143, 1, *config))

    def utf(value):
        out.extend(struct.pack('>H', len(value)) + value)

    utf(b'0' * 64)
    out.extend(struct.pack('>i', 1))
    utf(b'metadata')
    utf(metadata)
    for index, (name, rows, cols) in enumerate(layout(config)):
        utf(name.encode())
        out.extend(struct.pack('>2i', rows, cols))
        values = [0.0] * (rows * cols)
        if index == 0:
            values[0] = float('nan') if nonfinite else (1.0 if changed else 0.0)
        out.extend(struct.pack('>' + str(len(values)) + 'f', *values))
    return bytes(out) + hashlib.sha256(out).digest()


class WeightAuditTests(unittest.TestCase):
    def test_metadata_only_counterexample(self):
        original = read_bytes(checkpoint())
        changed = read_bytes(checkpoint(metadata=b'second'))
        self.assertNotEqual(original['sha256'], changed['sha256'])
        self.assertEqual(compare(original, changed)['changedFloat32Bits'], 0)

    def test_exact_one_weight_change(self):
        delta = compare(read_bytes(checkpoint()), read_bytes(checkpoint(changed=True)))
        self.assertEqual(delta['changedFloat32Bits'], 1)
        self.assertEqual(delta['changedNumericWeights'], 1)
        self.assertEqual(delta['maximumAbsoluteDelta'], 1.0)

    def test_parameter_count(self):
        self.assertEqual(read_bytes(checkpoint())['parameters'], 2632)

    def test_checksum_rejected(self):
        raw = bytearray(checkpoint())
        raw[-1] ^= 1
        with self.assertRaisesRegex(ValueError, 'checksum'):
            read_bytes(bytes(raw))

    def test_nonfinite_rejected(self):
        with self.assertRaisesRegex(ValueError, 'nonfinite'):
            read_bytes(checkpoint(nonfinite=True))

    def test_truncation_rejected(self):
        raw = checkpoint()[:-36]
        with self.assertRaisesRegex(ValueError, 'truncated'):
            read_bytes(raw + hashlib.sha256(raw).digest())

    def test_trailing_body_rejected(self):
        raw = checkpoint()[:-32] + b'extra'
        with self.assertRaisesRegex(ValueError, 'trailing'):
            read_bytes(raw + hashlib.sha256(raw).digest())


if __name__ == '__main__':
    unittest.main(verbosity=2)
