#!/usr/bin/env bash
# Isolated Continuum owner update v0.3.18, code 19; never touches main.
set -Eeuo pipefail
umask 077
die() { printf 'OWNER_V19_BLOCKED: %s\n' "$*" >&2; exit 69; }
: "${CONTINUUM_RELEASE_SHA:?Pinned signed owner release commit required}"
: "${CONTINUUM_ASTRA_UI_SHA:?Reviewed premium UI ancestor required}"
: "$CONTINUUM_MENTOR_SIGNING_P12_BASE64" "$CONTINUUM_MENTOR_SIGNING_PASSWORD"
repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"
render_publisher_root="$repo_root"
signed_release_ref="astra/render-mentor-apk-builder-20261019"
actual_branch="$(git branch --show-current)"
if [[ -n "$actual_branch" && "$actual_branch" != "$signed_release_ref" ]]; then
  die "Unexpected release branch"
fi
# Render checks out detached HEAD; the exact approved 40-character SHA gate below
# remains mandatory and prevents branch or commit substitution.
commit="$(git rev-parse --verify HEAD)"
[[ "$commit" =~ ^[a-f0-9]{40}$ && "$commit" == "$CONTINUUM_RELEASE_SHA" ]] || die "Unapproved source SHA"
[[ "$CONTINUUM_ASTRA_UI_SHA" == "811803138c86a5c1a7a2a2b9cd196580a574a4a1" ]] ||
  die "Unexpected reviewed UI baseline"
# Render uses a shallow detached-HEAD checkout; old commit objects can be missing.
# If ancestry is available it must pass. Otherwise, verify approved source blobs
# from this reviewed release, not a misleading ancestor check on absent objects.
if git cat-file -e "$CONTINUUM_ASTRA_UI_SHA^{commit}" 2>/dev/null; then
  git merge-base --is-ancestor "$CONTINUUM_ASTRA_UI_SHA" "$commit" ||
    die "Approved UI baseline is not an ancestor"
else
  echo "[Continuum] Shallow checkout: enforcing approved UI/CCC1 blob attestations"
fi
while read -r expected_blob source_path; do
  [[ "$expected_blob" =~ ^[a-f0-9]{40}$ && -f "$source_path" ]] ||
    die "Missing approved release file: $source_path"
  actual_blob="$(git hash-object -- "$source_path")"
  [[ "$actual_blob" == "$expected_blob" ]] ||
    die "Approved release file differs: $source_path"
done <<'APPROVED_RELEASE_SOURCES'
945dd025e7a6b441033649597d5848d278b54570 app/src/main/java/at/heinz/deviceai/AgentActivity.kt
8b99c6dae6626d2ed251bb723d75ed0a9ba3b8a3 app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt
2bb8817da268bdcca676aa9a9b48f56b50d06609 app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt
412d643296deb76ab07e9515f6881eba9df3a9b4 app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt
edaa4acbab2c9f2086d26d95e1044a3ccc4c7fc5 app/src/main/java/at/heinz/deviceai/ui/ContinuumSystemGaugesView.kt
e243a487a2011906a9c149a2324b63b39ad2f25c app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeElements.kt
a62abbbf1d005a1ea6fc05310c1dd7b401ecc740 app/src/main/java/at/heinz/deviceai/ContinuumCcc1Bridge.kt
520db0554c0e4d8803d674f229f243f51f2aba17 app/src/main/java/at/heinz/deviceai/ContinuumDashboardCollector.kt
8dac48b9eee7f5af74a777c8cd301b6e1576e49a app/build.gradle.kts
APPROVED_RELEASE_SOURCES
# Render restores SDK/Gradle caches as untracked files. Cache output is harmless,
# but changes to tracked source or new executable/tests in source locations are not.
if ! git -c core.fileMode=false diff --quiet HEAD --; then
  echo "[Continuum] Tracked content changes in cached checkout (paths only):" >&2
  git -c core.fileMode=false diff --name-status HEAD -- >&2 || true
  die "Tracked SOURCE CONTENT differs from approved release commit"
fi
new_source_files="$(git ls-files --others --exclude-standard -- \
  app/src scripts backend/src backend/test .github/continuum .github/workflows)"
[[ -z "$new_source_files" ]] || die "Untracked source files detected in release checkout"
for required in \
  app/src/main/java/at/heinz/deviceai/AgentActivity.kt \
  app/src/main/java/at/heinz/deviceai/ContinuumCcc1Bridge.kt \
  app/src/main/java/at/heinz/deviceai/ComposerReflowDraft.kt \
  app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt \
  .github/continuum/ccc1-cli.mjs \
  .github/continuum/ccc1-verifier.mjs \
  scripts/continuum-mobile-builder.mjs \
  docs/CONTINUUM-PREMIUM-UI-INTEGRATION-GATE-20261023.md; do
  [[ -s "$required" ]] || die "Required component absent: $required"
done
grep -Fq 'versionCode = 19' app/build.gradle.kts || die "Wrong owner versionCode"
grep -Fq 'versionName = "0.3.18"' app/build.gradle.kts || die "Wrong owner versionName"

# Public-source checks must never inherit the private owner keystore/passphrase.
no_signing_env() { env -u CONTINUUM_MENTOR_SIGNING_P12_BASE64 -u CONTINUUM_MENTOR_SIGNING_PASSWORD "$@"; }

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
no_signing_env gradle --version

