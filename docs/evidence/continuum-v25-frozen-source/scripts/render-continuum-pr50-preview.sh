#!/usr/bin/env bash
# Isolated Render static-build APK compilation. Never commits, merges, or writes secrets.
set -Eeuo pipefail
umask 077

printf '[Continuum] isolated PR50 side-by-side Android preview build\n'
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
expected_branch="chatgpt/continuum-apk-preview-20261021"
actual_branch="$(git branch --show-current)"
if [[ "$actual_branch" != "$expected_branch" && "$actual_branch" != "" ]]; then
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

echo "[Continuum] Separate debug-signed preview package, not a Mentor Pair update"
echo "[Continuum] Android JVM tests, API lint and separate Preview APK"
gradle --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m" \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  -PdeviceAiPreview=true --stacktrace

apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"
tools="$ANDROID_HOME/build-tools/35.0.0"
"$tools/apksigner" verify --verbose --print-certs "$apk" > "$workspace/verify.txt"
grep -Fq "Verified using v2 scheme (APK Signature Scheme v2): true" "$workspace/verify.txt"
"$tools/aapt" dump badging "$apk" > "$workspace/badging.txt"
grep -Fq "package: name='at.heinz.deviceai.preview' versionCode='6' versionName='0.3.3'" "$workspace/badging.txt"
test "$(git rev-parse HEAD)" = "$commit" || { echo 'Source HEAD changed'; exit 69; }
# Repository tests may chmod shell entrypoints from 0644 to 0755.
# Treat these as harmless ONLY when their contents still match HEAD exactly.
if ! git diff --cached --quiet --exit-code; then
  echo 'Staged source changes during build; refusing release' >&2
  git diff --cached --name-status >&2
  exit 69
fi
while IFS= read -r -d '' changed; do
  if [[ "$changed" != scripts/*.sh || ! -f "$changed" || -L "$changed" ]]; then
    echo "Unexpected tracked change: $changed" >&2
    exit 69
  fi
  head_sha="$(git show "HEAD:$changed" | sha256sum | awk '{print $1}')"
  work_sha="$(sha256sum -- "$changed" | awk '{print $1}')"
  if [[ "$head_sha" != "$work_sha" ]]; then
    echo "Modified source contents: $changed" >&2
    exit 69
  fi
  echo "Allowed chmod-only change with exact Git content: $changed"
done < <(git diff --name-only -z)

output="$repo_root/continuum-preview-output"
mkdir -p "$output"
apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
[[ "$apk_sha256" =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid hash'; exit 69; }
immutable_apk="DeviceAI-Continuum-PR50-preview-${apk_sha256}.apk"
cp "$apk" "$output/$immutable_apk"
(
 cd "$output"
 printf '%s  %s\n' "$apk_sha256" "$immutable_apk" > "SHA256SUMS-${apk_sha256}.txt"
 sha256sum --check --status "SHA256SUMS-${apk_sha256}.txt"
)
cp "$workspace/badging.txt" "$output/PACKAGE-INFO-${apk_sha256}.txt"
cp "$workspace/verify.txt" "$output/SIGNATURE-INFO-${apk_sha256}.txt"
printf 'commit=%s\nbranch=%s\npackage=at.heinz.deviceai.preview\nversion_code=6\nversion_name=0.3.3\nsigner=isolated_ephemeral_debug_signer\nartifact=%s\napk_sha256=%s\nstatus=BUILD_VERIFIED_NOT_DEVICE_INSTALLED\n' \
 "$commit" "$actual_branch" "$immutable_apk" "$apk_sha256" > "$output/BUILD-INFO-${apk_sha256}.txt"
printf 'User-agent: *\nDisallow: /\n' > "$output/robots.txt"
echo '[Continuum] Verified side-by-side preview APK published under immutable hash filename'
cat "$output/SHA256SUMS-${apk_sha256}.txt"
