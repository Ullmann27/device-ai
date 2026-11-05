"""Read-only integrity verifier for research ZIP bundles. Never executes payloads."""
import argparse
import hashlib
import json
import os
from pathlib import PurePosixPath
import stat
import zipfile


class IntegrityError(ValueError):
    pass


def _no_duplicate_json_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise IntegrityError(f"duplicate manifest key: {key}")
        result[key] = value
    return result


def _safe_member(name, info):
    if not name or '\\' in name or name.startswith('/') or '\x00' in name:
        raise IntegrityError("unsafe archive path")
    segments = name.split('/')
    if any(segment in ('', '.', '..') for segment in segments):
        raise IntegrityError("non-canonical archive path")
    if PurePosixPath(name).as_posix() != name:
        raise IntegrityError("non-canonical archive path")
    mode = (info.external_attr >> 16) & 0xffff
    if stat.S_IFMT(mode) == stat.S_IFLNK:
        raise IntegrityError("archive contains symlink")
    if info.is_dir():
        raise IntegrityError("directory entry not allowed")


def verify_bundle(path, expected_sha256):
    """Validate all declared files, no extras; return counts only (no private hashes)."""
    if not isinstance(expected_sha256, str) or len(expected_sha256) != 64:
        raise IntegrityError("expected complete archive digest required")
    try:
        int(expected_sha256, 16)
    except ValueError as exc:
        raise IntegrityError("invalid digest") from exc
    with open(path, 'rb') as stream:
        hasher = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            hasher.update(chunk)
    if hasher.hexdigest() != expected_sha256.lower():
        raise IntegrityError("archive digest mismatch")
    try:
        with zipfile.ZipFile(path) as bundle:
            infos = bundle.infolist()
            names = [info.filename for info in infos]
            if len(names) != len(set(names)):
                raise IntegrityError("duplicate archive member")
            for info in infos:
                _safe_member(info.filename, info)
            manifest_paths = [name for name in names if name.endswith('/MANIFEST.json') or name == 'MANIFEST.json']
            if len(manifest_paths) != 1:
                raise IntegrityError("one manifest required")
            manifest_path = manifest_paths[0]
            prefix = manifest_path[:-len('MANIFEST.json')]
            manifest = json.loads(bundle.read(manifest_path), object_pairs_hook=_no_duplicate_json_keys)
            records = manifest.get('files')
            if not isinstance(records, dict) or not records:
                raise IntegrityError("missing file registry")
            expected_names = {manifest_path}
            total_bytes = 0
            for relative, record in records.items():
                if not isinstance(relative, str) or not isinstance(record, dict):
                    raise IntegrityError("invalid registry entry")
                full_name = prefix + relative
                if full_name not in names:
                    raise IntegrityError("registered entry missing")
                if relative == 'MANIFEST.json' or full_name == manifest_path:
                    raise IntegrityError("manifest must not register itself")
                expected_names.add(full_name)
                declared_size = record.get('bytes')
                declared_digest = record.get('sha256')
                if type(declared_size) is not int or declared_size < 0:
                    raise IntegrityError("invalid registered size")
                if not isinstance(declared_digest, str) or len(declared_digest) != 64:
                    raise IntegrityError("invalid registered sha256")
                payload = bundle.read(full_name)
                if len(payload) != declared_size or hashlib.sha256(payload).hexdigest() != declared_digest:
                    raise IntegrityError("registered entry integrity mismatch")
                total_bytes += len(payload)
            if set(names) != expected_names:
                raise IntegrityError("unregistered or extraneous archive member")
            models = manifest.get('models')
            if models is not None:
                if not isinstance(models, dict) or not models:
                    raise IntegrityError("invalid model map")
                for model in models.values():
                    if not isinstance(model, dict) or model.get('path') not in records:
                        raise IntegrityError("model path absent from file registry")
                    record = records[model['path']]
                    if model.get('sha256') != record['sha256'] or model.get('bytes') != record['bytes']:
                        raise IntegrityError("model map contradicts registry")
            return {'status': 'PASS', 'registeredEntries': len(records), 'verifiedBytes': total_bytes,
                    'modelRecords': len(models or {}), 'executedPayloads': False}
    except (zipfile.BadZipFile, EOFError, OSError, json.JSONDecodeError, KeyError) as exc:
        raise IntegrityError('invalid or unreadable archive') from exc


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive')
    parser.add_argument('--expected-sha256', required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(verify_bundle(args.archive, args.expected_sha256), sort_keys=True))
    except IntegrityError as exc:
        parser.exit(2, 'FAIL: ' + str(exc) + '\n')


if __name__ == '__main__':
    main()
