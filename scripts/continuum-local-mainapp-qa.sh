#!/usr/bin/env bash
# Local, full MAIN-APP QA. Uses disposable debug signing, NEVER the owner key.
set -Eeuo pipefail
umask 077
fail(){ printf 'CONTINUUM_QA_BLOCKED: %s\n' "$*" >&2; exit 69; }
[[ $# -eq 0 || ( $# -eq 1 && "$1" == --offline ) ]] ||
  { echo 'Usage: bash scripts/continuum-local-mainapp-qa.sh [--offline]' >&2; exit 64; }
[[ -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] ||
   fail 'Never expose owner signing credentials to QA'
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
[[ -f "$root/app/build.gradle.kts" && -f "$root/settings.gradle.kts" ]] ||
  fail 'Full existing main-app checkout required'
# Match the exact owner doctor toolchain including JDK17, SDK36, native aapt2.
if [[ "${PREFIX:-}" == /data/data/com.termux/files/usr* &&
      -z "${JAVA_HOME:-}" &&
      -x "$PREFIX/lib/jvm/java-17-openjdk/bin/java" ]]; then
  export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  export ANDROID_HOME="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
fi
bash "$root/scripts/continuum-local-owner-apk.sh" doctor ||
  fail 'Full local Android toolchain doctor failed'
head="$(git -C "$root" rev-parse --verify HEAD 2>/dev/null)" ||
  fail 'No Git HEAD'
[[ "$head" =~ ^[0-9a-f]{40}$ &&
   "${CONTINUUM_LOCAL_QA_APPROVED_SHA:-}" == "$head" ]] ||
  fail 'Explicit same-source QA SHA approval missing'
[[ -z "$(git -C "$root" status --porcelain --untracked-files=normal)" ]] ||
  fail 'Git source unclean'
version="${CONTINUUM_OWNER_NEXT_VERSION_CODE:-26}"
[[ "$version" =~ ^(2[6-9]|[3-9][0-9]|[1-9][0-9]{2,3})$ ]] &&
  (( version <= 9999 )) || fail 'Invalid canonical versionCode'
version_name="0.3.$((version - 1))"
if [[ -n "${CONTINUUM_GRADLE_PATH:-}" ]]; then
  gradle_bin="$CONTINUUM_GRADLE_PATH"
elif command -v gradle >/dev/null 2>&1; then
  gradle_bin="$(command -v gradle)"
else
  gradle_bin="$HOME/continuum-toolchains/gradle-8.11.1/bin/gradle"
fi
[[ -x "$gradle_bin" ]] || fail 'Gradle unavailable'
if [[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]]; then
  aapt2="${CONTINUUM_AAPT2_PATH:-$PREFIX/bin/aapt2}"
  is_termux=true
else
  aapt2="${CONTINUUM_AAPT2_PATH:-$ANDROID_HOME/build-tools/35.0.0/aapt2}"
  is_termux=false
fi
[[ -x "$aapt2" ]] || fail 'Native aapt2 unavailable'
if [[ -x "$ANDROID_HOME/build-tools/35.0.0/apksigner" ]]; then
  signer="$ANDROID_HOME/build-tools/35.0.0/apksigner"
else
  signer="$(command -v apksigner || true)"
fi
[[ -n "$signer" && -x "$signer" ]] || fail 'apksigner unavailable'
out="${CONTINUUM_LOCAL_QA_OUTPUT_DIR:-$HOME/.local/state/device-ai-continuum/mainapp-qa}"
mkdir -p -- "$out"
out="$(cd -- "$out" && pwd -P)"
case "$out" in "$root"|"$root"/*) fail 'Output within checkout';; esac
flags=(--no-daemon --max-workers=2 --console=plain
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m'
  -Pkotlin.compiler.execution.strategy=in-process
  -PdeviceAiMentorPreview=true -PcontinuumOwnerLocalQa=true
  "-PcontinuumOwnerLocalVersionCode=$version"
  "-Pandroid.aapt2FromMavenOverride=$aapt2")
if [[ "$is_termux" == true ]]; then flags+=(-PdeviceAiMobileAidlFallback=true); fi
if [[ $# -eq 1 ]]; then flags+=(--offline); fi
log="$out/QA-GRADLE-$head-$version.log"
printf 'CONTINUUM_MAINAPP_QA_START=%s\n' "$head"
cd "$root"
"$gradle_bin" "${flags[@]}" :app:testDebugUnitTest :app:lintDebug \
    :app:compileDebugKotlin :app:assembleDebug > "$log" 2>&1 || {
  tail -n 70 "$log" >&2 || true
  fail "Android compile/test/lint/assemble failed. Log: $log"
}
[[ "$(git rev-parse HEAD)" == "$head" &&
   -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  fail 'Source mutated during compilation'
apk="$root/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$apk" && ! -L "$apk" ]] || fail 'No genuine APK'
signature="$out/QA-SIGNATURE-$head-$version.txt"
package="$out/QA-PACKAGE-$head-$version.txt"
"$signer" verify --verbose --print-certs "$apk" > "$signature" ||
  fail 'APK signature invalid'
actual_signer="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$signature" |
    head -n 1 | tr '[:upper:]' '[:lower:]')"
[[ "$actual_signer" =~ ^[a-f0-9]{64}$ ]] || fail 'Signer identity missing'
owner_signer='2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc'
[[ "$actual_signer" != "$owner_signer" ]] || fail 'QA MUST NOT use owner certificate'
"$aapt2" dump badging "$apk" > "$package" || fail 'Cannot inspect package'
grep -Fq "package: name='at.heinz.deviceai.mentorpaired' versionCode='$version' versionName='$version_name'" "$package" ||
  fail 'Wrong main-app package or version'
hash="$(sha256sum "$apk" | cut -d' ' -f1)"
[[ "$hash" =~ ^[a-f0-9]{64}$ ]] || fail 'Missing APK SHA-256'
result="$out/Continuum-MAINAPP-QA-DO-NOT-INSTALL-$version-$hash.apk"
if [[ -e "$result" ]]; then
  [[ -f "$result" && ! -L "$result" &&
     "$(sha256sum "$result" | cut -d' ' -f1)" == "$hash" ]] ||
    fail 'Conflicting previous output'
else
  cp -- "$apk" "$result"
  [[ "$(sha256sum "$result" | cut -d' ' -f1)" == "$hash" ]] ||
    fail 'Output copy digest mismatch'
fi
printf 'source_sha=%s\npackage=at.heinz.deviceai.mentorpaired\nversion_code=%s\nversion_name=%s\nqa_signer=%s\nowner_signer=FALSE\nowner_installable=FALSE\napk_sha256=%s\nartifact=%s\nstatus=MAINAPP_QA_NONOWNER_VERIFIED\n' \
  "$head" "$version" "$version_name" "$actual_signer" "$hash" "$result" \
  > "$out/QA-STATUS-$head-$version.txt"
printf 'CONTINUUM_MAINAPP_QA_PASSED=%s\nCONTINUUM_MAINAPP_QA_NOT_OWNER_INSTALLABLE=TRUE\nCONTINUUM_MAINAPP_QA_APK=%s\n' "$head" "$result"
