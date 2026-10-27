#!/usr/bin/env bash
# Locally build the EXISTING Continuum main app. No cloud, CI, downloads or installation.
set -Eeuo pipefail
umask 077
die(){ printf 'CONTINUUM_LOCAL_OWNER_BLOCKED: %s\n' "$*" >&2; exit 69; }
usage(){ printf 'Usage: bash scripts/continuum-local-owner-apk.sh doctor|build [--offline]\n' >&2; exit 64; }
(( $# >= 1 && $# <= 2 )) || usage
mode=$1
[[ "$mode" == doctor || "$mode" == build ]] || usage
if (( $# == 2 )); then [[ "$2" == --offline && "$mode" == build ]] || usage; fi
offline=false
if (( $# == 2 )); then offline=true; fi

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
[[ -d "$root/app/src/main" && -f "$root/app/build.gradle.kts" && -f "$root/settings.gradle.kts" ]] || die 'Full main-app checkout required'
for tool in git node sha256sum keytool java; do
  command -v "$tool" >/dev/null 2>&1 || die "Missing local executable: $tool"
done
is_termux=false
if [[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]]; then is_termux=true; fi
if [[ "$is_termux" == true && -z "${JAVA_HOME:-}" && -x "${PREFIX}/lib/jvm/java-17-openjdk/bin/java" ]]; then
  export JAVA_HOME="${PREFIX}/lib/jvm/java-17-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [[ -f "$HOME/android-sdk/platforms/android-36/android.jar" ]]; then
    export ANDROID_HOME="$HOME/android-sdk"
  else
    die 'Android SDK 36 not found; initial local installation required'
  fi
fi
[[ -f "$ANDROID_HOME/platforms/android-36/android.jar" ]] || die 'Missing local Android SDK 36'
java_bin="$(command -v java)"
if [[ -n "${JAVA_HOME:-}" ]]; then
  [[ -x "$JAVA_HOME/bin/java" ]] || die 'Invalid JAVA_HOME'
  java_bin="$JAVA_HOME/bin/java"
fi
java_banner="$("$java_bin" -version 2>&1 | head -n 1)"
[[ "$java_banner" =~ \"17([.\"]|$) ]] || die 'JDK 17 required'
if [[ -n "${CONTINUUM_GRADLE_PATH:-}" ]]; then
  gradle_bin="$CONTINUUM_GRADLE_PATH"
elif command -v gradle >/dev/null 2>&1; then
  gradle_bin="$(command -v gradle)"
else
  gradle_bin="$HOME/continuum-toolchains/gradle-8.11.1/bin/gradle"
fi
[[ -x "$gradle_bin" ]] || die 'Missing local Gradle 8.11.1'
gradle_version="$("$gradle_bin" --version 2>&1)" || die 'Gradle startup failed'
[[ "$gradle_version" == *'Gradle 8.11.1'* ]] || die 'Gradle 8.11.1 required'
node_major="$(node -p 'Number(process.versions.node.split(".")[0])')"
[[ "$node_major" == 20 || "$node_major" == 22 ]] || die 'Node 20/22 required'
if [[ "$is_termux" == true ]]; then
  aapt2_bin="${CONTINUUM_AAPT2_PATH:-$PREFIX/bin/aapt2}"
else
  aapt2_bin="${CONTINUUM_AAPT2_PATH:-$ANDROID_HOME/build-tools/35.0.0/aapt2}"
fi
[[ -x "$aapt2_bin" ]] || die 'Native local aapt2 missing'
if [[ -x "$ANDROID_HOME/build-tools/35.0.0/apksigner" ]]; then
  signer_bin="$ANDROID_HOME/build-tools/35.0.0/apksigner"
else
  signer_bin="$(command -v apksigner || true)"
fi
[[ -n "$signer_bin" && -x "$signer_bin" ]] || die 'Android apksigner unavailable'
expected_signer='2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc'
app_package='at.heinz.deviceai.mentorpaired'
head="$(git -C "$root" rev-parse HEAD 2>/dev/null)" || die 'Missing Git HEAD'
[[ "$head" =~ ^[a-f0-9]{40}$ ]] || die 'Invalid Git HEAD'
[[ -z "$(git -C "$root" status --porcelain --untracked-files=normal)" ]] || die 'Source must be clean'
if [[ "$mode" == doctor ]]; then
  printf 'CONTINUUM_LOCAL_DOCTOR=READY\nsource_sha=%s\ntermux=%s\nexpected_owner_signer=%s\n' "$head" "$is_termux" "$expected_signer"
  if [[ -n "${CONTINUUM_MENTOR_SIGNING_FILE:-}" && -f "$CONTINUUM_MENTOR_SIGNING_FILE" ]]; then
    echo 'original_keystore=AVAILABLE_UNVERIFIED'
  else
    echo 'original_keystore=NOT_PRESENT'
  fi
  exit 0
fi
[[ "${CONTINUUM_OWNER_LOCAL_APPROVED_SHA:-}" == "$head" ]] || die 'Owner approval of exact Git HEAD required'
[[ -n "${CONTINUUM_MENTOR_SIGNING_FILE:-}" && -f "$CONTINUUM_MENTOR_SIGNING_FILE" && ! -L "$CONTINUUM_MENTOR_SIGNING_FILE" ]] || die 'Original owner P12 key missing'
[[ -n "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] || die 'Owner signing password missing'
key_info="$(keytool -list -v -storetype PKCS12 -keystore "$CONTINUUM_MENTOR_SIGNING_FILE" -alias deviceai-mentor -storepass:env CONTINUUM_MENTOR_SIGNING_PASSWORD 2>/dev/null)" || die 'Owner P12 password or alias invalid'
key_fp="$(printf '%s\n' "$key_info" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
unset key_info
[[ "$key_fp" == "$expected_signer" ]] || die 'Original owner signing certificate does not match'
output_dir="${CONTINUUM_LOCAL_OWNER_OUTPUT_DIR:-$HOME/.local/state/device-ai-continuum/owner-apk}"
mkdir -p -- "$output_dir"
output_dir="$(cd -- "$output_dir" && pwd -P)"
case "$output_dir" in "$root"|"$root"/*) die 'Build artifact output must be outside source' ;; esac
[[ "$head" == "$(git -C "$root" rev-parse HEAD)" ]] || die 'Git HEAD changed'
flags=(--no-daemon --max-workers=2 --console=plain
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m'
  -Pkotlin.compiler.execution.strategy=in-process
  -PdeviceAiMentorPreview=true
  -PdeviceAiOwnerSignedUpdate=true
  -PcontinuumOwnerLocalVersion26=true)
if [[ "$is_termux" == true ]]; then
  flags+=("-Pandroid.aapt2FromMavenOverride=$aapt2_bin" -PdeviceAiMobileAidlFallback=true)
fi
if [[ "$offline" == true ]]; then flags+=(--offline); fi
printf 'CONTINUUM_LOCAL_BUILD_START source=%s package=%s code26 offline=%s\n' "$head" "$app_package" "$offline"
cd "$root"
"$gradle_bin" "${flags[@]}" :app:testDebugUnitTest :app:lintDebug :app:assembleDebug || die 'Gradle test/lint/build failed'
[[ "$(git rev-parse HEAD)" == "$head" && -z "$(git status --porcelain --untracked-files=normal)" ]] || die 'Source mutated while building'
apk="$root/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" && ! -L "$apk" && -s "$apk" ]] || die 'Build yielded no APK'
"$signer_bin" verify --verbose --print-certs "$apk" > "$output_dir/SIGNATURE-$head.txt" || die 'APK signature invalid'
apk_fp="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$output_dir/SIGNATURE-$head.txt" | head -n 1 | tr '[:upper:]' '[:lower:]')"
[[ "$apk_fp" == "$expected_signer" ]] || die 'APK has wrong signer'
"$aapt2_bin" dump badging "$apk" > "$output_dir/PACKAGE-$head.txt" || die 'Cannot inspect APK package'
grep -Fq "package: name='$app_package' versionCode='26' versionName='0.3.25'" "$output_dir/PACKAGE-$head.txt" || die 'APK package/version does not match existing Continuum update'
digest="$(sha256sum "$apk" | cut -d' ' -f1)"
[[ "$digest" =~ ^[a-f0-9]{64}$ ]] || die 'Invalid APK SHA-256'
dest="$output_dir/DeviceAI-Continuum-v0.3.25-$digest.apk"
if [[ -e "$dest" ]]; then
  [[ -f "$dest" && ! -L "$dest" && "$(sha256sum "$dest" | cut -d' ' -f1)" == "$digest" ]] || die 'Existing output file differs'
else
  cp -- "$apk" "$dest"
  [[ "$(sha256sum "$dest" | cut -d' ' -f1)" == "$digest" ]] || die 'APK copy SHA mismatch'
fi
printf 'source_sha=%s\npackage=%s\nversion_code=26\nversion_name=0.3.25\nowner_signer_sha256=%s\napk_sha256=%s\nartifact=%s\nstatus=SIGNED_NOT_DEVICE_INSTALLED\n' "$head" "$app_package" "$expected_signer" "$digest" "$dest" > "$output_dir/BUILD-INFO-$head.txt"
printf 'CONTINUUM_OWNER_APK=%s\nCONTINUUM_APK_SHA256=%s\nSOURCE_SHA=%s\n' "$dest" "$digest" "$head"
