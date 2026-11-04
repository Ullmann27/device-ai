"""Prepare this round's isolated local cache; never write the donor cache."""
from __future__ import annotations

import datetime
import fcntl
import hashlib
import json
import pathlib
import shutil


DONOR = pathlib.Path('/workspace/scratch/822e72396ed9/build-env')
TARGET = pathlib.Path('/workspace/scratch/7dcf5d78f2cb/round2-android-env')
RESERVE_BYTES = 1024 * 1024 * 1024


def inventory(root: pathlib.Path) -> dict[str, tuple[int, int]]:
    return {
        str(path.relative_to(root)): (path.stat().st_size, path.stat().st_mtime_ns)
        for path in root.rglob('*')
        if path.is_file() and not path.name.endswith('.lock') and path.name != 'gc.properties'
    }


def digest(path: pathlib.Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    source = DONOR / 'gradle-cache/caches'
    target = TARGET / 'gradle-cache/caches'
    TARGET.mkdir(exist_ok=True)
    with (DONOR / 'gradle-build.lock').open('rb') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_SH | fcntl.LOCK_NB)
        before = inventory(source)
        existing = inventory(target)
        remaining = sum(size for name, (size, mtime) in before.items()
                        if existing.get(name) != (size, mtime))
        free_before = shutil.disk_usage(TARGET).free
        if free_before < remaining + RESERVE_BYTES:
            raise RuntimeError(f'Insufficient budget: free={free_before}, '
                               f'remaining={remaining}, reserve={RESERVE_BYTES}')

        def copy(src: str, dst: str) -> str:
            source_path, target_path = pathlib.Path(src), pathlib.Path(dst)
            if target_path.is_file():
                current, wanted = target_path.stat(), source_path.stat()
                if (current.st_size, current.st_mtime_ns) == (wanted.st_size, wanted.st_mtime_ns):
                    return str(target_path)
            if shutil.disk_usage(TARGET).free - source_path.stat().st_size < RESERVE_BYTES:
                raise RuntimeError('Snapshot reserve exhausted; copy stopped.')
            return shutil.copy2(src, dst)

        shutil.copytree(source, target, dirs_exist_ok=True, copy_function=copy,
                        ignore=shutil.ignore_patterns('*.lock', 'gc.properties'))
        after = inventory(source)
        copied = inventory(target)
        if before != after or before != copied:
            raise RuntimeError('Source changed or destination inventory differs.')
        if any((source / name).stat().st_ino == (target / name).stat().st_ino and
               (source / name).stat().st_dev == (target / name).stat().st_dev
               for name in before):
            raise RuntimeError('Snapshot must not share file inodes with donor.')
        agp = pathlib.Path('modules-2/files-2.1/com.android.tools.build/gradle/8.10.0/'
                           '17bb1a87438ee832f8902c84b9791adf196d904d/gradle-8.10.0.jar')
        source_agp_hash, target_agp_hash = digest(source / agp), digest(target / agp)
        if source_agp_hash != target_agp_hash:
            raise RuntimeError('AGP artifact hash mismatch.')
        for name in ('jdk17', 'gradle-8.11.1', 'android-sdk'):
            path = TARGET / name
            if not path.exists():
                path.symlink_to((DONOR / name).resolve(), target_is_directory=True)
        for name in ('android-user', 'tmp', 'logs'):
            (TARGET / name).mkdir(exist_ok=True)
        (TARGET / 'gradle-build.lock').touch(exist_ok=True)
        receipt = {
            'createdAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'donor': str(source), 'destination': str(target),
            'donorLock': 'shared flock; existing launcher lock opened rb',
            'copiedFiles': len(before),
            'copiedFileBytes': sum(size for size, _ in before.values()),
            'remainingFileBytesAtResume': remaining,
            'manifestSha256': hashlib.sha256(json.dumps(before, sort_keys=True).encode()).hexdigest(),
            'manifestScope': 'Relative file names, sizes and mtime_ns; not every file content hash.',
            'donorManifestStable': before == after,
            'destinationManifestMatches': before == copied,
            'agpSha256': source_agp_hash, 'agpCopyHashMatches': True,
            'excluded': ['*.lock', 'gc.properties'],
            'freeBeforeBytes': free_before, 'freeAfterBytes': shutil.disk_usage(TARGET).free,
            'minimumCopyReserveBytes': RESERVE_BYTES,
            'copiesUseHardlinks': False,
            'readOnlyToolchainReferences': {name: str((TARGET / name).resolve())
                                           for name in ('jdk17', 'gradle-8.11.1', 'android-sdk')},
            'gradleStarted': False,
            'previousAttempt': 'Stopped automatically at 1250 MiB reserve; own historical '
                               'evidence sparsified before resuming with 1024 MiB reserve.'
        }
        (TARGET / 'snapshot-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
