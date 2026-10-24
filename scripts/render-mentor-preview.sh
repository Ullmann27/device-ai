#!/usr/bin/env bash
# Owner-approved owner-approved, in-place Continuum 0.3.16 Fold/motion update with unchanged signer.
# The original 0.3.12 builder is retained below for immediate Git revert and audit.
# v0.3.17: retain the long-tested persistent owner signature, but only publish the
# fully tested CCC1 + premium UI branch with independently verified rollback.
# Explicit release selection. Default preserves the currently published v0.3.17
# signed owner update; v19 is forbidden without a second owner-pinned source SHA.
set -Eeuo pipefail
case "${CONTINUUM_OWNER_RELEASE_VERSION:-18}" in
  18)
    exec bash scripts/render-mentor-owner-update-v18.sh
    ;;
  19)
    approved="${CONTINUUM_OWNER_V19_APPROVED_SHA:-}"
    requested="${CONTINUUM_RELEASE_SHA:-}"
    current="$(git rev-parse --verify HEAD 2>/dev/null)" || {
      echo "OWNER_V19_BLOCKED: No valid source revision" >&2
      exit 69
    }
    if [[ ! "$approved" =~ ^[a-f0-9]{40}$ || "$approved" != "$requested" || "$requested" != "$current" ]]; then
      echo "OWNER_V19_BLOCKED: Owner-approved source SHA missing or mismatched" >&2
      exit 69
    fi
    [[ -s scripts/render-mentor-owner-update-v19.sh ]] || {
      echo "OWNER_V19_BLOCKED: Reviewed v19 release script unavailable" >&2
      exit 69
    }
    exec bash scripts/render-mentor-owner-update-v19.sh
    ;;
  20)
    approved="${CONTINUUM_OWNER_V20_APPROVED_SHA:-}"
    requested="${CONTINUUM_RELEASE_SHA:-}"
    current="$(git rev-parse --verify HEAD 2>/dev/null)" || {
      echo "OWNER_V20_BLOCKED: No valid source revision" >&2
      exit 69
    }
    if [[ ! "$approved" =~ ^[a-f0-9]{40}$ || "$approved" != "$requested" || "$requested" != "$current" ]]; then
      echo "OWNER_V20_BLOCKED: Owner-approved source SHA missing or mismatched" >&2
      exit 69
    fi
    [[ -s scripts/render-mentor-owner-update-v20.sh ]] || {
      echo "OWNER_V20_BLOCKED: Reviewed v20 release script unavailable" >&2
      exit 69
    }
    exec bash scripts/render-mentor-owner-update-v20.sh
    ;;
  21)
    approved="${CONTINUUM_OWNER_V21_APPROVED_SHA:-}"
    requested="${CONTINUUM_RELEASE_SHA:-}"
    current="$(git rev-parse --verify HEAD 2>/dev/null)" || {
      echo "OWNER_V21_BLOCKED: No valid source revision" >&2
      exit 69
    }
    if [[ ! "$approved" =~ ^[a-f0-9]{40}$ || "$approved" != "$requested" || "$requested" != "$current" ]]; then
      echo "OWNER_V21_BLOCKED: Exact owner-approved release builder SHA missing or mismatched" >&2
      exit 69
    fi
    [[ -s scripts/render-mentor-owner-update-v21.sh ]] || {
      echo "OWNER_V21_BLOCKED: Reviewed v21 release script unavailable" >&2
      exit 69
    }
    exec bash scripts/render-mentor-owner-update-v21.sh
    ;;
  *)
    echo "OWNER_RELEASE_BLOCKED: Unsupported release version" >&2
    exit 69
    ;;
esac
# Isolated Render static-build APK compilation. Never commits, merges, or writes secrets.
set -Eeuo pipefail
umask 077

printf '[Continuum] same-package signed Mentor Pair navigation update v0.3.12, code 15\n'
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
# Review integration branch is allowed; never upload an APK without exact package/version/certificate verification.
expected_branch="astra/continuum-experiment-charter-20261019"
render_delivery_branch="astra/render-mentor-apk-builder-20261019"
actual_branch="$(git branch --show-current)"
if [[ "$actual_branch" != "$expected_branch" && "$actual_branch" != "$render_delivery_branch" && "$actual_branch" != "integration/continuum-astra-20261021" && "$actual_branch" != "" ]]; then
  echo "Refusing wrong source branch: $actual_branch" >&2
  exit 69
fi
# Only the established Render delivery branch is authorized; the private
# signing material stays in that service and never enters the repository.
if [[ "$actual_branch" != "$render_delivery_branch" && "$actual_branch" != "" ]]; then
  echo "Refusing any branch other than approved signer-owning delivery branch" >&2
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

