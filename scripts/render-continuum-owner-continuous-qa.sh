#!/usr/bin/env bash
# Strict QA for the original owner package, dynamic code39+; never signs or publishes it.
set -Eeuo pipefail
set +x
umask 077
fail() { printf 'CONTINUUM_QA_BLOCKED: %s\n' "$*" >&2; exit 69; }
cd "$(git rev-parse --show-toplevel)"
sha="$(git rev-parse --verify HEAD)" || fail 'No source SHA'
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || fail 'Invalid source SHA'
owner_qa="${CONTINUUM_OWNER_RELEASE_QA:-0}"
if [[ "$owner_qa" == "1" ]]; then
  [[ "${RENDER_SERVICE_NAME:-}" == "continuum-owner-v23-release-preflight" &&
     "${CONTINUUM_RELEASE_APPROVED_SHA:-}" == "$sha" &&
     "${CONTINUUM_RELEASE_HOST:-}" == "continuum-owner-v23-release-preflight.onrender.com" &&
     "${CONTINUUM_RELEASE_VERSION_CODE:-}" =~ ^[0-9]+$ &&
     "${CONTINUUM_RELEASE_VERSION_CODE}" -ge 39 &&
     "${CONTINUUM_RELEASE_VERSION_CODE}" -le 9999 ]] ||
    fail 'Owner QA requires approved SHA, future code39+ and pinned publisher'
elif [[ "$owner_qa" == "0" ]]; then
  [[ "${RENDER_SERVICE_NAME:-}" == "continuum-unified-research-apk-20261102" ]] ||
    fail 'Only isolated QA service may run ordinary QA'
else
  fail 'Invalid owner QA mode'
fi
branch_name="$(git symbolic-ref --quiet --short HEAD || true)"
if [[ "$branch_name" != "integration/continuum-all-systems-apk-qa-20261102" ]]; then
  # Render intentionally checks out a detached commit even when configured to
  # follow a named branch. Only this dedicated Render QA service is allowed.
  [[ -z "$branch_name" ]] ||
    fail 'Wrong source branch or untrusted detached checkout'
  # Render detaches HEAD. The canonical Node-QA contract requires an attached
  # local branch; reattach THIS exact checked-out SHA without touching remote.
  if git show-ref --verify --quiet refs/heads/integration/continuum-all-systems-apk-qa-20261102; then
    [[ "$(git rev-parse refs/heads/integration/continuum-all-systems-apk-qa-20261102)" == "$sha" ]] ||
      fail 'Local QA branch points to a different commit'
    git switch integration/continuum-all-systems-apk-qa-20261102 >/dev/null ||
      fail 'Could not select existing local QA branch'
  else
    git switch -c integration/continuum-all-systems-apk-qa-20261102 "$sha" >/dev/null ||
      fail 'Could not create local QA source branch'
  fi
  [[ "$(git rev-parse HEAD)" == "$sha" ]] || fail 'Reattachment changed source'
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
# Exact Adoptium Temurin Linux x64 JDK 17.0.20.1+1 (official GitHub
# expanded release asset SHA256). No mutable latest URL or unverified tarball.
jdk_release='17.0.20.1+1'
jdk_sha256='3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e'
jdk_url='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz'
export JAVA_HOME="$workspace/jdk17"
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  mkdir -p "$JAVA_HOME"
  curl --fail --location --retry 2 --max-time 360 "$jdk_url" -o "$workspace/jdk17.tar.gz" ||
    fail 'Pinned JDK download failed'
  echo "$jdk_sha256  $workspace/jdk17.tar.gz" | sha256sum --check --status ||
    fail 'Pinned JDK SHA-256 mismatch'
  tar -xzf "$workspace/jdk17.tar.gz" -C "$JAVA_HOME" --strip-components=1 ||
    fail 'Pinned JDK extract failed'
  rm -f "$workspace/jdk17.tar.gz"
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version
jdk_version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
[[ "$jdk_version" == *'17.0.20.1'* ]] || fail 'JDK toolchain version does not match pin'
printf 'jdk_version=%s\njdk_release=%s\njdk_archive_sha256=%s\n' "$jdk_version" "$jdk_release" "$jdk_sha256" >> continuum-all-systems-logs/SOURCE.txt
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

