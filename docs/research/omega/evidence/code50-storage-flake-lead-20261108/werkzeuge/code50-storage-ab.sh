#!/bin/bash
# Aufruf: code50-storage-ab.sh <Repository-Verzeichnis> <Ausgabe-XML>
# Führt nur das storage-agent-Gate (3 Testdateien, --test-concurrency=2 wie im Harness) einmal aus,
# schreibt JUnit-XML und gibt den Exit-Code von node --test aus.
# Funktional identisch mit der für die Stress-A/B-Läufe verwendeten Version (nur Kommentare ergänzt).
cd "$1" || exit 99
export PATH="/tmp/node24/node_modules/node/bin:$PATH" CONTINUUM_QA_DEPTH=1
node --test --test-concurrency=2 --test-reporter=junit --test-reporter-destination="$2" \
  .github/continuum/storage-fabric.test.mjs .github/continuum/coding-agent-loop.test.mjs \
  scripts/continuum-architecture-proof.test.mjs >/dev/null 2>&1
echo $?
