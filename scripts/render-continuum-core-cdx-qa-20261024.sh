#!/usr/bin/env bash
# Isolated Render quality gate for merged Continuum 0.3.20 controls + CDX1 audit. NOT an owner release.
set -Eeuo pipefail
umask 077
trap 'code=$?; echo "CONTINUUM_QA_EXIT=$code"; exit "$code"' EXIT

cd "$(git rev-parse --show-toplevel)"
expected_ref="integration/continuum-0320-core-cdx-20261024"
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
echo "CONTINUUM_QA_MODE=NOT_OWNER_RELEASE"
out="$PWD/continuum-core-cdx-qa-output"
mkdir -p "$out"
printf 'CONTINUUM REVIEW BUILD ONLY; NOT AN OWNER UPDATE.\nsource_sha=%s\n' "$head_sha" > "$out/QA-INFO.txt"
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

# Mirror the owner pre-signature regression gates, without owner credentials.
node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
node --test --test-concurrency=2 backend/test/*.test.js
node --check backend/src/groq-mentor.js
node --check backend/src/mentor-pairing.js
node --check backend/src/server.js
node --test backend/test/groq-mentor.test.js backend/test/mentor-answer-format.test.js backend/test/mentor-pairing.test.js .github/continuum/groq-mentor-ui-wiring.test.mjs
for file in .github/continuum/continuum-workshop.mjs .github/continuum/worker-academy.mjs .github/continuum/worker-atlas.mjs .github/continuum/worker-atlas-epistemic-graph.mjs .github/continuum/workshop-specialist-orchestrator.mjs .github/continuum/continuum-hyper-lab.mjs; do
  node --check "$file"
done
node --test .github/continuum/continuum-workshop.test.mjs .github/continuum/worker-academy.test.mjs .github/continuum/worker-atlas.test.mjs .github/continuum/worker-atlas-epistemic-graph.test.mjs .github/continuum/workshop-specialist-orchestrator.test.mjs .github/continuum/continuum-hyper-lab.test.mjs
node --test .github/continuum/autogenesis-safe-wiring.test.mjs .github/continuum/local-model-smoke.test.mjs .github/continuum/continuum-growth-and-archive.test.mjs .github/continuum/autonomous-topic-frontier.test.mjs .github/continuum/research-job-permission.test.mjs .github/continuum/cdx-large-curriculum.test.mjs .github/continuum/training-monitor-wiring.test.mjs

# A different package and the debug certificate: this cannot replace the owner's app.
gradle --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m" \
  -PdeviceAiPreview=true -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$apk"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$apk" | grep -F "package: name='at.heinz.deviceai.preview'"
apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
[[ "$apk_sha256" =~ ^[a-f0-9]{64}$ ]] || { echo "Invalid APK SHA-256" >&2; exit 69; }
artifact="DeviceAI-Continuum-0320-QA-${head_sha:0:12}-${apk_sha256}.apk"
cp "$apk" "$out/$artifact"
(
  cd "$out"
  printf '%s  %s\n' "$apk_sha256" "$artifact" > "SHA256SUMS-${apk_sha256}.txt"
  sha256sum --check --status "SHA256SUMS-${apk_sha256}.txt"
)
printf 'status=REVIEW_QA_PASSED\nowner_signed=false\npackage=at.heinz.deviceai.preview\nsource_sha=%s\nartifact=%s\napk_sha256=%s\n' \
  "$head_sha" "$artifact" "$apk_sha256" >> "$out/QA-INFO.txt"
printf 'User-agent: *\nDisallow: /\n' > "$out/robots.txt"
echo "CONTINUUM_QA_PASSED=$head_sha"
echo "CONTINUUM_QA_APK=$artifact"
