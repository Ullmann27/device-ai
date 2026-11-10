#!/usr/bin/env python3
"""Read LM1C tensors independently; ignore metadata for weight comparisons."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import struct


def require(condition, message):
    if not condition:
        raise ValueError(message)


def layout(config):
    arch, vocab, dim, layers, heads, context, mlp = config
    require(arch in (0, 1), 'unknown architecture')
    require(258 <= vocab <= 8192 and 8 <= dim <= 512, 'invalid dimensions')
    require(1 <= layers <= 12 and heads >= 1 and dim % heads == 0, 'invalid layers/heads')
    require(8 <= context <= 2048 and 8 <= mlp <= 4096, 'invalid context/mlp')
    result = [('tok', vocab, dim)]
    if arch == 0:
        result.append(('pos', context, dim))
    for layer in range(layers):
        prefix = f'l{layer}.'
        result.append((prefix + 'n1', 1, dim))
        if arch == 0:
            result.extend((prefix + name, dim, dim) for name in ('wq', 'wk', 'wv', 'wo'))
        else:
            result.extend([(prefix + 'wz', dim, dim), (prefix + 'bz', 1, dim)])
            result.extend((prefix + name, dim, dim) for name in ('wc', 'wg', 'wo'))
        result.extend([(prefix + 'n2', 1, dim), (prefix + 'w1', dim, mlp),
                       (prefix + 'b1', 1, mlp), (prefix + 'w2', mlp, dim),
                       (prefix + 'b2', 1, dim)])
    result.append(('nf', 1, dim))
    return result


def read_bytes(raw):
    require(64 < len(raw) <= 64 * 1024 * 1024, 'checkpoint size outside contract')
    body = raw[:-32]
    require(hashlib.sha256(body).digest() == raw[-32:], 'checkpoint checksum mismatch')
    offset = 0

    def take(size):
        nonlocal offset
        require(size >= 0 and offset + size <= len(body), 'truncated checkpoint')
        result = body[offset:offset + size]
        offset += size
        return result

    def integer():
        return struct.unpack('>i', take(4))[0]

    def utf_raw():
        return take(struct.unpack('>H', take(2))[0])

    require(integer() == 0x4c4d3143 and integer() == 1, 'unsupported LM1C header')
    config = tuple(integer() for _ in range(7))
    expected = layout(config)
    tokenizer = utf_raw().decode('ascii')
    require(len(tokenizer) == 64 and all(c in '0123456789abcdef' for c in tokenizer), 'invalid tokenizer identity')
    metadata_count = integer()
    require(0 <= metadata_count <= 64, 'invalid metadata count')
    for _ in range(metadata_count):
        utf_raw()
        utf_raw()
    tensors = []
    for name, rows, cols in expected:
        require(utf_raw() == name.encode('ascii'), 'tensor name mismatch')
        require(integer() == rows and integer() == cols, 'tensor shape mismatch')
        bits = take(rows * cols * 4)
        values = [item[0] for item in struct.iter_unpack('>f', bits)]
        require(all(math.isfinite(value) for value in values), 'nonfinite weight')
        tensors.append((name, rows, cols, bits, values))
    require(offset == len(body), 'trailing checkpoint body')
    return {'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw),
            'config': config, 'tokenizer': tokenizer, 'tensors': tensors,
            'parameters': sum(rows * cols for _, rows, cols in expected)}


def compare(before, after):
    require(before['config'] == after['config'], 'different model configuration')
    require(before['tokenizer'] == after['tokenizer'], 'different tokenizer')
    bit_changes = numeric_changes = 0
    max_delta = 0.0
    for a, b in zip(before['tensors'], after['tensors'], strict=True):
        require(a[:3] == b[:3], 'different tensor layout')
        bit_changes += sum(a[3][i:i + 4] != b[3][i:i + 4] for i in range(0, len(a[3]), 4))
        numeric_changes += sum(x != y for x, y in zip(a[4], b[4], strict=True))
        max_delta = max(max_delta, max(abs(x - y) for x, y in zip(a[4], b[4], strict=True)))
    return {'parameters': before['parameters'], 'changedFloat32Bits': bit_changes,
            'changedNumericWeights': numeric_changes, 'maximumAbsoluteDelta': max_delta}


def read(path):
    require(path.is_file() and not path.is_symlink(), 'regular checkpoint required')
    return read_bytes(path.read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, required=True)
    parser.add_argument('--before-sha256', required=True)
    parser.add_argument('--continuous', type=Path, required=True)
    parser.add_argument('--resumed', type=Path, required=True)
    parser.add_argument('--moment-reset', type=Path)
    parser.add_argument('--weights-reset', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'output already exists')
    paths = {'before': args.before, 'continuous': args.continuous, 'resumed': args.resumed}
    if args.moment_reset:
        paths['moment_reset'] = args.moment_reset
    if args.weights_reset:
        paths['weights_reset'] = args.weights_reset
    models = {name: read(path) for name, path in paths.items()}
    require(models['before']['sha256'] == args.before_sha256, 'unregistered initial checkpoint')
    comparisons = {'before_to_continuous': compare(models['before'], models['continuous']),
                   'continuous_to_resumed': compare(models['continuous'], models['resumed'])}
    for name in ('moment_reset', 'weights_reset'):
        if name in models:
            comparisons['continuous_to_' + name] = compare(models['continuous'], models[name])
    checks = {'training_changes_weights': comparisons['before_to_continuous']['changedNumericWeights'] > 0,
              'resume_preserves_weight_bits': comparisons['continuous_to_resumed']['changedFloat32Bits'] == 0}
    for name in ('moment_reset', 'weights_reset'):
        if name in models:
            checks[name + '_changes_weights'] = comparisons['continuous_to_' + name]['changedNumericWeights'] > 0
    report = {'schema': 'lm1-direct-weight-audit-v1', 'status': 'PASS' if all(checks.values()) else 'FAIL',
              'models': {name: {key: value for key, value in model.items() if key != 'tensors'}
                         for name, model in models.items()}, 'comparisons': comparisons, 'checks': checks,
              'scope': 'read-only parameter identity; no new optimizer updates; no language-quality claim'}
    with args.output.open('x') as output:
        json.dump(report, output, indent=2)
        output.write('\n')
    print(json.dumps({'status': report['status'], 'comparisons': comparisons}))
    raise SystemExit(0 if report['status'] == 'PASS' else 1)


if __name__ == '__main__':
    main()
