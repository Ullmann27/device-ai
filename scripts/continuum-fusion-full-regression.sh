#!/usr/bin/env bash
# Local full MAIN-APP unit regression. Never signs, installs, fetches models or merges.
set -Eeuo pipefail
umask 077
fail() { printf 'CONTINUUM_FULL_REGRESSION_BLOCKED: %s\n' "$*" >&2; exit 69; }
[[ $# -eq 1 && "$1" =~ ^[0-9a-f]{40}$ ]] || {
  printf 'Usage: bash scripts/continuum-fusion-full-regression.sh <approved-exact-HEAD-SHA>\n' >&2
  exit 64
}
expected="$1"
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
[[ -f "$root/app/build.gradle.kts" && -f "$root/settings.gradle.kts" ]] || fail 'Full Android checkout required'
for tool in git node sha256sum; do command -v "$tool" >/dev/null 2>&1 || fail "Missing $tool"; done
head="$(git -C "$root" rev-parse --verify HEAD 2>/dev/null)" || fail 'Git HEAD unavailable'
[[ "$head" == "$expected" ]] || fail "Refusing different SHA: actual $head, approved $expected"
[[ -z "$(git -C "$root" status --porcelain --untracked-files=normal)" ]] || fail 'Uncommitted source changes; preserve evidence first'
[[ -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" && -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] || fail 'Signing secrets must not be loaded for unit tests'
termux=false
if [[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]]; then
  termux=true
  if [[ -z "${JAVA_HOME:-}" && -x "$PREFIX/lib/jvm/java-17-openjdk/bin/java" ]]; then
    export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
  fi
fi
if [[ -z "${JAVA_HOME:-}" && -x /workspace/continuum-tools/jdk17/bin/java ]]; then
  export JAVA_HOME=/workspace/continuum-tools/jdk17
fi
[[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]] || fail 'Explicit JDK17 JAVA_HOME required'
export PATH="$JAVA_HOME/bin:$PATH"
[[ "$(java -version 2>&1 | head -n 1)" == *'"17.'* ]] || fail 'JDK17 required'
[[ "$(javac -version 2>&1)" == 'javac 17.'* ]] || fail 'javac 17 required'
if [[ -z "${ANDROID_HOME:-}" && -f /workspace/continuum-tools/android-sdk/platforms/android-36/android.jar ]]; then
  export ANDROID_HOME=/workspace/continuum-tools/android-sdk
elif [[ -z "${ANDROID_HOME:-}" && -f "$HOME/android-sdk/platforms/android-36/android.jar" ]]; then
  export ANDROID_HOME="$HOME/android-sdk"
fi
[[ -n "${ANDROID_HOME:-}" && -f "$ANDROID_HOME/platforms/android-36/android.jar" ]] || fail 'Existing Android SDK36 required'
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ -z "${GRADLE_USER_HOME:-}" && -d /workspace/continuum-tools/gradle-cache ]]; then
  export GRADLE_USER_HOME=/workspace/continuum-tools/gradle-cache
fi
if [[ -n "${CONTINUUM_GRADLE_PATH:-}" ]]; then
  gradle_bin="$CONTINUUM_GRADLE_PATH"
elif [[ -x /workspace/continuum-tools/gradle/gradle-8.11.1/bin/gradle ]]; then
  gradle_bin=/workspace/continuum-tools/gradle/gradle-8.11.1/bin/gradle
elif command -v gradle >/dev/null 2>&1; then
  gradle_bin="$(command -v gradle)"
else
  gradle_bin="$HOME/continuum-toolchains/gradle-8.11.1/bin/gradle"
fi
[[ -x "$gradle_bin" ]] || fail 'Existing Gradle8.11.1 required; never download a replacement'
[[ "$("$gradle_bin" --version 2>&1)" == *'Gradle 8.11.1'* ]] || fail 'Wrong Gradle version'
if [[ "$termux" == true ]]; then
  aapt2="${CONTINUUM_AAPT2_PATH:-$PREFIX/bin/aapt2}"
else
  aapt2="${CONTINUUM_AAPT2_PATH:-$ANDROID_HOME/build-tools/35.0.0/aapt2}"
fi
[[ -x "$aapt2" ]] || fail 'Existing build-tools/35.0.0/aapt2 required'
node_major="$(node -p 'parseInt(process.versions.node.split(".")[0],10)')"
[[ "$node_major" == 20 || "$node_major" == 22 || "$node_major" == 24 ]] || fail 'Supported Node LTS 20/22/24 required'
out="${CONTINUUM_FUSION_REGRESSION_OUTPUT:-$HOME/.local/state/device-ai-continuum/fusion-regression/$head}"
mkdir -p -- "$out"
out="$(cd -- "$out" && pwd -P)"
case "$out" in "$root"|"$root"/*) fail 'Evidence must remain outside source checkout';; esac
[[ ! -e "$out/summary.json" && ! -e "$out/sha256-manifest.json" ]] || fail 'Prior run evidence exists; use a fresh evidence directory'
cd "$root"
args=(--offline --no-daemon --console=plain --max-workers=2 --rerun-tasks
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true
  -PcontinuumStandaloneVersionCode=27 "-Pandroid.aapt2FromMavenOverride=$aapt2")
if [[ "$termux" == true ]]; then args+=(-PdeviceAiMobileAidlFallback=true); fi
started="$(node -p 'Date.now()')"
printf 'APPROVED_SOURCE_HEAD=%s\nSTART_EPOCH_MS=%s\n' "$head" "$started" > "$out/command.txt"
printf 'GRADLE_COMMAND=' >> "$out/command.txt"
printf ' %q' "$gradle_bin" "${args[@]}" :app:testDebugUnitTest :app:lintDebug >> "$out/command.txt"
printf '\nNODE_COMMAND=node --test scripts/continuum-external-research-contract.test.mjs scripts/continuum-experiment-integration.test.mjs scripts/continuum-local-context.test.mjs scripts/continuum-fusion-regression-report.test.mjs\n' >> "$out/command.txt"
set +e
"$gradle_bin" "${args[@]}" :app:testDebugUnitTest :app:lintDebug > "$out/gradle-full-suite.log" 2>&1
gradle_exit=$?
node --test scripts/continuum-external-research-contract.test.mjs \
  scripts/continuum-experiment-integration.test.mjs \
  scripts/continuum-local-context.test.mjs \
  scripts/continuum-fusion-regression-report.test.mjs > "$out/node-contracts.log" 2>&1
node_exit=$?
set -e
printf 'GRADLE_EXIT=%s\nNODE_EXIT=%s\n' "$gradle_exit" "$node_exit" >> "$out/command.txt"
node scripts/continuum-fusion-regression-report.mjs \
  "$out" "$root" "$head" "$gradle_exit" "$node_exit" "$started"
