#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
cd "$(git rev-parse --show-toplevel)"
head_sha="$(git rev-parse HEAD)"
[[ "$head_sha" =~ ^[a-f0-9]{40}$ ]] || { echo "Invalid git source"; exit 69; }
[[ -n "${CONTINUUM_NATIVE_QA_SHA:-}" && "$head_sha" == "$CONTINUUM_NATIVE_QA_SHA" ]] ||
  { echo "Unapproved or stale native corpus source"; exit 69; }
test -s scripts/continuum-native-corpus.mjs
test -s scripts/continuum-native-corpus.test.mjs
mkdir -p native-corpus-qa
node --test scripts/continuum-native-corpus.test.mjs > native-corpus-qa/TESTS.txt 2>&1 ||
  { cat native-corpus-qa/TESTS.txt; exit 69; }
{
  printf 'mode=ISOLATED_NATIVE_CORPUS_QA_NO_MODEL_NO_APK\n'
  printf 'source_sha=%s\n' "$head_sha"
  printf 'tests=PASS\n'
  printf 'signing=NOT_AVAILABLE\n'
  printf 'training=NOT_RUN\n'
} > native-corpus-qa/STATUS.txt
printf 'CONTINUUM_NATIVE_CORPUS_QA_PASSED=%s\n' "$head_sha"
