#!/usr/bin/env bash
# Reproducible, non-CI M4 gate. Requires a clean, complete Git checkout.
set -Eeuo pipefail

usage() {
  printf 'Usage: %s <expected-40-character-Git-SHA>\n' "$0" >&2
  exit 64
}
(( $# == 1 )) || usage
expected_sha="$1"
[[ "$expected_sha" =~ ^[0-9a-f]{40}$ ]] || usage

fail() { printf 'BLOCKED: %s\n' "$*" >&2; exit 69; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
root="$(cd -- "$script_dir/.." && pwd -P)"
for tool in git node java gradle realpath; do
  command -v "$tool" >/dev/null 2>&1 || fail "Missing executable: $tool"
done
[[ "$(git -C "$root" rev-parse --show-toplevel 2>/dev/null)" == "$root" ]] || fail 'Script must be inside a Git checkout root/scripts directory'
actual_sha="$(git -C "$root" rev-parse --verify HEAD 2>/dev/null)" || fail 'Cannot determine Git HEAD'
[[ "$actual_sha" == "$expected_sha" ]] || fail "HEAD mismatch: expected $expected_sha, found $actual_sha"
[[ -z "$(git -C "$root" status --porcelain 2>/dev/null)" ]] || fail 'Checkout is not clean'
[[ -f "$root/.github/continuum/m4-local-gate.mjs" && -d "$root/backend/test" ]] || fail 'Incomplete Continuum checkout'

# Gradle can use JAVA_HOME even when java from PATH is a different JDK.
java_executable="$(command -v java)"
if [[ -n "${JAVA_HOME:-}" ]]; then
  [[ -x "$JAVA_HOME/bin/java" ]] || fail 'JAVA_HOME/bin/java not executable'
  java_executable="$JAVA_HOME/bin/java"
fi
java_version="$("$java_executable" -version 2>&1 | head -n 1)" || fail 'Cannot read Java version'
[[ "$java_version" =~ \"17(\.|\") ]] || fail "JDK 17 is required: $java_version"
gradle_version="$(gradle --version | grep -m1 '^Gradle ' || :)"
[[ "$gradle_version" == 'Gradle 8.11.1' ]] || fail "Gradle 8.11.1 is required: $gradle_version"
node_major="$(node -p 'Number(process.versions.node.split(".")[0])')" || fail 'Cannot read Node version'
[[ "$node_major" == 20 || "$node_major" == 22 ]] || fail "Node 20 or 22 is required: $node_major"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk" && -f "$sdk/platforms/android-36/android.jar" && -x "$sdk/build-tools/35.0.0/zipalign" ]] || fail 'Android SDK 36 or Build-Tools 35.0.0 missing'
state_root="${XDG_STATE_HOME:-$HOME/.local/state}/device-ai-continuum/proofs"
# Refuse even symlink/../ variants before creating any directory in the source tree.
state_candidate="$(realpath -m -- "$state_root")"
[[ "$state_candidate" != "$root" && "$state_candidate" != "$root"/* ]] || fail 'Evidence directory must be outside the checkout'
mkdir -p -- "$state_root"
chmod 700 -- "$state_root"
state_root="$(cd -- "$state_root" && pwd -P)"
[[ "$state_root" != "$root" && "$state_root" != "$root"/* ]] || fail 'Evidence directory must be outside the checkout'

cd -- "$root"
output="$(mktemp)"
trap 'rm -f -- "$output"' EXIT
printf 'Running isolated local M4 gate for %s\n' "$actual_sha"
node .github/continuum/m4-local-gate.mjs --root "$root" --out "$state_root" --android --budget-minutes 90 | tee "$output"
proof="$(node -e 'const fs=require("node:fs");const data=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));if(!data.complete||typeof data.report!=="string")process.exit(1);process.stdout.write(data.report)' "$output")" || fail 'M4 gate did not provide a complete proof'
verified="$(node .github/continuum/m4-local-gate.mjs --verify "$proof")" || fail 'M4 proof verifier failed'
node -e 'const result=JSON.parse(process.argv[1]);if(result.valid!==true||result.releaseReady!==true||result.proof!==process.argv[2]||result.head!==process.argv[3])process.exit(1)' \
  "$verified" "$proof" "$actual_sha" || fail 'M4 proof verifier did not confirm release readiness for this commit'
printf 'M4 proof passed. This does not replace Android lint, cross-runtime parity, or a real-device test.\n'
# Machine-readable path is emitted only after verification of the immutable
# commit, independent repair evidence and SHA-256 of the built preview APK.
printf 'CONTINUUM_VERIFIED_M4_REPORT=%s\n' "$proof"
