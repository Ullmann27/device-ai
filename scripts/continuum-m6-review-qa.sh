#!/usr/bin/env bash
# Non-release validation of a single, pinned Device AI Continuum M6 commit.
set -Eeuo pipefail
umask 077

fail() { printf 'CONTINUUM_M6_QA_BLOCKED: %s\n' "$1" >&2; exit 69; }
root="$(git rev-parse --show-toplevel 2>/dev/null)" || fail 'not a Git checkout'
cd "$root"
branch="$(git branch --show-current)"
expected_branch="${CONTINUUM_M6_QA_EXPECTED_BRANCH:-integration/continuum-m6-all-verified-20261025}"
[[ "$expected_branch" == 'integration/continuum-m6-all-verified-20261025' ||
   "$expected_branch" =~ ^research/continuum-omega-[a-z0-9-]+$ ]] || fail 'unexpected branch'
[[ "$branch" == "$expected_branch" ]] || fail 'unexpected branch'
head="$(git rev-parse --verify HEAD)"
expected="${CONTINUUM_M6_QA_EXPECTED_SHA:-}"
[[ "$expected" =~ ^[a-f0-9]{40}$ && "$head" == "$expected" ]] || fail 'missing or mismatched pinned source SHA'
[[ -z "$(git status --porcelain)" ]] || fail 'source checkout must start clean'
[[ -d "$HOME" ]] || fail 'HOME is unavailable'
home_real="$(cd "$HOME" && pwd -P)"
[[ "$home_real" != "$root" && "$home_real" != "$root/"* ]] || fail 'state home is inside source'
out="$home_real/.local/state/device-ai-continuum/m6-review-qa/$head"
mkdir -p "$out"
printf 'source_sha=%s\nbranch=%s\nowner_apk_signed=false\nrelease=false\n' "$head" "$branch" > "$out/RESULT.txt"
trap 'code=$?; printf "exit_code=%s\n" "$code" >> "$out/RESULT.txt"; exit "$code"' EXIT

run_step() {
  local name="$1"; shift
  printf 'QA: %s\n' "$name"
  if "$@" > "$out/$name.log" 2>&1; then
    printf '%s=PASS\n' "$name" >> "$out/RESULT.txt"
  else
    local code=$?
    printf '%s=FAIL(exit=%s)\n' "$name" "$code" >> "$out/RESULT.txt"
    printf 'QA: %s failed; see %s/%s.log\n' "$name" "$out" "$name" >&2
    return "$code"
  fi
}

command -v node >/dev/null || fail 'Node 20+ required'
major="$(node -p 'Number(process.versions.node.split(".")[0])')"
((major >= 20)) || fail 'Node 20+ required'
for file in scripts/continuum-local-brain.mjs scripts/continuum-local-brain-cli.mjs \
 .github/continuum/coding-provider.mjs .github/continuum/coding-orchestrator.mjs \
 scripts/continuum-typed-coding-bridge.mjs; do
  run_step "syntax-$(basename "$file")" node --check "$file"
done
run_step node-targeted env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 \
 .github/continuum/coding-provider.test.mjs \
 .github/continuum/coding-orchestrator.test.mjs \
 .github/continuum/coding-rejection-feedback.test.mjs \
 scripts/continuum-typed-coding-bridge.test.mjs
run_step node-full env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 \
 .github/continuum/*.test.mjs scripts/*.test.mjs
# Research-only security checks; no network calls or owner device actions.
run_step node-omega-security env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 \
 experimental/omega/security/*.test.mjs
command -v npm >/dev/null || fail 'npm required for backend checks'
run_step backend-install-offline npm --prefix backend ci --offline --ignore-scripts --no-audit --no-fund
run_step backend-tests env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 backend/test/*.test.js

if [[ "${CONTINUUM_M6_QA_ANDROID:-0}" == '1' ]]; then
  [[ -n "${JAVA_HOME:-}" && -n "${ANDROID_HOME:-}" ]] || fail 'JAVA_HOME and ANDROID_HOME required'
  # This repository has no Gradle wrapper. Use the already installed toolchain;
  # never download or assemble an APK from this non-release runner.
  command -v gradle >/dev/null || fail 'installed Gradle required'
  run_step android-jvm-lint-compile gradle --offline --no-daemon \
    :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin
else
  printf 'android=NOT_RUN(opt_in_required)\n' >> "$out/RESULT.txt"
fi
[[ -z "$(git status --porcelain)" ]] || fail 'tests modified tracked/unignored repository files'
printf 'CONTINUUM_M6_QA_CHECKS_PASSED=%s\n' "$head" >> "$out/RESULT.txt"
printf 'QA complete. Results: %s/RESULT.txt\n' "$out"
