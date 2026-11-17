#!/usr/bin/env bash
# QA-only quality gate for the NEW 2026-10-27 integrated owner candidate. NEVER signs with owner key.
set -Eeuo pipefail
umask 077
trap 'code=$?; echo "CONTINUUM_QA_EXIT=$code"; exit "$code"' EXIT

cd "$(git rev-parse --show-toplevel)"
expected_ref="integration/continuum-owner-v26-live-candidate-20261027"
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
echo "CONTINUUM_QA_MODE=INTEGRATED_20261027_CODE25_EPHEMERAL_NONOWNER_TEST_CERT"
out="$PWD/continuum-owner-current-v25-qa"
mkdir -p "$out"
printf 'CONTINUUM 0.3.24 / code25 INTEGRATION QA ONLY; NOT OWNER-SIGNED; DO NOT INSTALL.\nsource_sha=%s\n' "$head_sha" > "$out/QA-INFO.txt"
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

# All historical source/inventory approval tests are run on their OWN frozen
# release branch, never weakened or silently relabeled green on this new HEAD.
# A new branch's code cannot pass the older fixed SOURCE/1034-blob approval.
# Preserve proof that the OLD V25 source approval rejects this new candidate.
echo "CURRENT_V25_OLD_MANIFEST=SUPERSEDED_NOT_APPROVED"
if env -u CONTINUUM_OWNER_V25_APPROVED_SOURCE_SHA \
   -u CONTINUUM_OWNER_V25_APPROVED_MANIFEST_BLOB \
   -u CONTINUUM_RELEASE_SHA -u CONTINUUM_OWNER_V25_APPROVED_SHA \
   node scripts/continuum-v25-source-gate.mjs > "$out/OLD-RELEASE-GATE.log" 2>&1; then
   echo "ERROR: old owner manifest unexpectedly accepted unfrozen source" >&2
   exit 69
fi
grep -q 'OWNER_V25_BLOCKED' "$out/OLD-RELEASE-GATE.log" || {
  echo "ERROR: legacy gate did not reject for an identifiable release reason" >&2
  exit 69
}
# The old historical-owner-v25 release test necessarily expects its immutable
# 2026-10-26 source HEAD. EXCLUDE ONLY THAT obsolete positive approval test from
# fresh integration QA; DO NOT delete/change it or weaken a production gate.
# Report the selection to reviewers and require a NEW immutable owner inventory
# before any signed APK is authorized.
mapfile -d '' tests < <(find .github/continuum scripts -type f -name '*.test.mjs' \
  ! -name 'continuum-v25-owner-release.test.mjs' -print0)
printf 'qa_new_source=%s\\nlegacy_release_test=NOT_APPLICABLE_OLD_FROZEN_SHA\\nnew_release_inventory=NOT_APPROVED\\n' \
  "$head_sha" > "$out/TEST-SCOPE.txt"
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 "${tests[@]}" > "$out/NODE-TESTS.log" 2>&1 || {
  tail -n 100 "$out/NODE-TESTS.log"; exit 69
}
npm --prefix backend ci --ignore-scripts --no-audit --no-fund > "$out/NPM-CI.log" 2>&1
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 backend/test/*.test.js > "$out/BACKEND-TESTS.log" 2>&1
# Use an ephemeral QA-only certificate so Gradle can compile exactly the v24
# owner package/version. This certificate deliberately differs from the installed app.
# The resulting artifact MUST NOT be used as an in-place owner update.
qa_key="$workspace/continuum-live-v25-qa-only.p12"
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
# NO APK is published by this QA project. It has a non-owner certificate and
# could cause Android package-conflict errors on the owner's device.
printf 'qa_apk_sha256=%s\\nqa_apk_published=FALSE\\nqa_apk_owner_installable=FALSE\\n' \
  "$apk_sha256" > "$out/QA-ARTIFACT.txt"
rm -f "$qa_key"
unset CONTINUUM_MENTOR_SIGNING_FILE CONTINUUM_MENTOR_SIGNING_PASSWORD qa_password
printf 'source_sha=%s\nstatus=V25_INTEGRATED_QA_NONOWNER_BUILD_PASSED\npackage=at.heinz.deviceai.mentorpaired\nversion_code=25\nversion_name=0.3.24\nqa_apk_sha256=%s\nowner_installable=FALSE\nrelease_authorized=FALSE\n' "$head_sha" "$apk_sha256" > "$out/STATUS.txt"
echo '<!doctype html><title>Continuum 0.3.24 source-integrated QA</title><pre>Android build and tests only; no downloadable APK and NO owner release authorized. See STATUS.txt and test logs.</pre>' > "$out/index.html"
printf 'User-agent: *\nDisallow: /\n' > "$out/robots.txt"
echo "CONTINUUM_OWNER_LIVE_QA_PASSED=$head_sha"
