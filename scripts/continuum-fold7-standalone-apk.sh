#!/usr/bin/env bash
# Full Continuum Android main app, independently signed and built locally.
# Does not install, remove, downgrade or overwrite the historic Mentor Pair owner app.
set +x
set -Eeuo pipefail
umask 077
die() { printf 'CONTINUUM_STANDALONE_BLOCKED: %s\n' "$*" >&2; exit 69; }
[[ $# -ge 1 && $# -le 2 && "$1" == "build" ]] || {
  printf 'Usage: bash scripts/continuum-fold7-standalone-apk.sh build [versionCode>=26]\n' >&2
  exit 64
}
version="${2:-26}"
[[ "$version" =~ ^(2[6-9]|[3-9][0-9]|[1-9][0-9]{2,3})$ ]] &&
  (( version <= 9999 )) || die 'Version code must be 26..9999'
version_name="0.3.$((version - 1))"
root="$(cd -- "$(dirname -- "$0")/.." && pwd -P)"
cd "$root"
[[ -f app/build.gradle.kts && -f app/src/main/AndroidManifest.xml ]] ||
  die 'Existing complete Continuum main app source missing'
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  die 'Build only a clean, reviewed Git checkout'
# A clean checkout alone does not authorize the original P12 to sign its source.
# Require an exact owner-reviewed commit even when this builder is invoked directly.
sha="$(git rev-parse --verify HEAD)" || die 'Git source identity unavailable'
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || die 'Invalid Git source SHA'
[[ "${CONTINUUM_OWNER_LOCAL_APPROVED_SHA:-}" == "$sha" ]] ||
  die 'EXACT_OWNER_SOURCE_APPROVAL_REQUIRED: export the reviewed full commit SHA'
branch="$(git symbolic-ref --quiet --short HEAD)" ||
  die 'Detached HEAD cannot be an owner release candidate'
case "$branch" in main|master|prod|production|release|main/*|master/*|prod/*|production/*|release/*)
  die 'A protected branch cannot be an owner release candidate' ;;
esac
origin="$(git remote get-url origin)" || die 'Original GitHub remote unavailable'
case "$origin" in
  https://github.com/Ullmann27/device-ai|https://github.com/Ullmann27/device-ai.git|git@github.com:Ullmann27/device-ai.git|ssh://git@github.com/Ullmann27/device-ai.git) ;;
  *) die 'Untrusted Git remote cannot use the original owner P12' ;;
esac
unset origin branch
# The one-time owner bootstrap must not produce an APK lacking the update
# button. This test runs before the owner P12 password is requested.
[[ -f scripts/continuum-one-tap-updater-contract.test.mjs ]] ||
  die 'ONE_TAP_UPDATE_CONTRACT_MISSING: refusing a release without the update button'
node --test scripts/continuum-one-tap-updater-contract.test.mjs scripts/continuum-owner-release-stage.test.mjs ||
  die 'ONE_TAP_UPDATE_CONTRACT_FAILED: refusing a release without a verified update path'
[[ -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" && -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" ]] ||
  die 'Do not load historic owner-signing credentials into the standalone build'

[[ "${PREFIX:-}" == /data/data/com.termux/files/usr* ]] ||
  die 'This signing/build entry point is for the local Fold7 Termux toolchain'
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
gradle="$HOME/continuum-toolchains/gradle-8.11.1/bin/gradle"
aapt2="$PREFIX/bin/aapt2"
signer="$ANDROID_HOME/build-tools/35.0.0/apksigner"
for cmd in git keytool sha256sum node; do command -v "$cmd" >/dev/null || die "Missing $cmd"; done
[[ -x "$gradle" && -x "$aapt2" && -x "$signer" &&
   -f "$ANDROID_HOME/platforms/android-36/android.jar" ]] ||
  die 'Previously verified Fold7 JDK17/Gradle8.11.1/SDK36 toolchain unavailable'
[[ "$(java -version 2>&1 | head -n1)" == *'"17.'* ]] || die 'Java 17 required'
[[ "$("$gradle" --version 2>&1)" == *'Gradle 8.11.1'* ]] ||
  die 'Gradle 8.11.1 required'

keydir="$HOME/.local/share/device-ai-continuum/signing"
[[ -d "$keydir" && ! -L "$keydir" ]] || die 'Original signing directory missing or unsafe; restore backup'
keyfile="$keydir/continuum-local.p12"
fingerprint="$keydir/continuum-local-cert-sha256.txt"
expected_fp='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'
[[ -s "$keyfile" && -s "$fingerprint" && ! -L "$keyfile" && ! -L "$fingerprint" ]] ||
  die 'Original local P12 and fingerprint required; restore backup, never create a replacement'
trusted_fp="$(cat "$fingerprint")"
[[ "$trusted_fp" == "$expected_fp" ]] || die 'Trusted signer differs from original Continuum Lokal certificate'
chmod 700 "$keydir"
chmod 600 "$keyfile"
qa_base="$HOME/.local/state/device-ai-continuum/standalone-qa"
mkdir -p -- "$qa_base"
qa_evidence="$(mktemp -d "$qa_base/pre-sign-$version-XXXXXX")"
env -u CONTINUUM_LOCAL_SIGNING_FILE -u CONTINUUM_LOCAL_SIGNING_PASSWORD \
  CONTINUUM_FUSION_REGRESSION_OUTPUT="$qa_evidence" \
  bash scripts/continuum-fusion-full-regression.sh "$sha" || die 'Full unsigned standalone regression failed; signing not started'
[[ -t 0 ]] || die 'Interactive password input required; never enter secrets in chat'
IFS= read -r -s -p 'Lokales Signierpasswort: ' pass
printf '\n'
[[ -n "$pass" ]] || die 'Signing password empty'
export CONTINUUM_LOCAL_SIGNING_PASSWORD="$pass"
unset pass
trap 'unset CONTINUUM_LOCAL_SIGNING_PASSWORD CONTINUUM_LOCAL_SIGNING_FILE' EXIT
export CONTINUUM_LOCAL_SIGNING_FILE="$keyfile"
keyinfo="$(keytool -list -v -storetype PKCS12 \
  -J-Duser.language=en -J-Duser.country=US \
  -alias continuum-local -keystore "$keyfile" \
  -storepass:env CONTINUUM_LOCAL_SIGNING_PASSWORD 2>/dev/null)" ||
  die 'Wrong password or unreadable local signing key'
actual_fp="$(printf '%s\n' "$keyinfo" | sed -n 's/^[[:space:]]*SHA256: //p' |
  head -n 1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
unset keyinfo
[[ "$actual_fp" =~ ^[a-f0-9]{64}$ ]] || die 'Cannot read key certificate SHA256'
[[ "$actual_fp" == "$expected_fp" && "$trusted_fp" == "$actual_fp" ]] ||
  die 'Local signing certificate changed; restore the original P12'

# Full existing Android app sources, native Android production release.
flags=(--offline --no-daemon --console=plain --max-workers=2
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m'
  -Pkotlin.compiler.execution.strategy=in-process
  -PdeviceAiMentorPreview=true
  -PcontinuumLocalStandalone=true
  "-PcontinuumStandaloneVersionCode=$version"
  -PdeviceAiMobileAidlFallback=true
  "-Pandroid.aapt2FromMavenOverride=$aapt2")
printf 'CONTINUUM_LOCAL_RELEASE_START source=%s versionCode=%s\n' "$sha" "$version"
"$gradle" "${flags[@]}" :app:lintRelease :app:assembleRelease ||
  die 'Local main-app Android test/lint/release assembly failed'
[[ "$sha" == "$(git rev-parse HEAD)" &&
   -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  die 'Source changed during release build'

apk="$root/app/build/outputs/apk/release/app-release.apk"
[[ -s "$apk" && ! -L "$apk" ]] || die 'Real signed Release APK missing'
state="$HOME/.local/state/device-ai-continuum/standalone-release"
mkdir -p "$state"
"$signer" verify --verbose --print-certs "$apk" > "$state/SIGNATURE-$sha.txt" ||
  die 'APK signing verification failed'
apk_fp="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
  "$state/SIGNATURE-$sha.txt" | head -n 1 | tr '[:upper:]' '[:lower:]')"
[[ "$apk_fp" == "$actual_fp" ]] || die 'Release APK signer differs from persistent local P12'
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' \
  "$state/SIGNATURE-$sha.txt" || die 'Android v2 signature verification missing'
"$aapt2" dump badging "$apk" > "$state/PACKAGE-$sha.txt" ||
  die 'APK identity cannot be inspected'
grep -Fq "package: name='at.heinz.deviceai.continuumlocal' versionCode='$version' versionName='$version_name'" \
  "$state/PACKAGE-$sha.txt" || die 'App package/version does not match local main-app release'
hash="$(sha256sum "$apk" | cut -d ' ' -f1)"
[[ "$hash" =~ ^[a-f0-9]{64}$ ]] || die 'Invalid APK checksum'
output="$HOME/storage/downloads/Continuum_back_up"
[[ -d "$HOME/storage/downloads" ]] || die 'Termux storage unavailable; original owner files untouched'
mkdir -p "$output"
name="Continuum-Lokal-v$version_name-$hash.apk"
dest="$output/$name"
if [[ -e "$dest" ]]; then
  [[ -f "$dest" && ! -L "$dest" &&
     "$(sha256sum "$dest" | cut -d ' ' -f1)" == "$hash" ]] ||
    die 'Existing downloaded APK name conflicts with a different file'
else
  cp -- "$apk" "$dest" || die 'Could not copy verified release to Downloads'
fi
[[ "$(sha256sum "$dest" | cut -d ' ' -f1)" == "$hash" ]] ||
  die 'Downloaded APK verification failed'
printf 'source_sha=%s\npackage=at.heinz.deviceai.continuumlocal\nversion_code=%s\nversion_name=%s\nsigner_sha256=%s\napk_sha256=%s\nartifact=%s\nstatus=LOCAL_RELEASE_SIGNED_NOT_DEVICE_INSTALLED\n' \
  "$sha" "$version" "$version_name" "$actual_fp" "$hash" "$dest" > "$state/BUILD-INFO-$sha-$version.txt"
printf 'CONTINUUM_LOCAL_APK_READY=%s\nCONTINUUM_LOCAL_SIGNER=%s\n' "$dest" "$actual_fp"
printf 'WICHTIG: Die P12 unter ~/.local/share/device-ai-continuum/signing/ sicher OFFLINE auf zweitem Medium sichern.\n'
printf 'ALTE CONTINUUM-V25-APP NICHT DEINSTALLIEREN. Kein Import ungepruefter Besitzer-Daten.\n'
