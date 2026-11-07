#!/usr/bin/env bash
# Release the previous exact-source, fully green Code48 APK.
set -Eeuo pipefail
set +x
umask 077
fail() { printf '::error::CODE48_SIGN_%s\n' "$1" >&2; exit 69; }
repo="Ullmann27/device-ai"
source_sha="c9fffb7dda9c173b6c5ccd4104672b09fd94de15"
qa_run="42364691949"
qa_workflow_sha="5324223e591718888582f6fad28c1e1fdb630a45"
fingerprint="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
pkg="at.heinz.deviceai.continuumlocal"
tag="continuum-local-code48-$source_sha"
[[ "$GITHUB_REPOSITORY" == "$repo" ]] || fail WRONG_REPOSITORY
[[ "$GITHUB_REF" == 'refs/heads/codex/continuum-code48-github-owner-20261107' ]] || fail WRONG_BRANCH
for cmd in gh jq keytool base64 sha256sum stat; do command -v "$cmd" >/dev/null || fail TOOL_MISSING; done
tmp="$(mktemp -d)"
chmod 700 "$tmp"
trap 'rm -rf -- "$tmp"' EXIT

run="$(gh api "repos/$repo/actions/runs/$qa_run")" || fail QA_RUN_UNREADABLE
jq -e --arg sha "$qa_workflow_sha" '.head_sha==$sha and .head_branch=="codex/continuum-code48-github-owner-20261107" and .event=="push" and .status=="completed"' <<< "$run" >/dev/null || fail QA_IDENTITY_MISMATCH
unset run
jobs="$(gh api "repos/$repo/actions/runs/$qa_run/jobs?per_page=100")" || fail QA_JOBS_UNREADABLE
jq -e '[.jobs[]|select(.name=="node-backend" and .conclusion=="success")]|length==1' <<< "$jobs" >/dev/null || fail NODE_BACKEND_NOT_GREEN
jq -e '[.jobs[]|select(.name=="android-unsigned" and .conclusion=="success")]|length==1' <<< "$jobs" >/dev/null || fail ANDROID_QA_NOT_GREEN
unset jobs
echo CHECKPOINT=PRIOR_SOURCE_BOUND_QA_JOBS_GREEN

mkdir "$tmp/unsigned"
gh run download "$qa_run" --repo "$repo" --name continuum-code48-github-unsigned-not-installable --dir "$tmp/unsigned" >/dev/null || fail APK_ARTIFACT_DOWNLOAD_FAILED
unsigned="$tmp/unsigned/Continuum-Lokal-Code48-UNSIGNED.apk"
test -s "$unsigned" || fail UNSIGNED_APK_MISSING
test -s "$tmp/unsigned/SHA256SUMS.txt" || fail CHECKSUM_MANIFEST_MISSING
grep -Eq '^[0-9a-f]{64}  Continuum-Lokal-Code48-UNSIGNED[.]apk$' "$tmp/unsigned/SHA256SUMS.txt" || fail CHECKSUM_MANIFEST_INVALID
(cd "$tmp/unsigned" && sha256sum -c SHA256SUMS.txt) || fail UNSIGNED_APK_SHA_MISMATCH
receipt="$tmp/unsigned/BUILD-INFO.txt"
test -s "$receipt" || fail QA_RECEIPT_MISSING
for field in "source_sha=$source_sha" "package=$pkg" "version_code=48" "version_name=0.3.47" "owner_signed=FALSE" "status=QA_UNSIGNED_NOT_INSTALLABLE"; do
  grep -Fxq "$field" "$receipt" || fail QA_RECEIPT_MISMATCH
done
tools="$ANDROID_HOME/build-tools/35.0.0"
test -x "$tools/apksigner" && test -x "$tools/aapt2" && test -x "$tools/zipalign" || fail ANDROID_SIGN_TOOLS_MISSING
badging="$("$tools/aapt2" dump badging "$unsigned")" || fail UNSIGNED_BADGING_FAILED
grep -Fq "package: name='$pkg' versionCode='48' versionName='0.3.47'" <<< "$badging" || fail UNSIGNED_PACKAGE_VERSION_MISMATCH
if "$tools/apksigner" verify "$unsigned" >/dev/null 2>&1; then fail UNSIGNED_APK_UNEXPECTEDLY_SIGNED; fi
echo CHECKPOINT=UNSIGNED_APK_HASH_PACKAGE_AND_SIGNATURE_STATE_VALID

[[ -n "$ORIGINAL_P12" && -n "$ORIGINAL_PASSWORD" ]] || fail ORIGINAL_SECRETS_MISSING
if ! printf '%s' "$ORIGINAL_P12" | base64 --decode > "$tmp/original.p12" 2>/dev/null; then fail ORIGINAL_P12_BASE64_INVALID; fi
unset ORIGINAL_P12
chmod 600 "$tmp/original.p12"
test "$(stat -c %s "$tmp/original.p12")" -gt 300 || fail ORIGINAL_P12_TOO_SMALL
export SIGN_PASS="$ORIGINAL_PASSWORD"
unset ORIGINAL_PASSWORD
if ! keytool -list -v -storetype PKCS12 -alias continuum-local -keystore "$tmp/original.p12" -storepass:env SIGN_PASS > "$tmp/keyinfo.txt" 2>/dev/null; then
  fail ORIGINAL_P12_PASSWORD_OR_ALIAS_INVALID