gateFailures=0
# The historical v25 owner-release protocol test deliberately replays a
# frozen real 1,095-file source manifest. Render uses a shallow checkout;
# fetch ONLY the immutable historical commit into the local object database.
# Never substitute current sources or waive the release protocol tests.
historical_sha='5270202e0699764b0eac5d4dd3e6a587b982ae05'
if ! git cat-file -e "${historical_sha}^{commit}" 2>/dev/null; then
  if git remote get-url origin >/dev/null 2>&1; then
    git -c protocol.version=2 fetch --no-tags --depth=1 origin "$historical_sha" >/dev/null 2>&1 || true
  fi
fi
if ! git cat-file -e "${historical_sha}^{commit}" 2>/dev/null; then
  frozen_tree="$(git rev-parse HEAD:docs/evidence/continuum-v25-frozen-source 2>/dev/null || true)"
  [[ "$frozen_tree" == '87e4c52821c4d5b5c61d4bab912b1dbe2aec1c25' ]] ||
    fail 'Neither original historical Git commit nor pinned 1095-blob fallback is available'
  echo 'HISTORICAL_QA_PINNED_BLOB_SNAPSHOT_READY: full protocol test will verify all bytes'
fi
[[ "$(git rev-parse --verify HEAD)" == "$sha" ]] ||
  fail 'Historical fetch changed integration HEAD'

# The historical architecture-proof suite executes a Python stdlib XML
# validator. Confirm the Render runtime can actually run it before Node QA.
command -v python3 || fail 'Python3 missing for mandatory JUnit verification'
python3 -c 'import sys, xml.etree.ElementTree as ET; print("PYTHON_QA_READY",sys.version.split()[0])' ||
  fail 'Python XML validator unavailable'
# Diagnostic only: never disable a mandatory gate if Render lacks a dependency.
for prerequisite in git node java python3 timeout readlink; do
  if command -v "$prerequisite" >/dev/null 2>&1; then
    printf "CONTINUUM_QA_TOOL_PRESENT %s=%s\n" "$prerequisite" "$(command -v "$prerequisite")"
  else
    printf "CONTINUUM_QA_TOOL_MISSING %s\n" "$prerequisite"
  fi
done
node --test scripts/continuum-atomic-acceptance.test.mjs scripts/continuum-gguf-preflight.test.mjs scripts/continuum-gguf-preflight.integration.test.mjs scripts/continuum-owner-v38-release.test.mjs scripts/continuum-owner-continuous-release.test.mjs > continuum-all-systems-logs/NODE-SPECIAL.txt 2>&1 ||
  { tail -n 50 continuum-all-systems-logs/NODE-SPECIAL.txt; gateFailures=$((gateFailures+1)); echo 'QA_RED node-special'; }
qa_proofs="$(mktemp -d "${TMPDIR:-/tmp}/continuum-integrated-qa.XXXXXXXX")"
node -p 'Date.now()' > continuum-all-systems-logs/CRITICAL-START.txt
node scripts/continuum-reliability-qa.mjs critical --out "$qa_proofs/critical" > continuum-all-systems-logs/NODE-CRITICAL.txt 2>&1 ||
  { if [[ -f "$qa_proofs/critical/report.json" ]]; then
    node - "$qa_proofs/critical/report.json" <<'NODE'
const fs=require('node:fs');
const report=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
for(const check of report.checks||[])
  console.log('NODE_GATE '+JSON.stringify({id:check.id,status:check.status,
    code:check.code,reason:check.reason,diagnostics:(check.tests?.diagnostics||[]).map(d=>({
      kind:d.kind,file:d.file,message:String(d.message||'').slice(0,240),fingerprint:d.fingerprint})).slice(0,8)}));
NODE
  fi
  tail -n 100 continuum-all-systems-logs/NODE-CRITICAL.txt
  gateFailures=$((gateFailures+1)); echo 'QA_RED node-critical'
}
if [[ -f "$qa_proofs/critical/report.json" ]]; then
  cp "$qa_proofs/critical/report.json" continuum-all-systems-logs/CRITICAL-RECEIPT.json
  node scripts/continuum-qa-evidence.mjs node "$qa_proofs/critical/report.json" "$sha" critical \
    "$(cat continuum-all-systems-logs/CRITICAL-START.txt)" > continuum-all-systems-logs/CRITICAL-PROOF.json || \
    { gateFailures=$((gateFailures+1)); echo 'QA_RED invalid-critical-receipt'; }
else
  gateFailures=$((gateFailures+1))
  echo 'QA_RED missing-critical-receipt; still checking remaining Android gates'
