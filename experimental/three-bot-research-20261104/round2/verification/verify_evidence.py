#!/usr/bin/env python3
"""Independent, read-only checks of the completed round-2 evidence inputs."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import statistics
import struct


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_hashes(root: Path, records: list[dict], key: str) -> int:
    for record in records:
        path = root / record[key]
        if digest(path) != record['sha256']:
            raise ValueError(f'Hash mismatch: {path}')
        if path.stat().st_size != record['bytes']:
            raise ValueError(f'Size mismatch: {path}')
    return len(records)


def glb(path: Path) -> tuple[dict, bytes]:
    raw = path.read_bytes()
    magic, version, length = struct.unpack_from('<4sII', raw)
    if (magic, version, length) != (b'glTF', 2, len(raw)):
        raise ValueError('Invalid GLB envelope')
    offset = 12
    chunks = {}
    while offset < len(raw):
        size, kind = struct.unpack_from('<I4s', raw, offset)
        offset += 8
        if kind in chunks or offset + size > len(raw):
            raise ValueError('Invalid GLB chunk')
        chunks[kind] = raw[offset:offset + size]
        offset += size
    if offset != len(raw):
        raise ValueError('Incomplete GLB')
    return json.loads(chunks[b'JSON']), chunks[b'BIN\0']


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = args.work_root.resolve(strict=True)
    autonomy, avatar, language = [root / name for name in ('autonomy', 'avatar', 'language')]
    a_manifest = json.loads((autonomy / 'DELIVERABLES.json').read_text())
    checked_autonomy = checked_hashes(autonomy, a_manifest['files'], 'path')
    v_manifest = json.loads((avatar / 'results/artifact-hashes.json').read_text())
    checked_avatar = checked_hashes(avatar, v_manifest['records'], 'file')
    bindings = json.loads((avatar / 'results/SourceBindings.json').read_text())['sourceBindings']
    checked_sources = checked_hashes(Path('/'), bindings, 'file')
    original_path = Path(bindings[0]['file'])
    original_json, original_bin = glb(original_path)
    structural_keys = ['accessors', 'meshes', 'nodes', 'skins', 'scenes', 'scene', 'animations']
    variants = []
    for name in ('v1_soft_flow', 'v2_fine_flow'):
        path = avatar / 'variants' / (name + '.glb')
        candidate, binary = glb(path)
        if not binary.startswith(original_bin):
            raise ValueError('Original BIN prefix changed')
        for key in structural_keys:
            if candidate.get(key) != original_json.get(key):
                raise ValueError(f'Changed structure: {key}')
        targets = [len(mesh.get('primitives', [{}])[0].get('targets', [])) for mesh in candidate['meshes']]
        variants.append({'name': name, 'sha256': digest(path), 'originalBinPrefixBytes': len(original_bin),
                         'unchangedStructuralKeys': structural_keys, 'morphCountsPerMesh': targets})
    measurement_file = autonomy / 'runs/20261104T191615417409Z/recall.stdout.json'
    measurement = json.loads(measurement_file.read_text())
    conditions = measurement['conditions']
    if sum(len(c['samples']) for c in conditions) != 260:
        raise ValueError('Unexpected measurement count')
    for condition in conditions:
        median = statistics.median(s['wall_ms'] for s in condition['samples'])
        if median != condition['wall_ms']['median']:
            raise ValueError('Incorrect reported median')
    paired = []
    for size in (32, 128):
        selected = [c for c in conditions if c['corpus_records'] == size
                    and c['query_paths'] == ['src/math.mjs'] and c['max_chars'] == 4096
                    and c['query_source'] == measurement['baseline_sha']]
        by_limit = {c['limit']: c for c in selected}
        if set(by_limit) != {2, 3}:
            raise ValueError('Missing paired conditions')
        for left, right in zip(by_limit[2]['samples'], by_limit[3]['samples'], strict=True):
            for field in ('output_sha256', 'returned_ids', 'output_chars'):
                if left[field] != right[field]:
                    raise ValueError('Paired outputs differ')
        paired.append({'records': size, 'pairs': len(by_limit[2]['samples']), 'identicalOutputs': True,
                       'limit3MedianMs': by_limit[3]['wall_ms']['median'],
                       'limit2MedianMs': by_limit[2]['wall_ms']['median'],
                       'ratioOfMedians': by_limit[3]['wall_ms']['median'] / by_limit[2]['wall_ms']['median']})
    registration_path = language / 'registration.json'
    registration = json.loads(registration_path.read_text())
    for name, expected in registration['files'].items():
        if digest(language / name) != expected:
            raise ValueError(f'Registration drift: {name}')
    result = {'checkedUtc': datetime.now(timezone.utc).isoformat(), 'checker': 'root-independent-read-only',
              'newModelExecution': False, 'newBenchmarkExecution': False, 'androidExecution': False,
              'autonomyDeliverableHashesChecked': checked_autonomy, 'avatarArtifactHashesChecked': checked_avatar,
              'avatarSourceBindingsChecked': checked_sources, 'avatarVariants': variants,
              'autonomyRawMeasurementSha256': digest(measurement_file), 'rawMediansChecked': len(conditions),
              'pairedRecallChecks': paired, 'languageRegisteredFilesChecked': len(registration['files']),
              'languageRegistrationSha256': digest(registration_path), 'passed': True}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
