#!/usr/bin/env bash
# Isolated Render quality gate for merged Continuum 0.3.20 controls + CDX1 audit. NOT an owner release.
set -Eeuo pipefail
umask 077
trap 'code=$?; echo "CONTINUUM_QA_EXIT=$code"; exit "$code"' EXIT

cd "$(git rev-parse --show-toplevel)"
expected_ref="integration/continuum-owner-0324-review-20261026"
head_sha="$(git rev-parse --verify HEAD)"
[[ "$head_sha" =~ ^[a-f0-9]{40}$ ]] || { echo "QA: invalid Git HEAD" >&2; exit 69; }
if [[ -n "${CONTINUUM_QA_SOURCE_SHA:-}" && "$head_sha" != "$CONTINUUM_QA_SOURCE_SHA" ]]; then
  echo "QA: source SHA does not match requested commit" >&2
  exit 69
fi
branch="$(git branch --show-current)"
if [[ -n "$branch" && "$branch" != "$expected_ref" ]]; then
  echo "QA: expected dedicated review source branch" >&2
  exit 69
fi
echo "CONTINUUM_QA_SOURCE=$head_sha"
echo "CONTINUUM_QA_MODE=OWNER_V25_BUILD_QA_WITH_EPHEMERAL_NONOWNER_TEST_CERT"
out="$PWD/continuum-owner-v24-qa"
mkdir -p "$out"
printf 'CONTINUUM 0.3.24 QA BUILD ONLY; NOT A SIGNED APK.\nsource_sha=%s\n' "$head_sha" > "$out/QA-INFO.txt"
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

# Owner v25 QA: ephemeral independently generated test key; NEVER owner signing or release.
# Run the actual merged PR133+PR135 sources and historical owner safety regressions.
export CONTINUUM_OWNER_V23_QA=1
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs > "$out/NODE-TESTS.log" 2>&1 || {
  tail -n 100 "$out/NODE-TESTS.log"; exit 69;
}
npm --prefix backend ci --ignore-scripts --no-audit --no-fund > "$out/NPM-CI.log" 2>&1
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 backend/test/*.test.js > "$out/BACKEND-TESTS.log" 2>&1
# Use an ephemeral QA-only certificate so Gradle can compile exactly the v24
# owner package/version. This certificate deliberately differs from the installed app.
# The resulting artifact MUST NOT be used as an in-place owner update.
qa_key="$workspace/continuum-v25-qa-only.p12"
qa_password="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
keytool -genkeypair -alias deviceai-mentor -keyalg RSA -keysize 3072 \
  -validity 2 -dname 'CN=Continuum NONOWNER QA Certificate,OU=QA,O=Continuum,C=AT' \
  -storetype PKCS12 -keystore "$qa_key" \
  -storepass "$qa_password" -keypass "$qa_password" -noprompt >/dev/null 2>&1
export CONTINUUM_MENTOR_SIGNING_FILE="$qa_key"
export CONTINUUM_MENTOR_SIGNING_PASSWORD="$qa_password"
gradle --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m" \
  -PdeviceAiMentorPreview=true -PdeviceAiOwnerSignedUpdate=true -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin :app:assembleDebug > "$out/ANDROID-QA.log" 2>&1 || {
    tail -n 100 "$out/ANDROID-QA.log"; exit 69;
  }
apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk" || { echo "V24 QA APK missing" >&2; exit 69; }
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$apk" > "$out/APK-SIGNATURE-QA.txt"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$apk" | grep -F "package: name='at.heinz.deviceai.mentorpaired' versionCode='25' versionName='0.3.24'" > "$out/APK-PACKAGE-QA.txt"
# This must never accidentally carry the original owner certificate.
if grep -qi '2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc' "$out/APK-SIGNATURE-QA.txt"; then
 echo "QA key unexpectedly matches owner certificate; aborting" >&2; exit 69
fi
apk_sha256="$(sha256sum "$apk" | cut -d' ' -f1)"
artifact="CONTINUUM-0324-NONOWNER-QA-NOT-INSTALLABLE-${head_sha:0:12}.apk"
cp "$apk" "$out/$artifact"
printf '%s  %s\n' "$apk_sha256" "$artifact" > "$out/SHA256SUMS"
(cd "$out"; sha256sum --check --status SHA256SUMS)
rm -f "$qa_key"
unset CONTINUUM_MENTOR_SIGNING_FILE CONTINUUM_MENTOR_SIGNING_PASSWORD qa_password
printf 'source_sha=%s\nstatus=V25_QA_APK_BUILT_TEST_CERT_NOT_OWNER_UPDATE\npackage=at.heinz.deviceai.mentorpaired\nversion_code=25\nversion_name=0.3.24\napk_sha256=%s\nowner_installable=FALSE\n' "$head_sha" "$apk_sha256" > "$out/STATUS.txt"
echo '<!doctype html><title>Continuum 0.3.24 NONOWNER QA</title><pre>QA-only APK. Wrong signing certificate; DO NOT INSTALL over the owner app. See STATUS.txt and test logs.</pre>' > "$out/index.html"
printf 'User-agent: *\nDisallow: /\n' > "$out/robots.txt"
echo "CONTINUUM_OWNER_V25_QA_PASSED=$head_sha"
