#!/usr/bin/env python3
"""ARGON4 F2 - measure what storage really delivers for weight streaming (run it on the Fold7 in Termux and on the PC).

Patterns that matter for an LLM whose weights do not fit in RAM:
  seq_8m     large sequential reads (a whole layer or an expert block in file order)
  expert_22m random reads of expert-sized chunks (a DeepSeek-V3 routed expert is ~22 MB at 4 bit)
  rand_4k    random 4 KiB reads (what naive mmap page faults on scattered rows look like)
  mmap_touch random page faults through mmap (the "virtual memory" path)

The page cache is dropped for the test file with posix_fadvise(DONTNEED) before each pattern (Linux/Android). If the
kernel ignores the hint, the numbers are cache speeds; the script reports a cache-warm control so this is visible.
No owner data: the file is random bytes in a temporary directory and is deleted afterwards.

Usage: python3 -I scripts/argon4/stream_bench.py [size_mib=2048] [out.json]
"""
import json
import mmap
import os
import random
import sys
import tempfile
import time


def drop(fd: int) -> None:
    if hasattr(os, "posix_fadvise"):
        os.fsync(fd)
        os.posix_fadvise(fd, 0, 0, os.POSIX_FADV_DONTNEED)


def timed(fn) -> float:
    start = time.perf_counter()
    fn()
    return time.perf_counter() - start


def bench(size_mib: int = 2048, seed: int = 7) -> dict:
    rnd = random.Random(seed)
    size = size_mib * 1024 * 1024
    out = {"size_mib": size_mib, "platform": os.uname().sysname + " " + os.uname().machine,
           "note": "host measurement; repeat on the Fold7 (Termux) before using any number for planning"}
    with tempfile.TemporaryDirectory(prefix="argon4-stream-") as d:
        path = os.path.join(d, "weights.bin")
        chunk = os.urandom(8 * 1024 * 1024)
        with open(path, "wb") as f:
            for _ in range(size // len(chunk)):
                f.write(chunk)
            f.flush(); os.fsync(f.fileno())
        fd = os.open(path, os.O_RDONLY)
        try:
            def seq():
                pos = 0
                while pos < size:
                    os.pread(fd, 8 * 1024 * 1024, pos); pos += 8 * 1024 * 1024
            drop(fd); t = timed(seq); out["seq_8m_gbps"] = round(size / t / 1e9, 3)
            t_warm = timed(seq); out["seq_8m_warm_cache_gbps"] = round(size / t_warm / 1e9, 3)

            expert = 22 * 1024 * 1024
            offsets = [rnd.randrange(0, size - expert) for _ in range(min(48, size // expert))]
            drop(fd); t = timed(lambda: [os.pread(fd, expert, o) for o in offsets])
            out["expert_22m_gbps"] = round(len(offsets) * expert / t / 1e9, 3)

            n4 = 20_000
            offs4 = [rnd.randrange(0, size // 4096) * 4096 for _ in range(n4)]
            drop(fd); t = timed(lambda: [os.pread(fd, 4096, o) for o in offs4])
            out["rand_4k_gbps"] = round(n4 * 4096 / t / 1e9, 4)
            out["rand_4k_iops"] = round(n4 / t)

            mm = mmap.mmap(fd, size, prot=mmap.PROT_READ)
            try:
                pages = [rnd.randrange(0, size // 4096) * 4096 for _ in range(n4)]
                drop(fd)
                t = timed(lambda: sum(mm[p] for p in pages))
                out["mmap_touch_gbps"] = round(n4 * 4096 / t / 1e9, 4)
                out["mmap_faults_per_s"] = round(n4 / t)
            finally:
                mm.close()
        finally:
            os.close(fd)
    return out


def main() -> None:
    size = int(sys.argv[1]) if len(sys.argv) > 1 else 2048
    result = bench(size)
    if len(sys.argv) > 2:
        with open(sys.argv[2], "w", encoding="utf-8") as f:
            f.write(json.dumps(result, indent=1) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