echo "[Continuum] Sol quality handover: complete Node 22+ deterministic regression gate"
no_signing_env node --version
no_signing_env node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
echo "[Continuum] Reproducible backend dependencies from committed lockfile"
no_signing_env npm --prefix backend ci --ignore-scripts --no-audit --no-fund
echo "[Continuum] Full backend regression gate"
no_signing_env node --test --test-concurrency=2 backend/test/*.test.js

echo "[Continuum] Groq-specific contract tests"
no_signing_env node --check backend/src/groq-mentor.js
no_signing_env node --check backend/src/mentor-pairing.js
no_signing_env node --check backend/src/server.js
no_signing_env node --test backend/test/groq-mentor.test.js backend/test/mentor-answer-format.test.js backend/test/mentor-pairing.test.js .github/continuum/groq-mentor-ui-wiring.test.mjs

echo "[Continuum] Astra Baumeister independent worker, skill academy and platform checks"
no_signing_env node --check .github/continuum/continuum-workshop.mjs
no_signing_env node --check .github/continuum/worker-academy.mjs
no_signing_env node --check .github/continuum/worker-atlas.mjs
no_signing_env node --check .github/continuum/worker-atlas-epistemic-graph.mjs
no_signing_env node --check .github/continuum/workshop-specialist-orchestrator.mjs
no_signing_env node --check .github/continuum/continuum-hyper-lab.mjs
no_signing_env node --test .github/continuum/continuum-workshop.test.mjs .github/continuum/worker-academy.test.mjs .github/continuum/worker-atlas.test.mjs .github/continuum/worker-atlas-epistemic-graph.test.mjs .github/continuum/workshop-specialist-orchestrator.test.mjs .github/continuum/continuum-hyper-lab.test.mjs

echo "[Continuum] AUTOGENESIS, CDX1 and bounded research regression gates"
no_signing_env node --test \
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
  -PdeviceAiMentorPreview=true -PdeviceAiOwnerSignedUpdate=true --stacktrace

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
  | grep -F "package: name='at.heinz.deviceai.mentorpaired' versionCode='19' versionName='0.3.18'"


# Fail closed unless the actually installed owner APK is independently recoverable.
output="$render_publisher_root/mentor-build-output"
mkdir -p "$output"
signer="$expected_signer"
old_base_url="https://device-ai-mentor-apk-preview.onrender.com"
old_metadata="$workspace/prior-build-info.txt"
curl --fail --silent --show-error --location --retry 2 --max-time 120 \
  "$old_base_url/BUILD-INFO.txt" -o "$old_metadata" || die "Prior release metadata unavailable"
prior_code="$(sed -n 's/^version_code=//p' "$old_metadata" | head -1)"
prior_package="$(sed -n 's/^package=//p' "$old_metadata" | head -1)"
prior_sha="$(sed -n 's/^apk_sha256=//p' "$old_metadata" | head -1)"
prior_apk="$(sed -n 's/^artifact=//p' "$old_metadata" | head -1)"
[[ "$prior_code" == "18" && "$prior_package" == "at.heinz.deviceai.mentorpaired" &&
   "$prior_sha" =~ ^[a-f0-9]{64}$ &&
   "$prior_apk" == "DeviceAI-Continuum-v0.3.17-$prior_sha.apk" ]] ||
  die "Prior release is not the installed owner identity"
curl --fail --silent --show-error --location --retry 2 --max-time 240 \
  "$old_base_url/$prior_apk" -o "$workspace/$prior_apk" ||
  die "Missing actual signed owner rollback APK"
printf '%s  %s\n' "$prior_sha" "$workspace/$prior_apk" | sha256sum --check --status ||
  die "Wrong prior APK SHA256"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$workspace/$prior_apk" |
  grep -qiF "$signer" || die "Wrong prior APK signing cert"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$workspace/$prior_apk" |
  grep -Fq "package: name='at.heinz.deviceai.mentorpaired' versionCode='18' versionName='0.3.17'" ||
  die "Wrong prior APK package/version"
cp "$workspace/$prior_apk" "$output/$prior_apk"
printf '%s  %s\n' "$prior_sha" "$output/$prior_apk" | sha256sum --check --status

apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
[[ "$apk_sha256" =~ ^[a-f0-9]{64}$ ]] || { echo "Invalid APK SHA-256"; exit 69; }
immutable_apk="DeviceAI-Continuum-v0.3.18-$apk_sha256.apk"
cp "$apk" "$output/$immutable_apk"
(
 cd "$output"
 printf '%s  %s\n' "$apk_sha256" "$immutable_apk" > "SHA256SUMS-$apk_sha256.txt"
 sha256sum --check --status "SHA256SUMS-$apk_sha256.txt"
)
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$output/$immutable_apk" \
  | grep -qiF "$signer"
{
  printf 'commit=%s\n' "$commit"
  printf 'builder_commit=%s\n' "$(git -C "$render_publisher_root" rev-parse HEAD)"
  printf 'source_branch=%s\n' "$signed_release_ref"
  printf 'package=at.heinz.deviceai.mentorpaired\n'
  printf 'version_code=19\n'
  printf 'version_name=0.3.18\n'
  printf 'android_min_sdk=26\n'
  printf 'signing=persistent_private_mentor_key_same_certificate_as_v0.3.12\n'
  printf 'artifact=%s\n' "$immutable_apk"
  printf 'apk_sha256=%s\n' "$apk_sha256"
  printf 'status=SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED\n'
} > "$output/BUILD-INFO.txt"
cp "$output/BUILD-INFO.txt" "$output/BUILD-INFO-$apk_sha256.txt"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$apk" > "$output/PACKAGE-INFO-$apk_sha256.txt"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$apk" > "$output/SIGNATURE-INFO-$apk_sha256.txt"
printf 'User-agent: *\nDisallow: /\n' > "$output/robots.txt"
echo "[Continuum] VERIFIED OWNER IN-PLACE UPDATE: signed package, code 19; previous owner binary independently archived"
cat "$output/SHA256SUMS-$apk_sha256.txt"
