#!/usr/bin/env python3
"""Independent inspection of one source-bound unsigned Command03 QA APK."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert re.fullmatch(r'[0-9a-f]{40}', args.source)
    assert not args.output.exists(), 'Preserve prior evidence; choose a new output path'

    def git(*parts):
        return subprocess.check_output(['git', *parts], cwd=args.repo)

    assert git('rev-parse', 'HEAD').decode().strip() == args.source
    assert not git('status', '--porcelain'), 'Source must remain frozen and clean'
    data = args.apk.read_bytes()
    report = {
        'checked_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'source_commit': args.source,
        'source_main_tree': git('rev-parse', args.source + ':app/src/main').decode().strip(),
        'apk_file': str(args.apk.resolve()), 'bytes': len(data), 'sha256': sha(data),
        'physical_device_verified': False,
        'verification_scope': 'INDEPENDENT_LOCAL_UNSIGNED_APK_STRUCTURE_AND_SOURCE_ASSETS',
    }
    prefix = 'app/src/main/assets/continuum-command03/'
    expected_assets = {}
    for entry in git('ls-tree', '-r', '-z', args.source, '--', prefix).split(b'\0'):
        if not entry:
            continue
        metadata, name = entry.split(b'\t', 1)
        mode, kind, blob = metadata.decode().split()
        assert mode == '100644' and kind == 'blob'
        expected_assets['assets/' + name.decode().removeprefix('app/src/main/assets/')] = blob
    assert len(expected_assets) == 38
    with zipfile.ZipFile(args.apk) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names))
        assert all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts and '\\' not in n for n in names)
        assert archive.testzip() is None
        actual_assets = {n for n in names if n.startswith('assets/continuum-command03/') and not n.endswith('/')}
        assert actual_assets == set(expected_assets)
        assets = []
        for name, blob in sorted(expected_assets.items()):
            content = archive.read(name)
            assert content == git('cat-file', 'blob', blob), name
            assets.append({'path': name, 'bytes': len(content), 'sha256': sha(content)})
        report['html_assets'] = assets
        signatures = [n for n in names if re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA|EC|MF)', n, re.I)]
        assert not signatures, signatures
        report['jar_signatures'] = signatures
        libraries = []
        for name in sorted(n for n in names if n.startswith('lib/') and n.endswith('.so')):
            assert name.startswith('lib/arm64-v8a/'), name
            elf = archive.read(name)
            assert elf[:6] == b'\x7fELF\x02\x01'
            assert struct.unpack_from('<H', elf, 18)[0] == 183
            phoff = struct.unpack_from('<Q', elf, 32)[0]
            phsize, phcount = struct.unpack_from('<HH', elf, 54)
            assert phsize >= 56
            segments = []
            for index in range(phcount):
                kind, flags, offset, vaddr, paddr, filesz, memsz, align = struct.unpack_from('<IIQQQQQQ', elf, phoff + index * phsize)
                if kind == 1:
                    assert align >= 16384 and align & (align - 1) == 0
                    assert offset % 16384 == vaddr % 16384
                    segments.append(align)
            assert segments
            libraries.append({'path': name, 'load_alignments': segments})
        assert len(libraries) == 5
        report['native_libraries'] = libraries
        report['dex_files'] = sorted(n for n in names if re.fullmatch(r'classes\d*\.dex', n))
        assert report['dex_files']
    eocd = data.rfind(b'PK\x05\x06')
    assert eocd >= 0
    central_offset = struct.unpack_from('<I', data, eocd + 16)[0]
    assert data[central_offset - 16:central_offset] != b'APK Sig Block 42'
    report['apk_signing_block'] = 'ABSENT'
    environment = os.environ.copy()
    environment['JAVA_HOME'] = str(args.java_home)
    environment['PATH'] = str(args.java_home / 'bin') + os.pathsep + environment.get('PATH', '')
    executions = []

    def run(tool, *parts):
        command = [str(args.build_tools / tool), *parts, str(args.apk)]
        result = subprocess.run(command, env=environment, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        log = args.output.with_name(args.output.stem + '-' + tool + '.txt')
        assert not log.exists()
        log.write_text(result.stdout)
        executions.append({'command': command, 'exit_code': result.returncode, 'log': str(log), 'log_sha256': sha(result.stdout.encode())})
        return result

    badging = run('aapt', 'dump', 'badging')
    assert badging.returncode == 0
    assert "package: name='at.heinz.deviceai.continuumlocal' versionCode='60' versionName='0.3.59'" in badging.stdout
    assert "sdkVersion:'26'" in badging.stdout and "targetSdkVersion:'36'" in badging.stdout
    assert "native-code: 'arm64-v8a'" in badging.stdout
    assert 'application-debuggable' not in badging.stdout
    report['badging'] = [line for line in badging.stdout.splitlines() if line.startswith(('package:', 'sdkVersion:', 'targetSdkVersion:', 'native-code:'))]
    alignment = run('zipalign', '-c', '-P', '16', '-v', '4')
    assert alignment.returncode == 0
    signing = run('apksigner', 'verify', '--verbose')
    assert signing.returncode != 0 and 'DOES NOT VERIFY' in signing.stdout
    assert git('rev-parse', 'HEAD').decode().strip() == args.source
    assert not git('status', '--porcelain')
    report.update(zip_crc='PASS', zip_unique_safe_paths='PASS', source_assets='38_BYTE_IDENTICAL',
                  zipalign_16k='PASS', signing_status='UNSIGNED_NOT_INSTALLABLE',
                  apksigner_exit=signing.returncode, commands=executions,
                  status='PASS_INDEPENDENT_LOCAL_ARTIFACT_REVIEW')
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ('source_commit', 'apk_file', 'bytes', 'sha256', 'source_assets', 'signing_status', 'status')}, indent=2))


if __name__ == '__main__':
    main()
