#!/usr/bin/env bash
# Owner-authorized ORIGINAL Continuum Lokal in-place update, versionCode38, preserving installed app data.
# Never publish an APK whose package, signer, source or host differs from the
# already-installed owner application's fixed update policy.
set -Eeuo pipefail
set +x
umask 077
fail() { printf 'CONTINUUM_OWNER_RENDER_RELEASE_BLOCKED: %s\n' "$*" >&2; exit 69; }
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$root"

expected_host='continuum-owner-v23-release-preflight.onrender.com'
expected_package='at.heinz.deviceai.continuumlocal'
expected_signer='6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23'
sha="$(git rev-parse --verify HEAD)" || fail 'No Git HEAD'
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || fail 'Invalid commit'
# P0 freeze remains in force until exact SHA release authorization.
[[ "${CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER:-}" == "$sha" ]] ||
  fail 'P0 release freeze active: owner signing and publishing are blocked'
[[ "${CONTINUUM_RELEASE_APPROVED_SHA:-}" == "$sha" ]] ||
  fail 'Exact source SHA not approved by owner'
[[ "${CONTINUUM_RELEASE_HOST:-}" == "$expected_host" ]] ||
  fail 'Update host differs from the installed application'
# This service must be the owner-approved publisher for the PINNED host, not
# a new QA site whose publicly served URL the existing app cannot reach.
[[ "${RENDER_SERVICE_NAME:-}" == "continuum-owner-v23-release-preflight" ]] ||
  fail 'Only the pinned Render owner update site may publish this release'
[[ -z "${CONTINUUM_MENTOR_SIGNING_FILE:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_PASSWORD:-}" &&
   -z "${CONTINUUM_MENTOR_SIGNING_P12_BASE64:-}" ]] ||
  fail 'Legacy Mentor Pair credentials must not enter Continuum Lokal'
[[ -n "${CONTINUUM_LOCAL_SIGNING_P12_BASE64:-}" &&
   -n "${CONTINUUM_LOCAL_SIGNING_PASSWORD:-}" ]] ||
  fail 'Original Continuum Lokal signing material unavailable'
[[ -z "$(git -c core.fileMode=false diff --name-only HEAD --)" ]] ||
  fail 'Tracked sources were modified'
[[ -z "$(git ls-files --others --exclude-standard -- app/src scripts backend/src backend/test .github/continuum)" ]] ||
  fail 'Untracked executable sources'
[[ "${CONTINUUM_RELEASE_VERSION_CODE:-}" =~ ^[0-9]+$ ]] ||
  fail 'Release versionCode required'
version="$CONTINUUM_RELEASE_VERSION_CODE"
[[ "$version" == "38" ]] || fail 'This pinned candidate accepts only versionCode 38'
version_name="0.3.$((version - 1))"

# Refuse to run the existing QA if source is different from the signed build.
# Full test/lint and release compile share one precise checked-out source.
env -u CONTINUUM_LOCAL_SIGNING_P12_BASE64 \
  -u CONTINUUM_LOCAL_SIGNING_PASSWORD \
  CONTINUUM_OWNER_RELEASE_QA=1 bash scripts/render-continuum-integration-qa.sh ||
  fail 'Node, Android unit tests, lint or unsigned QA build failed'
[[ "$(git rev-parse --verify HEAD)" == "$sha" ]] || fail 'HEAD changed during QA'
workspace="$root/.continuum-render-build"
export JAVA_HOME="$workspace/jdk17"
export ANDROID_HOME="$workspace/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$workspace/gradle-cache"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/build-tools/35.0.0:$workspace/gradle-8.11.1/bin:$PATH"
[[ -x "$JAVA_HOME/bin/keytool" && -x "$workspace/gradle-8.11.1/bin/gradle" ]] ||
  fail 'Verified QA toolchain unavailable'
signing_dir="$(mktemp -d "${TMPDIR:-/tmp}/continuum-owner-signing.XXXXXXXX")" ||
  fail 'Private signing directory unavailable'
chmod 700 "$signing_dir"
cleanup() { rm -rf -- "$signing_dir"; }
trap cleanup EXIT
export CONTINUUM_LOCAL_SIGNING_FILE="$signing_dir/continuum-local.p12"
printf '%s' "$CONTINUUM_LOCAL_SIGNING_P12_BASE64" | base64 --decode > "$CONTINUUM_LOCAL_SIGNING_FILE" ||
  fail 'Owner P12 could not be decoded'
chmod 600 "$CONTINUUM_LOCAL_SIGNING_FILE"
unset CONTINUUM_LOCAL_SIGNING_P12_BASE64
key_metadata="$("$JAVA_HOME/bin/keytool" -list -v -storetype PKCS12 \
  -J-Duser.language=en -J-Duser.country=US \
  -alias continuum-local -keystore "$CONTINUUM_LOCAL_SIGNING_FILE" \
  -storepass:env CONTINUUM_LOCAL_SIGNING_PASSWORD 2>/dev/null)" ||
  fail 'Original owner signing key unavailable or invalid'
grep -Fq 'Entry type: PrivateKeyEntry' <<<"$key_metadata" ||
  fail 'Owner P12 alias does not contain a private signing key'
