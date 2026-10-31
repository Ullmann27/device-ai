#!/usr/bin/env bash
# Continuum Lokal Render QA for M8 owner app. This job never handles signing keys,
# grants releases or publishes a debug/unsigned APK for installation.
set -Eeuo pipefail
umask 077
cd "$(git rev-parse --show-toplevel)"
die() { printf 'CONTINUUM_M8_RENDER_QA_FAILED: %s\n' "$*" >&2; exit 69; }
sha="$(git rev-parse --verify HEAD)"
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || die 'Invalid commit SHA'
# Render may create untracked framework/cache files in the checkout. Changes to
# tracked source or untracked executable project code are still forbidden.
git -c core.fileMode=false diff --quiet HEAD -- || die 'Tracked source changed in Render checkout'
untracked_sources="$(git ls-files --others --exclude-standard -- app/src scripts .github/continuum backend/src backend/test)"
[[ -z "$untracked_sources" ]] || die 'Untracked source files present in Render checkout'
case "$(git branch --show-current)" in \
  research/continuum-m8-operational-model-20261031|\
  release/continuum-local-complete-v31-20261031|\
  fix/continuum-v32-network-web-recovery-20261031|\
  research/continuum-m8-render-signed-release-20261031|"") ;; \
  *) die "Unexpected branch" ;; \
esac
[[ -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" && -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" &&
    -z "${CONTINUUM_LOCAL_SIGNING_FILE:-}" && -z "${CONTINUUM_LOCAL_SIGNING_PASSWORD:-}" ]] ||
    die 'QA must never receive private signing credentials'
mkdir -p continuum-m8-render-qa
printf 'status=RUNNING_NOT_A_RELEASE\nsource_commit=%s\n' "$sha" > continuum-m8-render-qa/STATUS.txt
workspace="$PWD/.continuum-render-build"
mkdir -p "$workspace"
export JAVA_HOME="$workspace/jdk17"
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  mkdir -p "$JAVA_HOME"
  curl --fail --location --retry 2 --max-time 360     'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse'     -o "$workspace/jdk17.tar.gz"
  tar -xzf "$workspace/jdk17.tar.gz" -C "$JAVA_HOME" --strip-components=1
  rm -f "$workspace/jdk17.tar.gz"
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version
export ANDROID_HOME="$workspace/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl --fail --location --retry 2 --max-time 360     'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip'     -o "$workspace/android-cli.zip"
  echo '7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  '"$workspace/android-cli.zip" |
    sha256sum --check --status
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest" "$workspace/android-cli-unzip"
  unzip -q "$workspace/android-cli.zip" -d "$workspace/android-cli-unzip"
  mv "$workspace/android-cli-unzip/cmdline-tools/"* "$ANDROID_HOME/cmdline-tools/latest/"
  rm -rf "$workspace/android-cli-unzip" "$workspace/android-cli.zip"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;35.0.0'
if [[ ! -x "$workspace/gradle-8.11.1/bin/gradle" ]]; then
  curl --fail --location --retry 2 --max-time 360     'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip'     -o "$workspace/gradle.zip"
  curl --fail --location --retry 2 --max-time 60     'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256'     -o "$workspace/gradle.zip.sha256"
  echo "$(tr -d '[:space:]' < "$workspace/gradle.zip.sha256")  $workspace/gradle.zip" |
    sha256sum --check --status
  unzip -q "$workspace/gradle.zip" -d "$workspace"
  rm -f "$workspace/gradle.zip" "$workspace/gradle.zip.sha256"
fi
export PATH="$workspace/gradle-8.11.1/bin:$PATH"
export GRADLE_USER_HOME="$workspace/gradle-cache"
mkdir -p "$GRADLE_USER_HOME"
gradle --version
node --version
# Preserve frozen historical owner-release approval tests: they are not valid
# acceptance criteria for changed source. Do not run them as false positives.
node --test   .github/continuum/coding-task.test.mjs   .github/continuum/coding-task-android-extensions.test.mjs     > continuum-m8-render-qa/NODE-TESTS.txt 2>&1 || {
    cat continuum-m8-render-qa/NODE-TESTS.txt
    die 'Node contract tests failed'
  }
