"""Synthetic negative tests only. No research model or finalized dataset is run."""
import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED, ZipInfo

from verify_archive import IntegrityError, verify_bundle


class ArchiveVerificationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.file = Path(self.tmp.name) / 'research.zip'

    def bundle(self, files=None, manifest=None, extra=None, duplicate=None):
        files = files if files is not None else {'models/dummy.lm1': b'not-a-model'}
        records = {key: {'bytes': len(value), 'sha256': hashlib.sha256(value).hexdigest()}
                   for key, value in files.items()}
        manifest = manifest if manifest is not None else {'files': records}
        with ZipFile(self.file, 'w', ZIP_STORED) as bundle:
            bundle.writestr('project/MANIFEST.json', json.dumps(manifest))
            for path, value in files.items():
                bundle.writestr('project/' + path, value)
            if extra:
                bundle.writestr(extra, b'unregistered')
            if duplicate:
                bundle.writestr('project/' + duplicate, b'duplicate')
        return hashlib.sha256(self.file.read_bytes()).hexdigest()

    def reject(self, digest=None):
        with self.assertRaises(IntegrityError):
            verify_bundle(self.file, digest or hashlib.sha256(self.file.read_bytes()).hexdigest())

    def test_clean(self):
        digest = self.bundle()
        self.assertEqual(verify_bundle(self.file, digest)['registeredEntries'], 1)

    def test_archive_digest_changed(self):
        digest = self.bundle()
        self.file.write_bytes(self.file.read_bytes() + b'altered')
        self.reject(digest)

    def test_tampered_checkpoint(self):
        registered = {'models/dummy.lm1': b'original'}
        manifest = {'files': {'models/dummy.lm1': {'bytes': 8, 'sha256': hashlib.sha256(b'original').hexdigest()}}}
        self.bundle({'models/dummy.lm1': b'corrupt!'}, manifest)
        self.reject()

    def test_missing_registered_checkpoint(self):
        self.bundle({}, {'files': {'models/missing.lm1': {'bytes': 1, 'sha256': '0' * 64}}})
        self.reject()

    def test_extra_undeclared_payload(self):
        self.bundle(extra='project/unexpected.bin')
        self.reject()

    def test_duplicate_member(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            self.bundle(duplicate='models/dummy.lm1')
        self.reject()

    def test_zip_slip(self):
        self.bundle(extra='../escape.txt')
        self.reject()

    def test_windows_path(self):
        self.bundle(extra='project\\escape.txt')
        self.reject()

    def test_wrong_manifest_bytes(self):
        files = {'models/dummy.lm1': b'abc'}
        self.bundle(files, {'files': {'models/dummy.lm1': {'bytes': 4, 'sha256': hashlib.sha256(b'abc').hexdigest()}}})
        self.reject()

    def test_duplicate_json_key(self):
        with ZipFile(self.file, 'w') as bundle:
            bundle.writestr('MANIFEST.json', '{"files":{"a":{"sha256":"' + '0'*64 + '","bytes":1}},"files":{}}')
            bundle.writestr('a', b'x')
        self.reject()

    def test_contradictory_model_map(self):
        payload = b'x'
        manifest = {'files': {'models/dummy.lm1': {'bytes': 1, 'sha256': hashlib.sha256(payload).hexdigest()}},
                    'models': {'W1': {'path': 'models/dummy.lm1', 'bytes': 1, 'sha256': '0' * 64}}}
        self.bundle({'models/dummy.lm1': payload}, manifest)
        self.reject()

    def test_missing_expected_digest(self):
        digest = self.bundle()
        self.reject('a')

    def test_symlink_member(self):
        payload = b'link-target'
        sha = hashlib.sha256(payload).hexdigest()
        manifest = {'files': {'shortcut': {'bytes': len(payload), 'sha256': sha}}}
        with ZipFile(self.file, 'w') as bundle:
            bundle.writestr('MANIFEST.json', json.dumps(manifest))
            link = ZipInfo('shortcut')
            link.create_system = 3
            link.external_attr = 0o120777 << 16
            bundle.writestr(link, payload)
        self.reject()


if __name__ == '__main__':
    unittest.main()
