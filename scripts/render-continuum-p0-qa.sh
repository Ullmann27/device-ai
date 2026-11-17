#!/usr/bin/env bash
# Dedicated Render QA build for the new Continuum Lokal P0 in-app updater.
# NEVER signs an owner update or touches original private signing credentials.
set -Eeuo pipefail
set +x
umask 077

fail() {
  printf 'CONTINUUM_P0_RENDER_QA_BLOCKED: %s\n' "$*" >&2
  exit 69
}

cd "$(git rev-parse --show-toplevel)"
[[ -f app/build.gradle.kts && -f app/src/main/AndroidManifest.xml ]] ||
  fail "Android source tree absent"
[[ -f app/src/main/java/at/heinz/deviceai/ContinuumOwnerUpdateActivity.kt ]] ||
  fail "P0 update activity missing"
[[ -f app/src/main/java/at/heinz/deviceai/intelligence/ContinuumOwnerUpdater.kt ]] ||
  fail "P0 update manager missing"
[[ -z "${CONTINUUM_LOCAL_SIGNING_FILE:-}" &&
   -z "${CONTINUUM_LOCAL_SIGNING_PASSWORD:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_P12_BASE64:-}" ]] ||
  fail "Owner and mentor secrets are forbidden in the public QA builder"

source_sha="$(git rev-parse HEAD)"
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail "Invalid source identity"
build_dir="$PWD/.continuum-render-p0-qa"
out="$PWD/continuum-p0-qa-output"
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

node --check scripts/continuum-owner-update-manifest.mjs
if node scripts/continuum-owner-update-manifest.mjs; then
  fail "Publisher accepted missing signed APK"
fi

flags=(
  --no-daemon --console=plain --max-workers=2
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m"
  -Pkotlin.compiler.execution.strategy=in-process
)
printf '%s\n' 'Testing actual Continuum Lokal source with QA-only package configuration'
gradle "${flags[@]}" \
  -PdeviceAiMentorPreview=true \
  -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=29 \
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin ||
    fail "Kotlin tests, lint, or standalone-local compilation failed"

# Build a DIFFERENT debug/preview package for engineering verification.
# It MUST NOT be offered as an in-place owner APK update.
gradle "${flags[@]}" -PdeviceAiPreview=true :app:assembleDebug ||
  fail "Android preview QA APK could not be built"
apk="app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$apk" ]] || fail "No real APK file produced"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$apk" > "$out/SIGNATURE.txt" ||
  fail "Debug APK signing verification failed"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$apk" > "$out/PACKAGE.txt" ||
  fail "Debug APK identity unreadable"
grep -Fq "package: name='at.heinz.deviceai.preview'" "$out/PACKAGE.txt" ||
  fail "QA package not isolated from owner package"
if grep -qiF '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23' "$out/SIGNATURE.txt"; then
  fail "An original owner signing identity must never enter public QA"
fi
sha="$(sha256sum "$apk" | cut -d ' ' -f 1)"
[[ "$sha" =~ ^[a-f0-9]{64}$ ]] || fail "Invalid APK SHA-256"
name="Continuum-P0-Update-Button-QA-NICHT-ALS-UPDATE-INSTALLIEREN-${sha}.apk"
cp "$apk" "$out/$name"
( cd "$out" && sha256sum "$name" > SHA256SUMS.txt && sha256sum --check --status SHA256SUMS.txt )
cat > "$out/STATUS.txt" <<EOF
source_commit=$source_sha
package=at.heinz.deviceai.preview
owner_signed=FALSE
owner_installable=FALSE
status=QA_APK_BUILT_NOT_OWNER_UPDATE
sha256=$sha
artifact=$name
EOF
printf 'CONTINUUM_P0_QA_APK_READY=%s/%s\n' "$out" "$name"
printf 'Original signer not used; do not install over Continuum Lokal\n'

# Separate original-package RELEASE assembly, deliberately with NO signer.
# The approved ORIGINAL P12 is used only on the Fold7 after a byte-for-byte
# hash comparison with build evidence, never in the Render environment.
printf '%s\n' 'Building unsigned Continuum Lokal v0.3.28 transport artifact'
gradle "${flags[@]}" \
  -PdeviceAiMentorPreview=true \
  -PcontinuumLocalRenderUnsigned=true \
  -PcontinuumStandaloneVersionCode=29 \
  :app:assembleRelease ||
    fail "Unsigned main-app release assembly failed"
unsigned="app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -s "$unsigned" && ! -L "$unsigned" ]] ||
  fail "Android did not produce an unsigned main-app APK"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$unsigned" > "$out/UNSIGNED-PACKAGE.txt" ||
  fail "Unsigned APK package unreadable"
grep -Fq "package: name='at.heinz.deviceai.continuumlocal' versionCode='29' versionName='0.3.28'" \
  "$out/UNSIGNED-PACKAGE.txt" ||
  fail "Unsigned transport APK identity/version mismatch"
if "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "$unsigned" >/dev/null 2>&1; then
  fail "Transport APK unexpectedly has a valid signing identity"
fi
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -p 4 "$unsigned" >/dev/null ||
  fail "Unsigned APK is not correctly zipaligned"
unsigned_sha="$(sha256sum "$unsigned" | cut -d ' ' -f 1)"
[[ "$unsigned_sha" =~ ^[a-f0-9]{64}$ ]] || fail "Bad unsigned APK digest"
unsigned_name="Continuum-Lokal-0.3.28-V29-UNSIGNIERT-${unsigned_sha}.apk"
cp "$unsigned" "$out/$unsigned_name"
( cd "$out" && sha256sum "$unsigned_name" > UNSIGNED-SHA256SUMS.txt &&
  sha256sum --check --status UNSIGNED-SHA256SUMS.txt )
cat > "$out/UNSIGNED-STATUS.txt" <<EOF
source_commit=$source_sha
package=at.heinz.deviceai.continuumlocal
version_code=29
version_name=0.3.28
owner_signed=FALSE
owner_installable=FALSE
status=UNSIGNED_OWNER_PACKAGE_FOR_LOCAL_ORIGINAL_P12_SIGNING
sha256=$unsigned_sha
artifact=$unsigned_name
EOF
printf 'CONTINUUM_UNSIGNED_OWNER_APK_READY=%s/%s\n' "$out" "$unsigned_name"
printf 'Local Fold7 original P12 signature required before installation.\n'

