#!/usr/bin/env bash
# Research-only APK: separate .qa package, no original owner signing.
set -Eeuo pipefail
set +x
umask 077
fail() { printf 'CONTINUUM_QA_BLOCKED: %s\n' "$*" >&2; exit 69; }
cd "$(git rev-parse --show-toplevel)"
sha="$(git rev-parse --verify HEAD)" || fail 'No source SHA'
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || fail 'Invalid source SHA'
branch_name="$(git symbolic-ref --quiet --short HEAD || true)"
if [[ "$branch_name" != "integration/continuum-all-systems-apk-qa-20261102" ]]; then
  # Render intentionally checks out a detached commit even when configured to
  # follow a named branch. Only this dedicated Render QA service is allowed.
  [[ -z "$branch_name" && "${RENDER_SERVICE_NAME:-}" == "continuum-unified-research-apk-20261102" ]] ||
    fail 'Wrong source branch or untrusted detached checkout'
fi
git -c core.fileMode=false diff --quiet HEAD -- || fail 'Changed tracked source'
[[ -z "$(git ls-files --others --exclude-standard -- app/src scripts backend/src backend/test)" ]] || fail 'Untracked executable source'
for key in CONTINUUM_LOCAL_SIGNING_FILE CONTINUUM_LOCAL_SIGNING_PASSWORD CONTINUUM_LOCAL_SIGNING_P12_BASE64 CONTINUUM_MENTOR_SIGNING_FILE CONTINUUM_MENTOR_SIGNING_PASSWORD; do
  [[ -z "$(printenv "$key" 2>/dev/null || true)" ]] || fail 'Signing material forbidden in QA'
done
[[ ! -e continuum-all-systems-site && ! -e continuum-all-systems-logs ]] || fail 'Stale QA directory'
mkdir continuum-all-systems-logs
printf 'source=%s\n' "$sha" > continuum-all-systems-logs/SOURCE.txt
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

node --test scripts/continuum-atomic-acceptance.test.mjs scripts/continuum-gguf-preflight.test.mjs scripts/continuum-gguf-preflight.integration.test.mjs > continuum-all-systems-logs/NODE-SPECIAL.txt 2>&1 ||
  { tail -n 100 continuum-all-systems-logs/NODE-SPECIAL.txt; fail 'Node special contract failed'; }
qa_proofs="$(mktemp -d "${TMPDIR:-/tmp}/continuum-integrated-qa.XXXXXXXX")"
node scripts/continuum-reliability-qa.mjs critical --out "$qa_proofs/critical" > continuum-all-systems-logs/NODE-CRITICAL.txt 2>&1 ||
  { tail -n 100 continuum-all-systems-logs/NODE-CRITICAL.txt; fail 'Node critical failed'; }
cp "$qa_proofs/critical/report.json" continuum-all-systems-logs/CRITICAL-RECEIPT.json
node scripts/continuum-reliability-qa.mjs full-node --out "$qa_proofs/full-node" > continuum-all-systems-logs/NODE-FULL.txt 2>&1 ||
  { tail -n 100 continuum-all-systems-logs/NODE-FULL.txt; fail 'Full Node failed'; }
cp "$qa_proofs/full-node/report.json" continuum-all-systems-logs/FULL-NODE-RECEIPT.json
gradle --no-daemon --rerun-tasks --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=38 :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug > continuum-all-systems-logs/ANDROID-TESTS.txt 2>&1 ||
  { tail -n 150 continuum-all-systems-logs/ANDROID-TESTS.txt; fail 'Android test/lint failure'; }
gradle --no-daemon --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumInstallableResearchQa=true -PcontinuumStandaloneVersionCode=38 :app:assembleDebug > continuum-all-systems-logs/APK-ASSEMBLY.txt 2>&1 ||
  { tail -n 150 continuum-all-systems-logs/APK-ASSEMBLY.txt; fail 'QA APK build failure'; }
apk='app/build/outputs/apk/debug/app-debug.apk'
[[ -s "$apk" && ! -L "$apk" ]] || fail 'APK missing'
apksigner verify --verbose "$apk" > continuum-all-systems-logs/APK-SIGNATURE.txt || fail 'APK signing invalid'
aapt2 dump badging "$apk" > continuum-all-systems-logs/APK-BADGING.txt || fail 'APK identity unreadable'
grep -Fq "package: name='at.heinz.deviceai.continuumlocal.qa' versionCode='38'" continuum-all-systems-logs/APK-BADGING.txt ||
  fail 'Wrong package or version'
[[ "$(git rev-parse HEAD)" == "$sha" ]] || fail 'Source changed'
git -c core.fileMode=false diff --quiet HEAD -- || fail 'Tracked source changed during build'
hash="$(sha256sum "$apk" | cut -d' ' -f1)"
[[ "$hash" =~ ^[0-9a-f]{64}$ ]] || fail 'Invalid APK hash'
bytes="$(stat -c '%s' "$apk")"
[[ "$bytes" =~ ^[0-9]+$ && "$bytes" -gt 1000000 ]] || fail 'Invalid APK size'
mkdir continuum-all-systems-site
name="Continuum-Research-QA-$hash.apk"
install -m 644 "$apk" "continuum-all-systems-site/$name"
[[ "$(sha256sum "continuum-all-systems-site/$name" | cut -d' ' -f1)" == "$hash" ]] || fail 'Staging hash changed'
CONTINUUM_SOURCE_SHA="$sha" CONTINUUM_APK_HASH="$hash" CONTINUUM_APK_FILE="$name" CONTINUUM_APK_SIZE="$bytes" node --input-type=module <<'NODE'
import fs from 'node:fs';
const d={schemaVersion:1,channel:'ISOLATED_RESEARCH_QA',status:'HOST_TESTS_PASS_DEVICE_NOT_RUN',packageName:'at.heinz.deviceai.continuumlocal.qa',versionCode:38,sourceCommit:process.env.CONTINUUM_SOURCE_SHA,sha256:process.env.CONTINUUM_APK_HASH,apkFile:process.env.CONTINUUM_APK_FILE,byteLength:Number(process.env.CONTINUUM_APK_SIZE),ownerSigned:false,canUpdateInstalledOwnerApp:false,deviceVerified:false};
fs.writeFileSync('continuum-all-systems-site/build.json',JSON.stringify(d,null,2)+'\n');
fs.writeFileSync('continuum-all-systems-site/index.html','<!doctype html><html lang="de"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Continuum QA</title><style>body{font:17px sans-serif;background:#101827;color:white;max-width:720px;margin:10vh auto;padding:24px}a{display:inline-block;padding:16px;border-radius:12px;background:#2479d5;color:#fff;text-decoration:none}</style><h1>Continuum – getrennte Forschungs-Test-APK</h1><p>Diese APK ersetzt die installierte Original-App nicht.</p><p>SHA: '+d.sourceCommit+'</p><p>APK SHA-256: '+d.sha256+'</p><p>Fold7 noch nicht abgenommen.</p><a href="/'+encodeURIComponent(d.apkFile)+'">APK herunterladen</a></html>');
NODE
echo "CONTINUUM_QA_APK_BUILT source=$sha sha256=$hash package=at.heinz.deviceai.continuumlocal.qa"
