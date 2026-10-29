#!/usr/bin/env bash
# Dedicated Render regression build for owner-reviewed web evidence bridge.
# NO APK; NO owner signature; no access to private user files.
set -Eeuo pipefail
set +x
umask 077

fail() {
  printf 'CONTINUUM_WEB_REVIEW_QA_BLOCKED: %s\n' "$*" >&2
  exit 69
}

cd "$(git rev-parse --show-toplevel)"
[[ -f app/build.gradle.kts && -f app/src/main/AndroidManifest.xml ]] ||
  fail "Android source tree absent"
[[ -f app/src/main/java/at/heinz/deviceai/ApprovedWebResearchToolRunner.kt ]] ||
  fail "Authorized tool receipt review bridge missing"
[[ -z "${CONTINUUM_LOCAL_SIGNING_FILE:-}" &&
   -z "${CONTINUUM_LOCAL_SIGNING_PASSWORD:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_P12_BASE64:-}" ]] ||
  fail "Owner and mentor secrets are forbidden in the public QA builder"

source_sha="$(git rev-parse HEAD)"
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail "Invalid source identity"
build_dir="$PWD/.continuum-render-web-review"
out="$PWD/continuum-web-review-qa-output"
mkdir -p "$build_dir" "$out"
printf '%s\n' 'QA build started; no original-signed APK is being produced' > "$out/STATUS.txt"

export JAVA_HOME="$build_dir/jdk17"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  mkdir -p "$JAVA_HOME"
  curl --fail --location --retry 2 --max-time 360 \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    -o "$build_dir/jdk17.tar.gz"
  tar -xzf "$build_dir/jdk17.tar.gz" -C "$JAVA_HOME" --strip-components=1
  rm -f "$build_dir/jdk17.tar.gz"
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version

export ANDROID_HOME="$build_dir/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl --fail --location --retry 2 --max-time 360 \
    "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip" \
    -o "$build_dir/android-cli.zip"
  echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  $build_dir/android-cli.zip" \
    | sha256sum --check --status || fail "Android SDK tool digest mismatch"
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest" "$build_dir/android-cli-unzip"
  unzip -q "$build_dir/android-cli.zip" -d "$build_dir/android-cli-unzip"
  mv "$build_dir/android-cli-unzip/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
  rm -rf "$build_dir/android-cli-unzip" "$build_dir/android-cli.zip"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-36" "build-tools;35.0.0"

if [[ ! -x "$build_dir/gradle-8.11.1/bin/gradle" ]]; then
  curl --fail --location --retry 2 --max-time 360 \
    "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" \
    -o "$build_dir/gradle.zip"
  curl --fail --location --retry 2 --max-time 60 \
    "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256" \
    -o "$build_dir/gradle.zip.sha256"
  digest="$(tr -d '[:space:]' < "$build_dir/gradle.zip.sha256")"
  [[ "$digest" =~ ^[a-fA-F0-9]{64}$ ]] || fail "Invalid Gradle release digest"
  printf '%s  %s\n' "$digest" "$build_dir/gradle.zip" | sha256sum --check --status ||
    fail "Gradle distribution integrity failed"
  unzip -q "$build_dir/gradle.zip" -d "$build_dir"
  rm -f "$build_dir/gradle.zip" "$build_dir/gradle.zip.sha256"
fi
export PATH="$build_dir/gradle-8.11.1/bin:$PATH"
export GRADLE_USER_HOME="$build_dir/gradle-cache"
gradle --version

export CONTINUUM_CRE1_PARITY_DIR="$out/cre1-parity"
export CONTINUUM_CONTRASTIVE_PARITY_DIR="$out/contrastive-parity"
node .github/continuum/cre-parity.mjs prepare "$CONTINUUM_CRE1_PARITY_DIR" > "$out/parity-prepare.log"
node .github/continuum/contrastive-parity.mjs prepare "$CONTINUUM_CONTRASTIVE_PARITY_DIR" >> "$out/parity-prepare.log"

flags=(--no-daemon --console=plain --max-workers=2
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m"
  -Pkotlin.compiler.execution.strategy=in-process
  -PdeviceAiMentorPreview=true
  -PcontinuumLocalStandaloneQa=true
  -PcontinuumStandaloneVersionCode=29
  "-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2"
)
printf '%s\n' 'Starting Android JVM full suite and NewApi lint; no APK generation'
if ! gradle "${flags[@]}" :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin > "$out/android-tests.log" 2>&1; then
  tail -n 65 "$out/android-tests.log" >&2
  fail "Android test or compilation failed"
fi
printf '%s\n' 'Starting isolated CCC1 repair-ledger, replay, and repair-loop regressions'
if ! node --test --test-concurrency=2 \
  .github/continuum/repair-ledger-independent.test.mjs \
  .github/continuum/repair-loop.test.mjs \
  .github/continuum/repair-experience.test.mjs \
  .github/continuum/repair-cycle-safety.test.mjs \
  > "$out/node-ccc1-regressions.log" 2>&1; then
  tail -n 70 "$out/node-ccc1-regressions.log" >&2
  fail "CCC1 independently verified repair regression failed"
fi
printf '%s\n' 'Starting Node regression suite'
if ! node --test scripts/continuum-external-research-contract.test.mjs \
  scripts/continuum-experiment-integration.test.mjs \
  scripts/continuum-local-context.test.mjs \
  scripts/continuum-fusion-regression-report.test.mjs \
  scripts/continuum-coding-bridge.test.mjs \
  scripts/continuum-selfhosted-coding.test.mjs \
  scripts/continuum-integration-source-gate.test.mjs \
  scripts/continuum-sol-max-readiness.test.mjs \
  scripts/continuum-standalone-qa-variant.test.mjs \
  scripts/continuum-learning-parity-audit.test.mjs \
  scripts/continuum-ccc1-review-gate.test.mjs \
  scripts/continuum-release-host-preflight.test.mjs \
  scripts/continuum-one-app-contract.test.mjs \
  scripts/continuum-gemma-picker-lifecycle.test.mjs \
  scripts/continuum-fold7-standalone-apk.test.mjs \
  scripts/continuum-historical-ledger-review.test.mjs \
  scripts/continuum-local-chat-streaming.test.mjs scripts/continuum-source-state.test.mjs \
  scripts/continuum-generation16-rescue.test.mjs \
  .github/continuum/local-development-worker.test.mjs \
  .github/continuum/local-worker-process.test.mjs > "$out/node-contracts.log" 2>&1; then
  tail -n 50 "$out/node-contracts.log" >&2
  fail "Node contract regression failed"
fi
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR" > "$out/parity-verify.log"
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR" >> "$out/parity-verify.log"
cat > "$out/STATUS.txt" <<EOF
source_commit=$source_sha
status=ANDROID_AND_NODE_TESTS_PASS
owner_signed=FALSE
owner_installable=FALSE
apk_created=FALSE
EOF
printf 'CONTINUUM_WEB_REVIEW_QA_PASS source=%s\n' "$source_sha"