cat continuum-m8-render-qa/NODE-TESTS.txt | tail -n 30
# All newest research checks, not just old M8 contract smoke tests.
node --test tools/continuum-virtual-fabric/*.test.mjs \
  > continuum-m8-render-qa/FRONTIER-NODE-TESTS.txt 2>&1 || {
  cat continuum-m8-render-qa/FRONTIER-NODE-TESTS.txt >&2
  die 'Latest Frontier Node tests failed'
}
# Regression for release gates with a stubbed signing environment, not signing.
node --test scripts/render-continuum-m8-owner-release.test.mjs \
  > continuum-m8-render-qa/RELEASE-CONTRACT-TESTS.txt 2>&1 || {
  cat continuum-m8-render-qa/RELEASE-CONTRACT-TESTS.txt >&2
  die 'Latest release contract tests failed'
}
# New independent branches integrated for this exact Android release candidate.
node --test   scripts/continuum-jarvis-mission-control-contract.test.mjs   scripts/continuum-owner-render-readback.test.mjs \
  scripts/continuum-v31-feature-gate.test.mjs scripts/continuum-v32-autonomy-gate.test.mjs   > continuum-m8-render-qa/JARVIS-READBACK-TESTS.txt 2>&1 || {
  cat continuum-m8-render-qa/JARVIS-READBACK-TESTS.txt >&2
  die 'JARVIS cockpit or HTTPS published-APK readback tests failed'
}
# The isolated QA mode forbids APK assembly and enforces a distinct .qa package.
gradle --no-daemon --max-workers=2 --console=plain   -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m'   -Pkotlin.compiler.execution.strategy=in-process   -PdeviceAiMentorPreview=true   -PcontinuumLocalStandaloneQa=true   -PcontinuumStandaloneVersionCode=32   :app:testDebugUnitTest :app:lintDebug   > continuum-m8-render-qa/GRADLE-QA.txt 2>&1 || {
    tail -n 180 continuum-m8-render-qa/GRADLE-QA.txt
    die 'Android QA compilation, unit test or lint failed'
  }
tail -n 60 continuum-m8-render-qa/GRADLE-QA.txt
# As a separate task, verify the correct production package can actually be
# assembled in release mode WITHOUT access to signing credentials.
gradle --no-daemon --max-workers=2 --console=plain   -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m'   -Pkotlin.compiler.execution.strategy=in-process   -PdeviceAiMentorPreview=true   -PcontinuumLocalRenderUnsigned=true   -PcontinuumStandaloneVersionCode=32   :app:assembleRelease > continuum-m8-render-qa/ASSEMBLY.txt 2>&1 || {
    tail -n 140 continuum-m8-render-qa/ASSEMBLY.txt
    die 'Unsigned owner-package assembly failed'
  }
test -s app/build/outputs/apk/release/app-release-unsigned.apk ||
  die 'No assembled unsigned candidate APK'
printf 'status=BUILD_AND_TEST_GREEN_UNSIGNED_NOT_INSTALLABLE\nsource_commit=%s\npackage=at.heinz.deviceai.continuumlocal\nversion_code=32\napk_signed=FALSE\napk_published=FALSE\n' "$sha" > continuum-m8-render-qa/STATUS.txt
printf '<!doctype html><html lang="de"><meta charset="UTF-8"><title>Continuum M8 QA</title><body><h1>CONTINUUM M8 QA PASS</h1><p>Nur Build-Abnahme, keine signierte APK veröffentlicht.</p><pre>%s</pre></body></html>' "$sha" > continuum-m8-render-qa/index.html
echo "CONTINUUM_M8_RENDER_QA=PASS SHA=$sha NO_OWNER_APK_PUBLISHED"
