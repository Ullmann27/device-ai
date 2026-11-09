"""Audit: harte Kurz-Timeouts (< 1000 ms) in .github/continuum/*.test.mjs und scripts/*.test.mjs (nur Liste, read-only).

Aufruf: audit_timeouts.py <repo-wurzel>
"""
import glob
import os
import re
import sys

root = sys.argv[1]
files = sorted(glob.glob(os.path.join(root, ".github/continuum/*.test.mjs")) + glob.glob(os.path.join(root, "scripts/*.test.mjs")))
num = r"(\d[\d_]*)"
patterns = {
    "timeoutMs": re.compile(r"timeoutMs\s*[:=]\s*" + num),
    "timeout-Option": re.compile(r"\btimeout\s*:\s*" + num),
    "setTimeout": re.compile(r"setTimeout\([^;\n]*?,\s*" + num + r"\s*\)"),
    "AbortSignal.timeout": re.compile(r"AbortSignal\.timeout\(\s*" + num),
    "req.setTimeout": re.compile(r"\.setTimeout\(\s*" + num),
    "test-timeout-Flag": re.compile(r"--test-timeout[= ]" + num),
    "Dauer-Assertion": re.compile(r"(?:elapsed|duration|Date\.now\(\)\s*-\s*\w+)[^;\n]{0,25}?[<>]=?\s*" + num),
    "wait/sleep/delay": re.compile(r"\b(?:sleep|delay|wait[A-Za-z]*)\(\s*" + num + r"\s*[,)]"),
}
hits, scanned = [], 0
for path in files:
    scanned += 1
    rel = os.path.relpath(path, root)
    with open(path, encoding="utf-8", errors="replace") as handle:
        for lineno, line in enumerate(handle, 1):
            for name, rx in patterns.items():
                for m in rx.finditer(line):
                    value = int(m.group(1).replace("_", ""))
                    if value < 1000:
                        hits.append((rel, lineno, name, value, line.strip()[:150]))
print(f"gescannte Dateien: {scanned}; Treffer < 1000: {len(hits)}")
for rel, lineno, name, value, text in hits:
    print(f"{rel}:{lineno} [{name}={value}] {text}")
