#!/usr/bin/env python3
"""Inventory existing PNGs and receipts without rendering."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
from datetime import datetime, timezone

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--workspace', type=Path, required=True)
parser.add_argument('--toolchain', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[3]
source = 'a7b4d1a090ab7117f29f6fcc3440b60b863395a5'
sha = lambda data: hashlib.sha256(data).hexdigest()
load = lambda path: json.loads(path.read_text())

def git(*cmd):
    return subprocess.check_output(['git', *cmd], cwd=repo, text=True).strip()

tracked = set(git('ls-files').splitlines())
sets = [
    ('native-navigation-before', args.workspace / 'cinema-native-capture', '*.png', 'Robolectric API28 native Canvas'),
    ('native-navigation-after', args.workspace / 'cinema-native-after', '*.png', 'Robolectric API28 native Canvas'),
    ('native-sequence-and-static', args.workspace / 'cinema-native-sequence', '*.png', 'Robolectric API28 native Canvas'),
    ('native-stop', args.workspace / 'cinema-stop-completed', '*.png', 'Robolectric API28 native Canvas'),
    ('native-dock-red', args.workspace / 'cinema-button-before', '*.png', 'Robolectric API28 native Canvas'),
    ('native-unbound-scenes', args.workspace / 'cinema-native-scenes', '*.png', 'Robolectric API28 native Canvas; unbound source'),
    ('java2d-stills', args.toolchain / 'candidate-cinema-stills', '*.png', 'Java2D'),
    ('skia-edge', args.workspace / 'viscous-edge-preview', '*.png', 'Skia 144.1 software raster'),
    ('original-avatar', args.workspace / 'cinema-original-avatar/render', '*.png', 'Blender 4.3.2 Cycles CPU'),
]
blender = load(repo / 'docs/evidence/cinema-native-capture-20261110/original-avatar-host-render.json')
blender_frames = {x['file']: x for x in blender['frames']}
stop = load(repo / 'docs/evidence/cinema-stop-return-20261110/completed-sequence.json')
stop_frames = {x['file']: x for x in stop['frames']}
java = load(repo / 'docs/evidence/cinema-fluid-light-20261110/preview-source-binding.json')
shader = load(args.workspace / 'viscous-edge-preview/metrics.json')
records, summaries = [], []
for group, directory, pattern, backend in sets:
    current = []
    for png in sorted(directory.glob(pattern)):
        raw = png.read_bytes()
        assert raw[:8] == b'\x89PNG\r\n\x1a\n', png
        width, height = struct.unpack('>II', raw[16:24])
        digest = sha(raw)
        sidecar = png.with_suffix('.json')
        metadata = load(sidecar) if sidecar.exists() else {}
        declared = metadata.get('sha256', metadata.get('png_sha256'))
        bound_source = metadata.get('source_commit')
        if group == 'original-avatar':
            declared = blender_frames[png.name]['imageSha256']
            bound_source = blender['mapperSourceCommit']
        elif group == 'native-stop':
            declared = stop_frames[png.name]['sha256']
        elif group == 'java2d-stills':
            bound_source = java['source_sha']
        reference_matches = []
        for folder in ('cinema-native-sequence-20261110', 'cinema-stop-return-20261110'):
            rel = f'docs/evidence/{folder}/{png.name}'
            f = repo / rel
            if rel in tracked and f.exists() and sha(f.read_bytes()) == digest:
                reference_matches.append(rel)
        location = ('toolchain/' + str(png.relative_to(args.toolchain))) if args.toolchain in png.parents else (
            'workspace/' + str(png.relative_to(args.workspace)))
        row = {'group': group, 'file': png.name, 'location_alias': location,
               'width_px': width, 'height_px': height, 'bytes': len(raw), 'sha256': digest,
               'backend': backend, 'source_commit': bound_source,
               'metadata_sha256': sha(sidecar.read_bytes()) if sidecar.exists() else None,
               'source_attribution': 'per-frame JSON' if sidecar.exists() else ('group receipt' if bound_source else 'UNBOUND'),
               'declared_png_hash': declared, 'declared_hash_matches': None if declared is None else declared == digest,
               'committed_identical_pngs': reference_matches,
               'phase': metadata.get('phase', metadata.get('capture_state', png.stem)),
               'test_outcome': 'RED_TWO_ASSERTIONS' if group == 'native-dock-red' else 'SEE_GROUP_RECEIPT'}
        assert declared is None or declared == digest, png
        current.append(row)
    records += current
    summaries.append({'group': group, 'pngs_present': len(current),
                      'declared_hashes_verified': sum(x['declared_hash_matches'] is True for x in current),
                      'source_commit_attributed_pngs': sum(x['source_commit'] is not None for x in current)})

bindings = []
for path, expected in blender['mapperSourceHashes'].items():
    bindings.append({'kind': 'Blender mapper', 'path': path, 'expected_sha256': expected,
                     'matches_audited_source': sha((repo / path).read_bytes()) == expected})
for row in java['inputs']:
    if not row['path'].startswith('generated/'):
        bindings.append({'kind': 'Java2D compiled input', 'path': row['path'], 'expected_sha256': row['sha256'],
                         'matches_audited_source': sha((repo / row['path']).read_bytes()) == row['sha256']})
text = (repo / 'app/src/main/java/at/heinz/deviceai/ui/JarvisGlassRenderer.kt').read_text()
embedded = re.search(r'const val FLUID_EDGE = """(.*?)"""', text, re.S).group(1)
source_diffs = {}
for prior in {r['source_commit'] for r in records if r['source_commit']}:
    source_diffs[prior] = git('diff', '--name-only', prior, source, '--', 'app/src/main').splitlines()
report = {'schema': 'cinema-render-coverage-v1', 'observed_at_utc': datetime.now(timezone.utc).isoformat(),
          'audited_source': source, 'remote_equivalent_source': '5457351a7585eada2393f80aa81db4bff5cc8897',
          'base_tree': '6906b84e8434d4247a759ac0e7cf701ad82416dc',
          'scope': 'Nine named current-session evidence directories; older repository archives not exhaustively audited',
          'groups': summaries, 'records': records, 'total_pngs': len(records),
          'unique_png_hashes': len({r['sha256'] for r in records}), 'source_bindings': bindings,
          'application_changes_since_capture': source_diffs,
          'shader_binding': {'expected_sha256': shader['shader_sha256'], 'current_sha256': sha(embedded.encode()),
                             'matches': shader['shader_sha256'] == sha(embedded.encode())},
          'original_glb_binding': {'expected_sha256': blender['assetSha256'],
                                  'matches': sha((repo / 'app/src/main/assets/avatar3d/continuum-avatar.glb').read_bytes()) == blender['assetSha256']},
          'new_render_executed': False, 'device_or_gpu_verified': False}
args.output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'total_pngs': report['total_pngs'], 'unique_png_hashes': report['unique_png_hashes'], 'groups': summaries}, indent=2))