echo "[Continuum] Sol quality handover: complete Node 22+ deterministic regression gate"
node --version
node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
echo "[Continuum] Reproducible backend dependencies from committed lockfile"
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
echo "[Continuum] Full backend regression gate"
node --test --test-concurrency=2 backend/test/*.test.js

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
  .github/continuum/cdx-large-curriculum.test.mjs \
  .github/continuum/training-monitor-wiring.test.mjs

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
  | grep -F "package: name='at.heinz.deviceai.mentorpaired' versionCode='15' versionName='0.3.12'"

output="$repo_root/mentor-build-output"
mkdir -p "$output"
# Preserve the last known-good signed v0.3.10 immutable download while
# publishing the signed v0.3.11 update. No mutable CDN aliases are trusted.
previous_sha="67b1715d0e7eb42945c81aa90ede5b1d6eca526444d0cff8d83f388dd782aa56"
previous_apk="DeviceAI-Continuum-v0.3.10-${previous_sha}.apk"
curl --fail --location --retry 2 --max-time 240 \
  "https://device-ai-mentor-apk-preview.onrender.com/$previous_apk" \
  -o "$output/$previous_apk"
printf '%s  %s\n' "$previous_sha" "$output/$previous_apk" | sha256sum --check --status || {
  echo "Previous known-good APK was not preserved; refusing release" >&2
  exit 69
}
printf '%s  %s\n' "$previous_sha" "$previous_apk" > "$output/SHA256SUMS-${previous_sha}.txt"

# Retain the previous signed 0.3.11 APK for a verified rollback option.
prior_sha="e529374f9e4eb4bde149c156e1f217405500c7c9c88711efa1b437d63bd01b72"
prior_apk="DeviceAI-Continuum-v0.3.11-e529374f9e4eb4bde149c156e1f217405500c7c9c88711efa1b437d63bd01b72.apk"
curl --fail --location --retry 2 --max-time 240 \
  "https://device-ai-mentor-apk-preview.onrender.com/$prior_apk" \
  -o "$output/$prior_apk"
printf '%s  %s\n' "$prior_sha" "$output/$prior_apk" | sha256sum --check --status || {
  echo "Previous signed 0.3.11 APK could not be preserved" >&2
  exit 69
}
printf '%s  %s\n' "$prior_sha" "$prior_apk" > "$output/SHA256SUMS-${prior_sha}.txt"

# Content-addressed publication prevents an existing CDN path serving an older
# APK under a reused version name. Never accept checksum metadata from a prior deploy.
apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
if [[ ! "$apk_sha256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Invalid APK SHA-256" >&2
  exit 69
fi
immutable_apk="DeviceAI-Continuum-v0.3.12-${apk_sha256}.apk"
cp "$apk" "$output/$immutable_apk"
(
  cd "$output"
  printf '%s  %s\n' "$apk_sha256" "$immutable_apk" > "SHA256SUMS-${apk_sha256}.txt"
  sha256sum --check --status "SHA256SUMS-${apk_sha256}.txt"
)
# Keep legacy aliases for compatibility, but NEVER use them as release links:
# a CDN can cache an older APK with the same version under these names.
cp "$apk" "$output/DeviceAI-Continuum-Groq-Mentor-Preview.apk"
cp "$apk" "$output/DeviceAI-Continuum-Training-Monitor-v0.3.12.apk"
(
  cd "$output"
  sha256sum DeviceAI-Continuum-Groq-Mentor-Preview.apk \
    DeviceAI-Continuum-Training-Monitor-v0.3.12.apk > SHA256SUMS.txt
)
printf 'commit=%s\nbranch=%s\nreview_branch=%s\npackage=at.heinz.deviceai.mentorpaired\nversion_code=15\nversion_name=0.3.12\nandroid_min_sdk=26\nsigning=persistent_private_preview_keystore\nartifact=%s\napk_sha256=%s\n' \
  "$commit" "$actual_branch" "$expected_branch" "$immutable_apk" "$apk_sha256" > "$output/BUILD-INFO.txt"
cp "$output/BUILD-INFO.txt" "$output/BUILD-INFO-Training-Monitor-v0.3.12.txt"
cp "$output/BUILD-INFO.txt" "$output/BUILD-INFO-${apk_sha256}.txt"
printf 'User-agent: *\nDisallow: /\n' > "$output/robots.txt"
echo "[Continuum] Verified immutable APK and checksums ready for publication"
cat "$output/SHA256SUMS-${apk_sha256}.txt"
