#!/usr/bin/env bash
# Isolated Render static-build APK compilation. Never commits, merges, or writes secrets.
set -Eeuo pipefail
umask 077

printf '[Continuum] isolated Android mentor preview build\n'
command -v bash >/dev/null
command -v curl >/dev/null
command -v unzip >/dev/null
command -v tar >/dev/null
command -v sha256sum >/dev/null
command -v node >/dev/null
command -v git >/dev/null

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"
# The original Mentor service is still pinned to the older branch.
# This updated script is ready for a separately authorized Render branch switch.
expected_branch="astra/continuum-experiment-charter-20261019"
render_delivery_branch="astra/render-mentor-apk-builder-20261019"
actual_branch="$(git branch --show-current)"
if [[ "$actual_branch" != "$expected_branch" && "$actual_branch" != "$render_delivery_branch" && "$actual_branch" != "" ]]; then
  echo "Refusing wrong source branch: $actual_branch" >&2
  exit 69
fi
commit="$(git rev-parse HEAD)"
if [[ ! "$commit" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Invalid source commit" >&2
  exit 69
fi

workspace="$repo_root/.continuum-render-build"
mkdir -p "$workspace"
export JAVA_HOME="$workspace/jdk17"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "[Continuum] Installing isolated OpenJDK 17"
  mkdir -p "$JAVA_HOME"
  curl --fail --location --retry 2 --max-time 360 \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    -o "$workspace/jdk17.tar.gz"
  tar -xzf "$workspace/jdk17.tar.gz" -C "$JAVA_HOME" --strip-components=1
  rm -f "$workspace/jdk17.tar.gz"
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version

export ANDROID_HOME="$workspace/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "[Continuum] Installing checksum-verified Android SDK command line tools"
  curl --fail --location --retry 2 --max-time 360 \
    "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip" \
    -o "$workspace/android-cli.zip"
  echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  $workspace/android-cli.zip" \
    | sha256sum --check --status
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest" "$workspace/android-cli-unzip"
  unzip -q "$workspace/android-cli.zip" -d "$workspace/android-cli-unzip"
  mv "$workspace/android-cli-unzip/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
  rm -rf "$workspace/android-cli-unzip" "$workspace/android-cli.zip"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
echo "[Continuum] Accepting SDK licenses for approved toolchain"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-36" "build-tools;35.0.0"

if [[ ! -x "$workspace/gradle-8.11.1/bin/gradle" ]]; then
  echo "[Continuum] Installing checksum-verified Gradle"
  curl --fail --location --retry 2 --max-time 360 \
    "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip" \
    -o "$workspace/gradle.zip"
  curl --fail --location --retry 2 --max-time 60 \
    "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256" \
    -o "$workspace/gradle.zip.sha256"
  expected="$(tr -d '[:space:]' < "$workspace/gradle.zip.sha256")"
  echo "$expected  $workspace/gradle.zip" | sha256sum --check --status
  unzip -q "$workspace/gradle.zip" -d "$workspace"
  rm -f "$workspace/gradle.zip" "$workspace/gradle.zip.sha256"
fi
export PATH="$workspace/gradle-8.11.1/bin:$PATH"
export GRADLE_USER_HOME="$workspace/gradle-cache"
mkdir -p "$GRADLE_USER_HOME"
gradle --version

echo "[Continuum] Groq-specific contract tests"
node --check backend/src/groq-mentor.js
node --check backend/src/mentor-pairing.js
node --check backend/src/server.js
node --test backend/test/groq-mentor.test.js backend/test/mentor-answer-format.test.js backend/test/mentor-pairing.test.js .github/continuum/groq-mentor-ui-wiring.test.mjs

echo "[Continuum] Astra Baumeister independent worker, skill academy and platform checks"
node --check .github/continuum/continuum-workshop.mjs
node --check .github/continuum/worker-academy.mjs
node --check .github/continuum/worker-atlas.mjs
node --check .github/continuum/worker-atlas-epistemic-graph.mjs
node --check .github/continuum/workshop-specialist-orchestrator.mjs
node --check .github/continuum/continuum-hyper-lab.mjs
node --test .github/continuum/continuum-workshop.test.mjs .github/continuum/worker-academy.test.mjs .github/continuum/worker-atlas.test.mjs .github/continuum/worker-atlas-epistemic-graph.test.mjs .github/continuum/workshop-specialist-orchestrator.test.mjs .github/continuum/continuum-hyper-lab.test.mjs

echo "[Continuum] AUTOGENESIS, CDX1 and bounded research regression gates"
node --test \
  .github/continuum/autogenesis-safe-wiring.test.mjs \
  .github/continuum/local-model-smoke.test.mjs \
  .github/continuum/continuum-growth-and-archive.test.mjs \
  .github/continuum/autonomous-topic-frontier.test.mjs \
  .github/continuum/research-job-permission.test.mjs \
  .github/continuum/cdx-large-curriculum.test.mjs

echo "[Continuum] Require persistent mentor preview signing (not disposable debug key)"
: "${CONTINUUM_MENTOR_SIGNING_P12_BASE64:?Persistent signing keystore is missing}"
: "${CONTINUUM_MENTOR_SIGNING_PASSWORD:?Signing password is missing}"
printf '%s' "$CONTINUUM_MENTOR_SIGNING_P12_BASE64" | base64 --decode > "$workspace/mentor-preview-signing.p12"
chmod 600 "$workspace/mentor-preview-signing.p12"
export CONTINUUM_MENTOR_SIGNING_FILE="$workspace/mentor-preview-signing.p12"
keytool -list -keystore "$CONTINUUM_MENTOR_SIGNING_FILE" -storepass "$CONTINUUM_MENTOR_SIGNING_PASSWORD" -storetype PKCS12 >/dev/null

echo "[Continuum] Android JVM tests, API lint and Mentor Preview APK"
gradle --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m" \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  -PdeviceAiMentorPreview=true --stacktrace

apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$apk"
cert_details="$("$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$apk")"
expected_signer="2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc"
if ! grep -qiF "$expected_signer" <<< "$cert_details"; then
  echo "Refusing APK signed by an unexpected or temporary debug key." >&2
  exit 69
fi
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$apk" \
  | grep -F "package: name='at.heinz.deviceai.mentorpaired'"

output="$repo_root/mentor-build-output"
mkdir -p "$output"
cp "$apk" "$output/DeviceAI-Continuum-Groq-Mentor-Preview.apk"
(
  cd "$output"
  sha256sum DeviceAI-Continuum-Groq-Mentor-Preview.apk > SHA256SUMS.txt
)
printf 'commit=%s\nbranch=%s\nreview_branch=%s\npackage=at.heinz.deviceai.mentorpaired\nandroid_min_sdk=26\nsigning=persistent_private_preview_keystore\n' \
  "$commit" "$actual_branch" "$expected_branch" > "$output/BUILD-INFO.txt"
printf 'User-agent: *\nDisallow: /\n' > "$output/robots.txt"
echo "[Continuum] Verified APK and checksums ready for publication"
cat "$output/SHA256SUMS.txt"