fi
node -p 'Date.now()' > continuum-all-systems-logs/FULL-NODE-START.txt
node scripts/continuum-reliability-qa.mjs full-node --out "$qa_proofs/full-node" > continuum-all-systems-logs/NODE-FULL.txt 2>&1 ||
  { if [[ -f "$qa_proofs/full-node/report.json" ]]; then
    node - "$qa_proofs/full-node/report.json" <<'NODE'
const fs=require('node:fs'),r=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
for(const c of r.checks||[])console.log('NODE_FULL_GATE '+JSON.stringify({
  id:c.id,status:c.status,code:c.code,reason:c.reason,
  diagnostics:(c.tests?.diagnostics||[]).slice(0,16).map(x=>({kind:x.kind,file:x.file,message:String(x.message||'').slice(0,260),fingerprint:x.fingerprint}))}));
NODE
  fi
  tail -n 100 continuum-all-systems-logs/NODE-FULL.txt
  gateFailures=$((gateFailures+1)); echo 'QA_RED full-node'
}
if [[ -f "$qa_proofs/full-node/report.json" ]]; then
  cp "$qa_proofs/full-node/report.json" continuum-all-systems-logs/FULL-NODE-RECEIPT.json
  node scripts/continuum-qa-evidence.mjs node "$qa_proofs/full-node/report.json" "$sha" full-node \
    "$(cat continuum-all-systems-logs/FULL-NODE-START.txt)" > continuum-all-systems-logs/FULL-NODE-PROOF.json || \
    { gateFailures=$((gateFailures+1)); echo 'QA_RED invalid-full-node-receipt'; }
else
  gateFailures=$((gateFailures+1))
  echo 'QA_RED missing-full-node-receipt; still checking remaining Android gates'
fi
node -p 'Date.now()' > continuum-all-systems-logs/GRADLE-START.txt
gradle --no-daemon --rerun-tasks --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode="$CONTINUUM_RELEASE_VERSION_CODE" :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug > continuum-all-systems-logs/ANDROID-TESTS.txt 2>&1 ||
  { tail -n 100 continuum-all-systems-logs/ANDROID-TESTS.txt; gateFailures=$((gateFailures+1)); echo 'QA_RED android-gradle'; }
node scripts/continuum-qa-evidence.mjs android "$PWD" \
  "$(cat continuum-all-systems-logs/GRADLE-START.txt)" > continuum-all-systems-logs/ANDROID-JUNIT-PROOF.json || \
  { gateFailures=$((gateFailures+1)); echo 'QA_RED incomplete-or-stale-android-junit'; }
if ! python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
expected={
 'at.heinz.deviceai.ShizukuBridgeLifecycleTest':14,
 'at.heinz.deviceai.ShizukuConnectionDialogTest':2,
 'at.heinz.deviceai.ui.ContinuumControlsTest':7,
 'at.heinz.deviceai.cognitive.OwnerChatIntentRouterTest':5,
 'at.heinz.deviceai.cognitive.HybridMemoryRetrieverTest':6,
}
seen={name:0 for name in expected};skips={name:0 for name in expected};failures=0
files=glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml')
if not files: raise SystemExit('QA_RED JUnit reports missing')
for p in files:
 root=ET.parse(p).getroot()
 for case in root.iter('testcase'):
  cls=case.get('classname','')
  if case.find('failure') is not None or case.find('error') is not None: failures+=1
  if cls in seen:
   seen[cls]+=1
   if case.find('skipped') is not None: skips[cls]+=1
if failures or any(seen[name]<minimum or skips[name] for name,minimum in expected.items()):
 raise SystemExit('QA_RED mandatory owner suites '+repr({'seen':seen,'skips':skips,'failures':failures}))
print('CONTINUUM_CONTINUOUS_OWNER_REQUIRED_ANDROID_GREEN',seen)
PY
then
  gateFailures=$((gateFailures+1)); echo 'QA_RED mandatory-owner-Fold7-suites'
fi
# Release compiler identity must match the ORIGINAL owner package.
# The debug .qa package above was used only for hermetic unit tests.
gradle --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumLocalRenderUnsigned=true \
  -PcontinuumStandaloneVersionCode="$CONTINUUM_RELEASE_VERSION_CODE" :app:assembleRelease :app:bundleRelease \
  > continuum-all-systems-logs/ORIGINAL-PACKAGE-ASSEMBLY.txt 2>&1 ||
  { tail -n 100 continuum-all-systems-logs/ORIGINAL-PACKAGE-ASSEMBLY.txt; gateFailures=$((gateFailures+1)); echo 'QA_RED original-package-assembly'; }
