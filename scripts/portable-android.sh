#!/usr/bin/env bash
# Android candidate build without a CI provider; never publishes or installs apps.
set -Eeuo pipefail
umask 077

usage() {
  printf 'Usage: bash scripts/portable-android.sh doctor|status|tests|preview-apk|worker-status\n' >&2
  exit 2
}
[[ "$#" -eq 1 ]] || usage
mode="$1"
case "$mode" in
  doctor|status|tests|preview-apk|worker-status) ;;
  *) usage ;;
esac
command -v git >/dev/null || { echo 'Git is missing' >&2; exit 3; }
command -v node >/dev/null || { echo 'Node.js is missing' >&2; exit 3; }
root="$(git rev-parse --show-toplevel)" || { echo 'Trusted Git checkout required' >&2; exit 3; }
cd "$root"
for file in settings.gradle.kts app/build.gradle.kts scripts/continuum-mobile-builder.mjs .github/continuum/teamleader-cli.mjs; do
  [[ -f "$file" ]] || { echo "Unexpected Continuum source; missing $file" >&2; exit 3; }
done

clean_source() {
  [[ -z "$(git status --porcelain=v1 --untracked-files=normal)" ]] || {
    echo 'STOP: checkout has uncommitted/untracked files. Preserve owner work before tests/build.' >&2
    exit 3
  }
}

run_tests() {
  local expected_head="$1"
  node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
  command -v npm >/dev/null || { echo 'npm is missing' >&2; exit 3; }
  npm --prefix backend ci --ignore-scripts --no-audit --no-fund
  node --test --test-concurrency=2 backend/test/*.test.js
  [[ "$(git rev-parse HEAD)" == "$expected_head" ]] || { echo 'STOP: Git HEAD changed during tests' >&2; exit 3; }
  git diff --quiet HEAD -- || { echo 'STOP: Tracked source changed during tests' >&2; exit 3; }
}

case "$mode" in
  doctor)
    exec node scripts/continuum-mobile-builder.mjs doctor --project "$root"
    ;;
  status)
    exec node scripts/continuum-mobile-builder.mjs status --project "$root"
    ;;
  worker-status)
    exec node .github/continuum/teamleader-cli.mjs --status --root "$root"
    ;;
  tests|preview-apk)
    clean_source
    original_head="$(git rev-parse HEAD)"
    [[ "$original_head" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid Git HEAD' >&2; exit 3; }
    run_tests "$original_head"
    if [[ "$mode" == tests ]]; then
      printf 'Continuum source and Node/backend tests passed for %s\n' "$original_head"
      exit 0
    fi
    # The existing builder owns SDK/JDK checks, Android JVM tests, lint,
    # APK signing/badging, immutable SHA copy and the separate .preview identity.
    # It deliberately cannot install or replace the Mentor Pair owner app.
    node scripts/continuum-mobile-builder.mjs build --project "$root"
    [[ "$(git rev-parse HEAD)" == "$original_head" ]] || { echo 'STOP: Source HEAD changed during APK build' >&2; exit 3; }
    git diff --quiet HEAD -- || { echo 'STOP: Tracked source changed during build' >&2; exit 3; }
    printf 'Continuum preview build finished; device installation remains owner-controlled.\n'
    ;;
esac
