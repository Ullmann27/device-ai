#!/usr/bin/env python3
"""Offline, source-bound Kotlin smoke check of two production index methods.

This tests index ownership/aliasing only. GermanText, terms, grams and vec are
test stubs. It is NOT a full Gradle, retrieval-ranking, Android or device test.
"""
import argparse
import hashlib
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
TEMPLATE = pathlib.Path(__file__).with_name("HostTemplate.kt")
FINAL_FNV = {"Bm25GermanRetriever": "ee11dd94", "CharNgramHashRetriever": "fc6e1a8a"}

def extract(source: str, class_name: str) -> str:
    cls = source.index("class " + class_name)
    start = source.index("override fun index(docs:List<MemoryDoc>) {", cls)
    opening = source.index("{", start)
    depth = 0
    for pos in range(opening, len(source)):
        if source[pos] == "{":
            depth += 1
        elif source[pos] == "}":
            depth -= 1
            if depth == 0:
                return source[start:pos + 1]
    raise ValueError("unterminated method: " + class_name)

def fnv32(value: str) -> str:
    h = 2166136261
    for char in value:
        h = ((h ^ ord(char)) * 16777619) & 0xFFFFFFFF
    return f"{h:08x}"

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, default=SOURCE)
    parser.add_argument("--allow-other-source", action="store_true", help="frozen RED sources only")
    args = parser.parse_args()
    source_bytes = args.source.read_bytes()
    source = source_bytes.decode("utf-8")
    print(f"SOURCE={args.source} SHA256={hashlib.sha256(source_bytes).hexdigest()}", flush=True)
    code = TEMPLATE.read_text()
    for cls, slot in [("Bm25GermanRetriever", "__BM25_INDEX__"),
                      ("CharNgramHashRetriever", "__NGRAM_INDEX__")]:
        method = extract(source, cls)
        check = fnv32(method)
        print(f"INDEX {cls} chars={len(method)} FNV32={check}", flush=True)
        if not args.allow_other_source and check != FINAL_FNV[cls]:
            raise ValueError("unexpected production index method: " + cls)
        code = code.replace(slot, method)
    with tempfile.TemporaryDirectory(prefix="memory-r6-host-") as tmp:
        work = pathlib.Path(tmp)
        kotlin = work / "SourceMethodHost.kt"
        kotlin.write_text(code)
        jar = work / "index-check.jar"
        compile_result = subprocess.run(["kotlinc", str(kotlin), "-include-runtime", "-d", str(jar)],
                                        timeout=90, capture_output=True, text=True)
        print("KOTLIN_COMPILE_EXIT=" + str(compile_result.returncode), flush=True)
        if compile_result.stdout:
            print(compile_result.stdout, flush=True)
        if compile_result.stderr:
            print(compile_result.stderr, file=sys.stderr, flush=True)
        if compile_result.returncode:
            return compile_result.returncode
        result = subprocess.run(["java", "-jar", str(jar)], timeout=45, capture_output=True, text=True)
        print(result.stdout, end="", flush=True)
        if result.stderr:
            print(result.stderr, file=sys.stderr, flush=True)
        print("KOTLIN_RUNTIME_EXIT=" + str(result.returncode), flush=True)
        return result.returncode

if __name__ == "__main__":
    raise SystemExit(main())