if (( gateFailures > 0 )); then
  echo "QA_SUMMARY failed_gates=$gateFailures SHA=$sha Original signing and download remain BLOCKED"
  fail 'At least one genuine QA gate is red; no owner APK is published'
fi
apk='app/build/outputs/apk/release/app-release-unsigned.apk'
[[ -s "$apk" && ! -L "$apk" ]] || fail 'Original unsigned APK missing'
aapt2 dump badging "$apk" > continuum-all-systems-logs/ORIGINAL-APK-BADGING.txt ||
  fail 'Binary package identity unavailable'
grep -Fq "package: name='at.heinz.deviceai.continuumlocal' versionCode='$CONTINUUM_RELEASE_VERSION_CODE' versionName='0.3.$((CONTINUUM_RELEASE_VERSION_CODE - 1))'" continuum-all-systems-logs/ORIGINAL-APK-BADGING.txt ||
  fail 'Wrong original update package, version or name'
if apksigner verify "$apk" >/dev/null 2>&1; then
  fail 'QA artifact is unexpectedly signed'
fi
[[ "$(git rev-parse --verify HEAD)" == "$sha" ]] || fail 'Source SHA changed during build'
git -c core.fileMode=false diff --quiet HEAD -- || fail 'Source changed during build'
hash="$(sha256sum "$apk" | cut -d' ' -f1)"
bytes="$(stat -c '%s' "$apk")"
[[ "$hash" =~ ^[0-9a-f]{64}$ && "$bytes" =~ ^[0-9]+$ && "$bytes" -gt 1000000 ]] || fail 'Bad original APK size or hash'
mkdir continuum-all-systems-site
CONTINUUM_SOURCE_SHA="$sha" CONTINUUM_APK_HASH="$hash" CONTINUUM_APK_SIZE="$bytes" CONTINUUM_JDK_VERSION="$jdk_version" CONTINUUM_JDK_ARCHIVE_SHA256="$jdk_sha256" node --input-type=module <<'NODE'
import fs from 'node:fs';
const d={schemaVersion:1,channel:'OWNER_UPDATE_PRE_SIGN_QA',status:'UNSIGNED_BUILD_VERIFIED_DEVICE_NOT_RUN',packageName:'at.heinz.deviceai.continuumlocal',versionCode:Number(process.env.CONTINUUM_RELEASE_VERSION_CODE),versionName:'0.3.'+(Number(process.env.CONTINUUM_RELEASE_VERSION_CODE)-1),sourceCommit:process.env.CONTINUUM_SOURCE_SHA,unsignedArtifactSha256:process.env.CONTINUUM_APK_HASH,unsignedArtifactBytes:Number(process.env.CONTINUUM_APK_SIZE),jdkVersion:process.env.CONTINUUM_JDK_VERSION,jdkArchiveSha256:process.env.CONTINUUM_JDK_ARCHIVE_SHA256,ownerSigned:false,apkPublished:false,deviceVerified:false};
fs.writeFileSync('continuum-all-systems-site/build.json',JSON.stringify(d,null,2)+'\n');
fs.writeFileSync('continuum-all-systems-site/index.html','<!doctype html><html lang="de"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Continuum Original-Update QA</title><style>body{font:17px sans-serif;background:#101827;color:white;max-width:720px;margin:10vh auto;padding:24px}</style><h1>Continuum Lokal – Original-Update QA</h1><p>App-Paket: at.heinz.deviceai.continuumlocal · Version 0.3.'+(Number(process.env.CONTINUUM_RELEASE_VERSION_CODE)-1)+' ('+process.env.CONTINUUM_RELEASE_VERSION_CODE+').</p><p>Quellstand: '+d.sourceCommit+'</p><p>Build geprüft, <strong>noch nicht signiert oder veröffentlicht.</strong></p><p>APK-Download erst nach Originalsignatur, Quell-SHA-Freigabe und HTTPS-Readback über den bisherigen Updatehost.</p></html>');
NODE
echo "CONTINUUM_OWNER_PRESIGN_QA_PASS SHA=$sha PACKAGE=at.heinz.deviceai.continuumlocal CODE=$CONTINUUM_RELEASE_VERSION_CODE UNSIGNED_NOT_PUBLISHED"