key_fingerprint="$(sed -n 's/^[[:space:]]*SHA256: //p' <<<"$key_metadata" |
  head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
unset key_metadata
[[ "$key_fingerprint" == "$expected_signer" ]] ||
  fail 'Wrong signing certificate, refusing in-place update'

"$workspace/gradle-8.11.1/bin/gradle" --no-daemon --max-workers=2 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true \
  -PcontinuumLocalStandalone=true \
  "-PcontinuumStandaloneVersionCode=$version" \
  :app:lintRelease :app:assembleRelease ||
  fail 'Signed release compilation failed'
[[ "$(git rev-parse --verify HEAD)" == "$sha" ]] ||
  fail 'Source SHA changed during release build'
apk="$root/app/build/outputs/apk/release/app-release.apk"
[[ -s "$apk" && ! -L "$apk" ]] || fail 'No signed APK'
apksigner="$ANDROID_HOME/build-tools/35.0.0/apksigner"
aapt2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
[[ -x "$apksigner" && -x "$aapt2" ]] ||
  fail 'Android signature and package verification tools missing'
signed_info="$("$apksigner" verify --verbose --print-certs "$apk")" ||
  fail 'APK certificate verification failed'
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$signed_info" ||
  fail 'No valid APK v2 signature'
apk_signer="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$signed_info" |
  head -n1 | tr '[:upper:]' '[:lower:]')"
[[ "$apk_signer" == "$expected_signer" ]] ||
  fail 'Built APK signer differs from installed owner certificate'
badging="$("$aapt2" dump badging "$apk")" || fail 'Cannot inspect built APK package'
grep -Fq "package: name='$expected_package' versionCode='$version' versionName='$version_name'" <<<"$badging" ||
  fail 'Package identity/version does not match installed Continuum Lokal'
size="$(stat -c '%s' "$apk")"
[[ "$size" =~ ^[0-9]+$ && "$size" -ge 1000000 && "$size" -le 2000000000 ]] ||
  fail 'APK byte size outside owner update policy'
digest="$(sha256sum "$apk" | cut -d' ' -f1)"
[[ "$digest" =~ ^[0-9a-f]{64}$ ]] || fail 'Invalid APK SHA-256'

out="$root/continuum-m8-owner-release/continuum-local"
mkdir -p "$out"
name="Continuum-Lokal-$digest.apk"
[[ ! -e "$out/$name" ]] || fail 'Release artifact already exists'
install -m 600 "$apk" "$out/$name" || fail 'Cannot stage signed APK'
[[ "$(sha256sum "$out/$name" | cut -d' ' -f1)" == "$digest" ]] ||
  fail 'Staged APK changed'
export CONTINUUM_RELEASE_SHA="$sha"
export CONTINUUM_RELEASE_DIGEST="$digest"
export CONTINUUM_RELEASE_SIZE="$size"
export CONTINUUM_RELEASE_VERSION_NAME="$version_name"
# Strict JSON manifest, matching EXACTLY the current installed app catalog.
node --input-type=module - "$out/latest.json" <<'NODE'
import { writeFileSync } from 'node:fs';
const target = process.argv[2];
const sha = process.env.CONTINUUM_RELEASE_SHA;
const digest = process.env.CONTINUUM_RELEASE_DIGEST;
const size = Number(process.env.CONTINUUM_RELEASE_SIZE);
const name = process.env.CONTINUUM_RELEASE_VERSION_NAME;
const host = 'continuum-owner-v23-release-preflight.onrender.com';
const release = {
  schema: 1,
  channel: 'OWNER_RELEASE_CANDIDATE',
  status: 'SIGNED_STAGED_DEVICE_NOT_RUN',
  packageName: 'at.heinz.deviceai.continuumlocal',
  versionCode: 38,
  versionName: name,
  apkUrl: 'https://' + host + '/continuum-local/Continuum-Lokal-' + digest + '.apk',
  apkSha256: digest,
  byteLength: size,
  sourceCommit: sha,
  signerSha256: '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23',
  approved: false,
  notes: 'Originalsigniertes, nicht freigegebenes Update: Fold7-Abnahme und separate Besitzerfreigabe erforderlich.'
};
if (!Number.isSafeInteger(size) || size < 1_000_000 || size > 2_000_000_000 ||
    !/^[a-f0-9]{40}$/.test(sha) || !/^[a-f0-9]{64}$/.test(digest) ||
    name !== '0.3.37') process.exit(69);
writeFileSync(target, JSON.stringify(release) + '\n', {flag:'wx', mode:0o600});
NODE
test -s "$out/latest.json" || fail 'No valid owner update catalog'
# Publishing the site is a separate Render deployment operation; do not claim
# success unless the Render hosting service actually serves these bytes.
printf 'source_sha=%s\nversion=0.3.37\npackage=%s\nsigner_sha256=%s\napk_sha256=%s\nstatus=SIGNED_STAGED_FOR_PINNED_HOST\n' \
  "$sha" "$expected_package" "$expected_signer" "$digest" \
  > "$root/continuum-m8-owner-release/BUILD-INFO.txt"
echo 'CONTINUUM_OWNER_RENDER_SIGNED_STAGED_FOR_PINNED_HOST'
