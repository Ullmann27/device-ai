#!/usr/bin/env python3
"""Reproduce the recorded validator self-checks."""


def main() -> int:
    from pathlib import Path
    from types import ModuleType, SimpleNamespace
    import ast
    import hashlib
    import json
    import struct
    import subprocess
    import tempfile
    import warnings
    import zipfile

    repo = Path(__file__).resolve().parents[2]
    path = repo / 'tools/code55-apk/validator.py'
    text = path.read_text()
    module = ModuleType('code55_validator_check')
    module.__file__ = str(path)
    exec(compile(text, str(path), 'exec'), module.__dict__)
    checks = []

    def good(label, operation):
        operation()
        checks.append({'case': label, 'result': 'PASS'})

    def rejected(label, operation, reason):
        try:
            operation()
        except module.Rejected as error:
            if reason not in str(error):
                raise AssertionError(f'{label}: unexpected rejection {error}')
        else:
            raise AssertionError(f'{label}: accepted')
        checks.append({'case': label, 'result': 'PASS'})

    baseline = repo / 'tools/code55-apk/baselines/code53-validator.py'
    raw = baseline.read_bytes()
    blob = hashlib.sha1(b'blob ' + str(len(raw)).encode('ascii') + b'\0' + raw).hexdigest()
    if blob != '90b8dcba60a62bf52b2d8fe1530d19f841328256':
        raise AssertionError('Historical validator baseline changed')
    original = raw.decode('utf-8')
    old_ast, new_ast = ast.parse(original), ast.parse(text)
    functions = ('require', 'digest_stream', 'digest_file', 'write_json', 'run', 'check_elf', 'check_unsigned_diagnostic', 'check_archive')
    for name in functions:
        old = next(node for node in old_ast.body if isinstance(node, ast.FunctionDef) and node.name == name)
        new = next(node for node in new_ast.body if isinstance(node, ast.FunctionDef) and node.name == name)
        if ast.get_source_segment(original, old) != ast.get_source_segment(text, new):
            raise AssertionError(f'Inherited gate changed: {name}')
    checks.append({'case': 'eight_inherited_gate_functions_byte_identical', 'result': 'PASS'})
    for name in ('PROTECTED_SOURCE', 'EXPECTED_PACKAGE', 'EXPECTED_LIBRARIES'):
        def value(tree):
            return next(ast.literal_eval(node.value) for node in tree.body if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == name for target in node.targets))
        if value(old_ast) != value(new_ast):
            raise AssertionError(f'Inherited constant changed: {name}')
    checks.append({'case': 'avatar_package_native_pins_unchanged', 'result': 'PASS'})

    badging = "package: name='at.heinz.deviceai.continuumlocal' versionCode='55' versionName='0.3.54'\nsdkVersion:'26'\ntargetSdkVersion:'36'\nnative-code: 'arm64-v8a'\n"
    good('correct_code55_badging', lambda: module.check_badging(badging))
    for label, broken, reason in (
        ('old_version_rejected', badging.replace("'55'", "'53'").replace('0.3.54', '0.3.52'), 'package/version'),
        ('qa_package_rejected', badging.replace("continuumlocal'", "continuumlocal.qa'"), 'package/version'),
        ('extra_abi_rejected', badging.replace("'arm64-v8a'", "'arm64-v8a' 'x86_64'"), 'native-code'),
        ('debuggable_rejected', badging + 'application-debuggable\n', 'debuggable'),
        ('wrong_min_sdk_rejected', badging.replace("sdkVersion:'26'", "sdkVersion:'25'"), 'minSdk'),
        ('wrong_target_sdk_rejected', badging.replace("targetSdkVersion:'36'", "targetSdkVersion:'35'"), 'targetSdk'),
    ):
        rejected(label, lambda broken=broken: module.check_badging(broken), reason)

    elf = bytearray(256)
    elf[:7] = b'\x7fELF\x02\x01\x01'
    struct.pack_into('<HH', elf, 16, 3, 183)
    struct.pack_into('<Q', elf, 32, 64)
    struct.pack_into('<HH', elf, 54, 56, 1)
    struct.pack_into('<I', elf, 64, 1)
    struct.pack_into('<QQ', elf, 72, 0, 0)
    struct.pack_into('<QQQ', elf, 96, 256, 256, 16384)
    good('minimal_elf_control', lambda: module.check_elf(bytes(elf), 'fixture.so'))
    for label, offset, fmt, value, reason in (
        ('elf32_rejected', 4, 'B', 1, 'ELF64'),
        ('x86_elf_rejected', 18, '<H', 62, 'AArch64'),
        ('executable_elf_rejected', 16, '<H', 2, 'ET_DYN'),
        ('elf_4k_alignment_rejected', 112, '<Q', 4096, '16 KiB'),
        ('elf_non_power_alignment_rejected', 112, '<Q', 24576, '16 KiB'),
        ('elf_incongruent_segment_rejected', 72, '<Q', 1, 'congruence'),
        ('elf_file_overflow_rejected', 96, '<Q', 257, 'exceeds file'),
        ('elf_program_table_rejected', 56, '<H', 129, 'program table'),
        ('elf_without_load_rejected', 64, '<I', 2, 'no load segments'),
    ):
        data = bytearray(elf)
        struct.pack_into(fmt, data, offset, value)
        rejected(label, lambda data=data: module.check_elf(bytes(data), 'fixture.so'), reason)

    unsigned = 'DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF\n'
    good('explicit_unsigned_control', lambda: module.check_unsigned_diagnostic(1, unsigned))
    for label, rc, diagnostic, reason in (
        ('unsigned_tool_failure_rejected', 2, unsigned, 'did not prove'),
        ('unsigned_arbitrary_error_rejected', 1, unsigned + 'ERROR: other corruption\n', 'did not prove'),
        ('unsigned_exception_rejected', 1, unsigned + 'Exception: invalid archive\n', 'malformed'),
        ('signed_apk_rejected', 0, 'Verifies\n', 'did not prove'),
        ('verified_signature_rejected', 1, unsigned + 'Verified using v2 scheme: true\n', 'verified signature'),
    ):
        rejected(label, lambda rc=rc, diagnostic=diagnostic: module.check_unsigned_diagnostic(rc, diagnostic), reason)

    expected_source, expected_tree, avatar_blob = '1' * 40, '2' * 40, '3' * 40
    real_run = module.run
    responses = {('rev-parse', 'HEAD'): expected_source,
                 ('rev-parse', 'HEAD^{tree}'): expected_tree,
                 ('status', '--porcelain', '--untracked-files=normal'): '',
                 ('rev-parse', module.PROTECTED_SOURCE + ':app/src/main/assets/avatar3d/continuum-avatar.glb'): avatar_blob,
                 ('rev-parse', 'HEAD:app/src/main/assets/avatar3d/continuum-avatar.glb'): avatar_blob}
    def git_fixture(command, **kwargs):
        if command[0] != 'git':
            raise AssertionError('Unexpected command')
        return SimpleNamespace(returncode=0, stdout=responses[tuple(command[1:])])
    module.run = git_fixture
    try:
        good('exact_clean_source_mock_control', lambda: module.source_identity(repo, {}, expected_source, expected_tree))
        for label, key, value, reason in (
            ('wrong_source_sha_rejected', ('rev-parse', 'HEAD'), '4' * 40, 'exact clean'),
            ('wrong_source_tree_rejected', ('rev-parse', 'HEAD^{tree}'), '4' * 40, 'exact clean'),
            ('dirty_source_rejected', ('status', '--porcelain', '--untracked-files=normal'), ' M file.kt', 'exact clean'),
            ('untracked_source_rejected', ('status', '--porcelain', '--untracked-files=normal'), '?? new.file', 'exact clean'),
            ('changed_original_avatar_rejected', ('rev-parse', 'HEAD:app/src/main/assets/avatar3d/continuum-avatar.glb'), '4' * 40, 'Original avatar'),
        ):
            previous = responses[key]
            responses[key] = value
            rejected(label, lambda: module.source_identity(repo, {}, expected_source, expected_tree), reason)
            responses[key] = previous
    finally:
        module.run = real_run

    with tempfile.TemporaryDirectory(prefix='validator-check-', dir=path.parent) as temporary:
        scratch = Path(temporary)
        fixture_repo = scratch / 'source'
        source = fixture_repo / 'app/src/main/assets'
        generated = fixture_repo / 'app/build/generated/owner-face-model-assets'
        assets = {'avatar3d/continuum-avatar.glb': b'original-avatar', 'nested/required.asset': b'required'}
        generated_assets = {'faceid/model-manifest.json': b'{}', 'faceid/yunet.onnx': b'yunet', 'faceid/sface.onnx': b'sface'}
        for directory, contents in ((source, assets), (generated, generated_assets)):
            for name, data in contents.items():
                target = directory / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
        lock = {'models': [{'id': key, 'filename': key + '.onnx', 'byteLength': len(generated_assets['faceid/' + key + '.onnx']), 'sha256': hashlib.sha256(generated_assets['faceid/' + key + '.onnx']).hexdigest()} for key in ('yunet', 'sface')]}
        lock_path = fixture_repo / 'app/owner-face-models.lock.json'
        lock_path.write_text(json.dumps(lock))
        entries = {'AndroidManifest.xml': b'manifest', 'resources.arsc': b'resources', 'classes.dex': b'dex\n'}
        entries.update({name: bytes(elf) for name in module.EXPECTED_LIBRARIES})
        entries.update({'assets/' + name: data for name, data in (assets | generated_assets).items()})
        def archive(name, content=None, extra=None):
            apk = scratch / (name + '.apk')
            with zipfile.ZipFile(apk, 'w', compression=zipfile.ZIP_STORED) as zipped:
                for entry, data in (entries if content is None else content).items():
                    zipped.writestr(entry, data)
                if extra:
                    with warnings.catch_warnings():
                        warnings.simplefilter('ignore', UserWarning)
                        zipped.writestr(*extra)
            out = scratch / (name + '-report')
            out.mkdir()
            return apk, out
        def verify(pair):
            return module.check_archive(pair[0], fixture_repo, pair[1])
        good('archive_gate_fixture_control', lambda: verify(archive('control')))
        for label, extra, reason in (
            ('duplicate_zip_rejected', ('classes.dex', b'dex\n'), 'Duplicate'),
            ('traversal_zip_rejected', ('../outside', b'bad'), 'Unsafe'),
            ('jar_signature_rejected', ('META-INF/CERT.RSA', b'bad'), 'JAR signing'),
        ):
            pair = archive(label, extra=extra)
            rejected(label, lambda pair=pair: verify(pair), reason)
        link = zipfile.ZipInfo('symlink')
        link.create_system = 3
        link.external_attr = 0o120777 << 16
        pair = archive('zip_symlink', extra=(link, b'classes.dex'))
        rejected('zip_symlink_rejected', lambda: verify(pair), 'symlink')
        for label, missing, reason in (
            ('missing_asset_rejected', 'assets/nested/required.asset', 'Missing packaged asset'),
            ('missing_avatar_asset_rejected', 'assets/avatar3d/continuum-avatar.glb', 'Missing packaged asset'),
            ('missing_native_library_rejected', 'lib/arm64-v8a/libfilament-jni.so', 'Native inventory'),
        ):
            reduced = dict(entries)
            reduced.pop(missing)
            pair = archive(label, content=reduced)
            rejected(label, lambda pair=pair: verify(pair), reason)
        altered = dict(entries)
        altered['assets/nested/required.asset'] = b'changed!'
        pair = archive('altered_asset', content=altered)
        rejected('altered_asset_hash_rejected', lambda: verify(pair), 'differs from source')
        wrong_lock = json.loads(lock_path.read_text())
        wrong_lock['models'][0]['sha256'] = '0' * 64
        lock_path.write_text(json.dumps(wrong_lock))
        pair = archive('wrong_lock')
        rejected('locked_model_hash_rejected', lambda: verify(pair), 'Locked model hash')
        lock_path.write_text(json.dumps(lock))
        pair = archive('crc')
        with zipfile.ZipFile(pair[0]) as zipped:
            member = zipped.getinfo('classes.dex')
            offset = member.header_offset + 30 + len(member.filename.encode()) + len(member.extra)
        damaged = bytearray(pair[0].read_bytes())
        damaged[offset] ^= 1
        pair[0].write_bytes(damaged)
        rejected('corrupt_crc_rejected', lambda: verify(pair), 'CRC verification')
        pair = archive('signing_block')
        raw = bytearray(pair[0].read_bytes())
        with zipfile.ZipFile(pair[0]) as zipped:
            central = zipped.start_dir
        eocd = raw.rfind(b'PK\x05\x06')
        raw[central:central] = b'APK Sig Block 42'
        struct.pack_into('<I', raw, eocd + 16 + 16, central + 16)
        pair[0].write_bytes(raw)
        rejected('apk_signing_block_rejected', lambda: verify(pair), 'signing block')
        common = ['python3', str(path), str(scratch / 'missing.apk'), str(scratch / 'cli-report'), '--repo', str(repo), '--build-tools', str(scratch), '--java-home', str(scratch)]
        cli_cases = (
            ('mandatory_source_and_tree_cli', [], '--expected-source'),
            ('mandatory_tree_cli', ['--expected-source', expected_source], '--expected-tree'),
            ('symbolic_source_cli_rejected', ['--expected-source', 'HEAD', '--expected-tree', expected_tree], '40-character'),
            ('short_tree_cli_rejected', ['--expected-source', expected_source, '--expected-tree', 'abcdef0'], '40-character'),
            ('missing_apk_cli_rejected', ['--expected-source', expected_source, '--expected-tree', expected_tree], 'existing regular APK'),
        )
        for label, arguments, marker in cli_cases:
            result = subprocess.run(common + arguments, capture_output=True, text=True)
            if result.returncode != 2 or marker not in result.stderr:
                raise AssertionError(f'{label}: {result.returncode} {result.stderr}')
            checks.append({'case': label, 'result': 'PASS'})
        if (scratch / 'cli-report').exists():
            raise AssertionError('Invalid CLI created report files')

    print(json.dumps({'status': 'PASS', 'checks': len(checks), 'validatorSha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'cases': checks, 'actual_APK_validation': 'NOT_RUN', 'Gradle': 'NOT_RUN', 'signing': 'NOT_USED'}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