fi
grep -Fq 'Entry type: PrivateKeyEntry' "$tmp/keyinfo.txt" || fail ORIGINAL_PRIVATE_KEY_MISSING
fp="$(sed -n 's/^[[:space:]]*SHA256: //p' "$tmp/keyinfo.txt" | head -n1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
test "$fp" = "$fingerprint" || fail ORIGINAL_CERTIFICATE_MISMATCH
echo CHECKPOINT=ORIGINAL_P12_CERTIFICATE_VERIFIED

"$tools/zipalign" -p -f 4 "$unsigned" "$tmp/aligned.apk" >/dev/null || fail APK_ZIPALIGN_FAILED
if ! "$tools/apksigner" sign --ks "$tmp/original.p12" --ks-type PKCS12 --ks-key-alias continuum-local --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --out "$tmp/signed.apk" "$tmp/aligned.apk" 2>/dev/null; then
  fail ORIGINAL_APK_SIGNING_FAILED
fi
unset SIGN_PASS
verified="$("$tools/apksigner" verify --verbose --print-certs "$tmp/signed.apk")" || fail SIGNED_APK_VERIFY_FAILED
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<< "$verified" || fail SIGNED_APK_V2_MISSING
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$verified" | head -n1 | tr '[:upper:]' '[:lower:]')"
test "$actual" = "$fingerprint" || fail SIGNED_APK_CERTIFICATE_MISMATCH
signed_badging="$("$tools/aapt2" dump badging "$tmp/signed.apk")" || fail SIGNED_APK_BADGING_FAILED
grep -Fq "package: name='$pkg' versionCode='48' versionName='0.3.47'" <<< "$signed_badging" || fail SIGNED_PACKAGE_MISMATCH
size="$(stat -c %s "$tmp/signed.apk")"
[[ "$size" -ge 1000000 && "$size" -le 2000000000 ]] || fail SIGNED_SIZE_INVALID
digest="$(sha256sum "$tmp/signed.apk" | cut -d' ' -f1)"
name="Continuum-Lokal-$digest.apk"
mv "$tmp/signed.apk" "$tmp/$name"
printf 'source_sha=%s\npackage=%s\nversion_code=48\nversion_name=0.3.47\nsigner_sha256=%s\napk_sha256=%s\napk_bytes=%s\nfold7_test=NOT_RUN\nstatus=ORIGINAL_SIGNED_VERIFIED\n' "$source_sha" "$pkg" "$fingerprint" "$digest" "$size" > "$tmp/BUILD-INFO.txt"
echo CHECKPOINT=SIGNED_APK_CERTIFICATE_PACKAGE_AND_HASH_VERIFIED

if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then fail RELEASE_ALREADY_EXISTS; fi
cat > "$tmp/notes.txt" <<EOF
Continuum Lokal Code48 / 0.3.47. Originalsigniert.
Source: $source_sha
Package: $pkg
Certificate SHA256: $fingerprint
APK SHA256: $digest
Byte length: $size
Real Fold7 thermal/visual/device acceptance: NOT_RUN.
The installed update button remains pinned to Render; use this verified GitHub Release APK for manual in-place update.
EOF
gh release create "$tag" "$tmp/$name" "$tmp/BUILD-INFO.txt" --repo "$repo" --target "$source_sha" --title 'Continuum Lokal 0.3.47 (Code48)' --notes-file "$tmp/notes.txt" --draft --latest=false >/dev/null || fail GITHUB_DRAFT_CREATE_FAILED
mkdir "$tmp/readback"
gh release download "$tag" --repo "$repo" --pattern "$name" --dir "$tmp/readback" >/dev/null || fail GITHUB_READBACK_FAILED
rb="$tmp/readback/$name"
test "$(sha256sum "$rb" | cut -d' ' -f1)" = "$digest" || fail GITHUB_READBACK_SHA_MISMATCH
test "$(stat -c %s "$rb")" = "$size" || fail GITHUB_READBACK_SIZE_MISMATCH
verify_rb="$("$tools/apksigner" verify --verbose --print-certs "$rb")" || fail GITHUB_READBACK_SIGNATURE_FAILED
grep -Fq "Signer #1 certificate SHA-256 digest: $fingerprint" <<< "$verify_rb" || fail GITHUB_READBACK_CERT_MISMATCH
rb_badging="$("$tools/aapt2" dump badging "$rb")" || fail GITHUB_READBACK_BADGING_FAILED
grep -Fq "package: name='$pkg' versionCode='48' versionName='0.3.47'" <<< "$rb_badging" || fail GITHUB_READBACK_PACKAGE_MISMATCH
gh release edit "$tag" --repo "$repo" --draft=false --latest=false >/dev/null || fail GITHUB_PUBLICATION_FAILED
echo CHECKPOINT=VERIFIED_GITHUB_RELEASE_PUBLIC
printf 'RELEASE_URL=https://github.com/%s/releases/tag/%s\n' "$repo" "$tag"
