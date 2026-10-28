#!/usr/bin/env bash
# Device AI Continuum v26 OWNER update. Replaces obsolete v25 source/version pins.
# Never bypass the installed-app certificate, exact source identity or testing.
set -Eeuo pipefail
umask 077
die() { printf 'CONTINUUM_RENDER_V26_BLOCKED: %s\n' "$*" >&2; exit 69; }
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$repo_root"
head="$(git rev-parse --verify HEAD 2>/dev/null)" || die 'Checkout has no HEAD'
[[ "$head" =~ ^[a-f0-9]{40}$ ]] || die 'Invalid source SHA'
[[ "${CONTINUUM_OWNER_V26_APPROVED_SHA:-}" == "$head" ]] ||
  die 'Exact v26 source approval missing or mismatched'
[[ -z "$(git -c core.fileMode=false diff --name-only HEAD --)" ]] ||
  die 'Tracked source content has changed'
[[ -z "$(git ls-files --others --exclude-standard -- app/src scripts backend/src backend/test .github/continuum)" ]] ||
  die 'Untracked production source detected'
[[ -f scripts/continuum-local-owner-apk.sh && -f app/build.gradle.kts ]] ||
  die 'Version 26 owner builder or main app missing'
[[ -n "${CONTINUUM_MENTOR_SIGNING_P12_BASE64:-}" ]] ||
  die 'Original P12 secret unavailable. Never create replacement signer'
[[ -n "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] ||
  die 'Original signer password unavailable. Never create replacement signer'

# Reuse the historically successful Render JDK17/SDK36/Gradle8.11.1 setup,
# but keep cached toolchains and private material OUTSIDE the Git checkout.
workspace="$HOME/.cache/continuum-render-v26"
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

export CONTINUUM_GRADLE_PATH="$workspace/gradle-8.11.1/bin/gradle"
private_dir="$(mktemp -d "$workspace/private-p12-XXXXXX")"
chmod 700 "$private_dir"
trap 'rm -rf -- "$private_dir"' EXIT
export CONTINUUM_MENTOR_SIGNING_FILE="$private_dir/continuum-owner.p12"
printf '%s' "$CONTINUUM_MENTOR_SIGNING_P12_BASE64" | base64 --decode > "$CONTINUUM_MENTOR_SIGNING_FILE" ||
  die 'Original P12 data cannot be decoded'
chmod 600 "$CONTINUUM_MENTOR_SIGNING_FILE"
unset CONTINUUM_MENTOR_SIGNING_P12_BASE64

export CONTINUUM_OWNER_NEXT_VERSION_CODE=26
export CONTINUUM_OWNER_LOCAL_APPROVED_SHA="$head"
export CONTINUUM_LOCAL_OWNER_OUTPUT_DIR="$workspace/signed-owner-v26"
# The existing owner builder checks original P12 alias/fingerprint, full Kotlin
# tests, lint, APK signature, package, versionCode, and immutable SHA-256.
bash "$repo_root/scripts/continuum-local-owner-apk.sh" build ||
  die 'Original-signature Android owner build failed'

owner_signer='2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc'
owner_package='at.heinz.deviceai.mentorpaired'
info="$CONTINUUM_LOCAL_OWNER_OUTPUT_DIR/BUILD-INFO-$head-26.txt"
[[ -s "$info" ]] || die 'Owner build provenance missing'
grep -Fxq 'status=SIGNED_NOT_DEVICE_INSTALLED' "$info" ||
  die 'Not an owner-signed APK'
grep -Fxq "source_sha=$head" "$info" || die 'Source provenance mismatch'
grep -Fxq "owner_signer_sha256=$owner_signer" "$info" || die 'Original signer mismatch'
grep -Fxq 'version_code=26' "$info" || die 'Expected versionCode 26'
grep -Fxq "package=$owner_package" "$info" || die 'Wrong owner package'
apk="$(sed -n 's/^artifact=//p' "$info" | head -n 1)"
[[ -n "$apk" && -s "$apk" && ! -L "$apk" ]] || die 'Owner APK missing'
sha="$(sha256sum "$apk" | cut -d' ' -f1)"
grep -Fxq "apk_sha256=$sha" "$info" || die 'Signed APK SHA-256 mismatch'

# Preserve the last verified signed v25 APK when replacing the static release
# listing. This digest was observed in the successful 2026-10-27 Render log.
old_sha='f415e7858e8aeb278e1da9a70098db4827dbd55226376f0d127a1f3d231feeac'
old_name="DeviceAI-Continuum-v0.3.24-$old_sha.apk"
old_path="$workspace/$old_name"
curl --fail --location --retry 2 --max-time 240 \
  "https://device-ai-mentor-apk-preview.onrender.com/$old_name" -o "$old_path" ||
  die 'Previous installed-v25-compatible rollback APK unavailable'
printf '%s  %s\n' "$old_sha" "$old_path" | sha256sum --check --status ||
  die 'Rollback APK digest changed'
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$old_path" |
  grep -qiF "$owner_signer" || die 'Rollback APK original certificate mismatch'
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$old_path" |
  grep -Fq "package: name='$owner_package' versionCode='25' versionName='0.3.24'" ||
  die 'Rollback APK identity incorrect'

# Publish only on a completely verified original-signature owner release.
# No QA/debug APK, model snapshot, P12, or credential enters static output.
publish="$repo_root/mentor-build-output"
mkdir -p -- "$publish"
cp -- "$old_path" "$publish/$old_name"
new_name="DeviceAI-Continuum-v0.3.25-$sha.apk"
cp -- "$apk" "$publish/$new_name"
(
  cd "$publish"
  sha256sum "$old_name" "$new_name" > SHA256SUMS.txt
  sha256sum --check --status SHA256SUMS.txt ||
    die 'Published artifact checksum mismatch'
)
printf 'status=SIGNED_BUILD_VERIFIED_NOT_DEVICE_INSTALLED\nsource_sha=%s\npackage=%s\nversion_code=26\nversion_name=0.3.25\nsigning=original_owner_certificate\nowner_signer_sha256=%s\nartifact=%s\napk_sha256=%s\nrollback_artifact=%s\n' \
  "$head" "$owner_package" "$owner_signer" "$new_name" "$sha" "$old_name" \
  > "$publish/BUILD-INFO.txt"
printf 'User-agent: *\nDisallow: /\n' > "$publish/robots.txt"
printf 'CONTINUUM_RENDER_V26_SIGNED_READY source_sha=%s package=%s versionCode=26 apk_sha256=%s\n' \
  "$head" "$owner_package" "$sha"
