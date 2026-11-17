#!/usr/bin/env bash
# Isolated Android plus Node preflight for selected Gemma/DeepSeek model center; no owner release.
set -Eeuo pipefail
umask 077
trap 'code=$?; echo "CONTINUUM_QA_EXIT=$code"; exit "$code"' EXIT

cd "$(git rev-parse --show-toplevel)"
expected_ref="feature/continuum-model-center-selected-models-20261025"
head_sha="$(git rev-parse --verify HEAD)"
[[ "$head_sha" =~ ^[a-f0-9]{40}$ ]] || { echo "QA: invalid Git HEAD" >&2; exit 69; }
if [[ ! "${CONTINUUM_MODEL_CENTER_QA_SHA:-}" =~ ^[a-f0-9]{40}$ ||
      "$head_sha" != "$CONTINUUM_MODEL_CENTER_QA_SHA" ]]; then
  echo "QA: source SHA does not match requested commit" >&2
  exit 69
fi
branch="$(git branch --show-current)"
if [[ -n "$branch" && "$branch" != "$expected_ref" ]]; then
  echo "QA: expected dedicated review source branch" >&2
  exit 69
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "QA: source checkout must start clean" >&2
  exit 69
fi
echo "CONTINUUM_QA_SOURCE=$head_sha"
echo "CONTINUUM_QA_MODE=MODEL_CENTER_ANDROID_PREFLIGHT_NO_SIGNING"
out="$PWD/continuum-model-center-qa"
mkdir -p "$out"
printf 'Continuum owner model center source preflight; no APK signed.\nsource_sha=%s\n' "$head_sha" > "$out/QA-INFO.txt"
workspace="$PWD/.continuum-render-build"
mkdir -p "$workspace"
export JAVA_HOME="$workspace/jdk17"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  mkdir -p "$JAVA_HOME"
  curl --fail --location --retry 2 --max-time 360 "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o "$workspace/jdk17.tar.gz"
  tar -xzf "$workspace/jdk17.tar.gz" -C "$JAVA_HOME" --strip-components=1
  rm "$workspace/jdk17.tar.gz"
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version
export ANDROID_HOME="$workspace/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl --fail --location --retry 2 --max-time 360 "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip" -o "$workspace/android-cli.zip"
  echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  $workspace/android-cli.zip" | sha256sum --check --status
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest" "$workspace/android-cli-unzip"
  unzip -q "$workspace/android-cli.zip" -d "$workspace/android-cli-unzip"
  mv "$workspace/android-cli-unzip/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
  rm -rf "$workspace/android-cli-unzip" "$workspace/android-cli.zip"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-36" "build-tools;35.0.0"
if [[ ! -x "$workspace/gradle-8.11.1/bin/gradle" ]]; then
  curl --fail --location --retry 2 --max-time 360 "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" -o "$workspace/gradle.zip"
  curl --fail --location --retry 2 --max-time 60 "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256" -o "$workspace/gradle.zip.sha256"
  expected="$(tr -d '[:space:]' < "$workspace/gradle.zip.sha256")"
  echo "$expected  $workspace/gradle.zip" | sha256sum --check --status
  unzip -q "$workspace/gradle.zip" -d "$workspace"
  rm "$workspace/gradle.zip" "$workspace/gradle.zip.sha256"
fi
export PATH="$workspace/gradle-8.11.1/bin:$PATH"
export GRADLE_USER_HOME="$workspace/gradle-cache"
mkdir -p "$GRADLE_USER_HOME"
gradle --version
node --version

# Owner v23 preflight: never accesses signing keys and never publishes any APK.
# Run all existing Node safety regressions, backend tests, Android unit tests, lint and compilation.
export CONTINUUM_OWNER_V23_QA=1
# Research-only preflight, signing credentials must never enter this job.
unset CONTINUUM_MENTOR_SIGNING_P12_BASE64 CONTINUUM_MENTOR_SIGNING_PASSWORD CONTINUUM_MENTOR_SIGNING_FILE
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs > "$out/NODE-TESTS.log" 2>&1 || {
  tail -n 100 "$out/NODE-TESTS.log"; exit 69;
}
npm --prefix backend ci --ignore-scripts --no-audit --no-fund > "$out/NPM-CI.log" 2>&1
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 backend/test/*.test.js > "$out/BACKEND-TESTS.log" 2>&1
gradle --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m" \
  -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin > "$out/ANDROID-QA.log" 2>&1 || {
    tail -n 100 "$out/ANDROID-QA.log"; exit 69;
  }
# Same Android code and Manifest class paths. No owner signing is possible in this QA environment.
printf 'source_sha=%s\nstatus=MODEL_CENTER_ANDROID_QA_GREEN_NO_SIGNING\nowner_package=at.heinz.deviceai.mentorpaired\ntarget_version_code=24\nsigner=NOT_PRESENT\n' "$head_sha" > "$out/STATUS.txt"
echo '<!doctype html><title>Continuum model center preflight</title><pre>QA results in STATUS.txt. No APK or signing keys published.</pre>' > "$out/index.html"
printf 'User-agent: *\nDisallow: /\n' > "$out/robots.txt"
echo "CONTINUUM_MODEL_CENTER_QA_PASSED=$head_sha"
