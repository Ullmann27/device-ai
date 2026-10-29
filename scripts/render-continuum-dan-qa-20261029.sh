#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077
cd "$(git rev-parse --show-toplevel)"
[[ -z "${CONTINUUM_LOCAL_SIGNING_FILE:-}" && -z "${CONTINUUM_LOCAL_SIGNING_PASSWORD:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" && -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] || exit 69
head="$(git rev-parse HEAD)"
[[ "$head" =~ ^[0-9a-f]{40}$ ]] || exit 69
out="$PWD/continuum-dan-qa-output"
base="$PWD/.continuum-dan-qa-toolchain"
mkdir -p "$out" "$base"
export JAVA_HOME="$base/jdk17"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  mkdir -p "$JAVA_HOME"
  curl -fL --retry 2 --max-time 360 https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse -o "$base/jdk.tar.gz"
  tar -xzf "$base/jdk.tar.gz" -C "$JAVA_HOME" --strip-components=1
  rm "$base/jdk.tar.gz"
fi
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$base/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fL --retry 2 --max-time 360 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$base/android.zip"
  echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  $base/android.zip" | sha256sum -c --status
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest" "$base/unpack"
  unzip -q "$base/android.zip" -d "$base/unpack"
  mv "$base/unpack/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
  rm -rf "$base/unpack" "$base/android.zip"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-36" "build-tools;35.0.0"
if [[ ! -x "$base/gradle-8.11.1/bin/gradle" ]]; then
  curl -fL --retry 2 --max-time 360 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o "$base/gradle.zip"
  curl -fL --retry 2 --max-time 60 https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256 -o "$base/gradle.sha"
  hash="$(tr -d '[:space:]' < "$base/gradle.sha")"
  [[ "$hash" =~ ^[a-f0-9]{64}$ ]] || exit 69
  echo "$hash  $base/gradle.zip" | sha256sum -c --status
  unzip -q "$base/gradle.zip" -d "$base"
  rm "$base/gradle.zip" "$base/gradle.sha"
fi
export GRADLE_USER_HOME="$base/gradle-cache"
export CONTINUUM_CRE1_PARITY_DIR="$base/cre1-parity"
export CONTINUUM_CONTRASTIVE_PARITY_DIR="$base/contrastive-parity"
node .github/continuum/cre-parity.mjs prepare "$CONTINUUM_CRE1_PARITY_DIR"
node .github/continuum/contrastive-parity.mjs prepare "$CONTINUUM_CONTRASTIVE_PARITY_DIR"
"$base/gradle-8.11.1/bin/gradle" --no-daemon --console=plain --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m" \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=27 \
  :app:testDebugUnitTest :app:lintDebug > "$out/gradle-tests.log" 2>&1 || {
    tail -n 90 "$out/gradle-tests.log" >&2
    exit 1
  }
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR"
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR"
printf 'source_sha=%s\nstatus=ANDROID_UNIT_AND_NEWAPI_LINT_PASS\nowner_signed=false\napk_built=false\n' "$head" > "$out/STATUS.txt"
echo "CONTINUUM_DAN_QA_PASSED=$head"
