#!/usr/bin/env bash
# Code 51 (0.3.50 / versionCode 51), owner-authorized channel 09.11.2026.
# Historical filename retained for the already registered workflow. Code50 branch is unchanged.
# Modi: preflight (ohne Geheimnisse) | sign (nur im geschützten Environment).
# Signing material is used only inside the protected job and never printed or exported.
set -Eeuo pipefail
set +x
umask 077
fail() { printf '::error::CODE51_SIGN_%s\n' "$1" >&2; exit 69; }

mode="${1:-}"
[[ "$mode" == preflight || "$mode" == sign ]] || fail BAD_MODE

repo="Ullmann27/device-ai"
branch="copilot/code51-master-integrator-20261108"
qa_workflow_path=".github/workflows/continuum-code50-github-qa.yml"
environment_name="continuum-original-signing"
owner_login="Ullmann27"
fingerprint="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
pkg="at.heinz.deviceai.continuumlocal"
code="51"
name_version="0.3.50"
artifact="Code51-UNSIGNED-NOT-INSTALLABLE"
unsigned_name="Continuum-Code51-UNSIGNED-NOT-INSTALLABLE.apk"

source_sha="${SOURCE_SHA:-}"
qa_run="${QA_RUN_ID:-}"
approved_sha="${OWNER_APPROVED_SHA:-}"

[[ "${GITHUB_REPOSITORY:-}" == "$repo" ]] || fail WRONG_REPOSITORY
[[ "${GITHUB_REF:-}" == "refs/heads/$branch" ]] || fail WRONG_BRANCH
[[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]] || fail WRONG_EVENT
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail SOURCE_SHA_FORMAT
[[ "$approved_sha" =~ ^[0-9a-f]{40}$ ]] || fail APPROVED_SHA_FORMAT
[[ "$qa_run" =~ ^[0-9]{6,20}$ ]] || fail QA_RUN_FORMAT
[[ "$source_sha" == "$approved_sha" ]] || fail APPROVED_SHA_DIFFERS_FROM_SOURCE
[[ "$source_sha" == "${GITHUB_SHA:-}" ]] || fail SOURCE_DIFFERS_FROM_RUN_COMMIT
tag="continuum-local-code${code}-${source_sha}"
for cmd in gh jq base64 sha256sum stat curl; do command -v "$cmd" >/dev/null || fail TOOL_MISSING; done
tmp="$(mktemp -d)"
chmod 700 "$tmp"
trap 'rm -rf -- "$tmp"' EXIT

# 1. Environment-Schutz: ohne Pflicht-Reviewer (Besitzer) und enge Branch-Regel wird NICHT signiert.
env_json="$(gh api "repos/$repo/environments/$environment_name")" || fail ENVIRONMENT_UNREADABLE
jq -e --arg owner "$owner_login" '
  ([.protection_rules[]? | select(.type=="required_reviewers") | .reviewers[]? | select(.type=="User" and .reviewer.login==$owner)] | length) >= 1
' <<< "$env_json" >/dev/null || fail ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING
jq -e '.deployment_branch_policy.custom_branch_policies==true' <<< "$env_json" >/dev/null || fail ENVIRONMENT_BRANCH_POLICY_NOT_CUSTOM
policies="$(gh api "repos/$repo/environments/$environment_name/deployment-branch-policies")" || fail ENVIRONMENT_POLICIES_UNREADABLE
jq -e --arg b "$branch" '.branch_policies | length==1 and .[0].type=="branch" and .[0].name==$b' <<< "$policies" >/dev/null || fail ENVIRONMENT_BRANCH_POLICY_NOT_EXACT
echo CHECKPOINT=ENVIRONMENT_OWNER_REVIEWER_AND_EXACT_BRANCH_POLICY_VERIFIED

# 2. QA-Lauf: exakt dieser Commit, dieser Branch, manuell ausgelöst, dieser Workflow, vollständig grün.
run="$(gh api "repos/$repo/actions/runs/$qa_run")" || fail QA_RUN_UNREADABLE
jq -e --arg sha "$source_sha" --arg b "$branch" --arg path "$qa_workflow_path" '
  .head_sha==$sha and .head_branch==$b and .event=="workflow_dispatch" and .status=="completed"
  and .conclusion=="success" and (.path==$path or (.path|startswith($path+"@")))
' <<< "$run" >/dev/null || fail QA_IDENTITY_OR_RESULT_MISMATCH
unset run
jobs="$(gh api "repos/$repo/actions/runs/$qa_run/jobs?per_page=100")" || fail QA_JOBS_UNREADABLE
jq -e '[.jobs[]|select(.name=="node-backend" and .conclusion=="success")]|length==1' <<< "$jobs" >/dev/null || fail NODE_BACKEND_NOT_GREEN
jq -e '[.jobs[]|select(.name=="android-host" and .conclusion=="success")]|length==1' <<< "$jobs" >/dev/null || fail ANDROID_HOST_NOT_GREEN
lifecycle="PASS_REQUIRED_IN_ANDROID_HOST"
unset jobs
echo CHECKPOINT=EXACT_SOURCE_QA_RUN_GREEN
echo "INFO=lifecycle_jdk21_job=$lifecycle"

# 3. Never publish over an existing/newer release or accept unreadable latest metadata.
if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then fail RELEASE_ALREADY_EXISTS; fi
latest_tag="$(gh api "repos/$repo/releases/latest" --jq .tag_name)" || fail LATEST_RELEASE_UNREADABLE
[[ "$latest_tag" =~ ^continuum-local-code([0-9]{2,4})-[0-9a-f]{40}$ ]] || fail LATEST_RELEASE_IDENTITY_INVALID
[[ "${BASH_REMATCH[1]}" -lt "$code" ]] || fail NOT_HIGHER_THAN_LATEST_RELEASE
[[ "$latest_tag" == continuum-local-code50-473f79727e4b52509cc7d1af71e59b80ab9b6be5 ]] || fail CODE50_BASELINE_CHANGED
echo CHECKPOINT=RELEASE_PREFLIGHT_VALID
[[ "$mode" == sign ]] || { echo STATUS=PREFLIGHT_OK; exit 0; }

# 4. Ungesignierte QA-APK aus genau diesem Lauf prüfen.
[[ -n "${ANDROID_HOME:-}" ]] || fail ANDROID_HOME_MISSING
mkdir "$tmp/unsigned"
gh run download "$qa_run" --repo "$repo" --name "$artifact" --dir "$tmp/unsigned" >/dev/null || fail APK_ARTIFACT_DOWNLOAD_FAILED
unsigned="$tmp/unsigned/$unsigned_name"
test -s "$unsigned" || fail UNSIGNED_APK_MISSING
test -s "$tmp/unsigned/SHA256SUMS.txt" || fail CHECKSUM_MANIFEST_MISSING
grep -Eq "^[0-9a-f]{64}  ${unsigned_name//./[.]}\$" "$tmp/unsigned/SHA256SUMS.txt" || fail CHECKSUM_MANIFEST_INVALID
(cd "$tmp/unsigned" && sha256sum -c SHA256SUMS.txt >/dev/null) || fail UNSIGNED_APK_SHA_MISMATCH
receipt="$tmp/unsigned/BUILD-INFO.txt"
test -s "$receipt" || fail QA_RECEIPT_MISSING
for field in "status=UNSIGNED_QA_NOT_INSTALLABLE" "source_sha=$source_sha" "package=$pkg" "versionCode=$code" "versionName=$name_version" "originalSigner=NOT_USED" "qa_run_id=$qa_run" "qa_ref=refs/heads/$branch" "tests_total=4200" "tests_failed=0" "tests_errors=0" "tests_skipped=18" "lifecycle_jdk21=PASS" "code50_apk_sha256=38b39da66a96d8be08b84937dd6412868fa279eb477a3ff5700d9195611a2778"; do
  grep -Fxq "$field" "$receipt" || fail QA_RECEIPT_MISMATCH
done
tools="$ANDROID_HOME/build-tools/35.0.0"
test -x "$tools/apksigner" && test -x "$tools/aapt2" && test -x "$tools/zipalign" || fail ANDROID_SIGN_TOOLS_MISSING
badging="$("$tools/aapt2" dump badging "$unsigned")" || fail UNSIGNED_BADGING_FAILED
grep -Fq "package: name='$pkg' versionCode='$code' versionName='$name_version'" <<< "$badging" || fail UNSIGNED_PACKAGE_VERSION_MISMATCH
grep -Fxq "native-code: 'arm64-v8a'" <<< "$badging" || fail UNSIGNED_ABI_MISMATCH
if "$tools/apksigner" verify "$unsigned" >/dev/null 2>&1; then fail UNSIGNED_APK_UNEXPECTEDLY_SIGNED; fi
echo CHECKPOINT=UNSIGNED_APK_HASH_PACKAGE_AND_SIGNATURE_STATE_VALID

# 5. Originalschlüssel nur prüfen (Fingerabdruck), nie ausgeben.
[[ -n "${ORIGINAL_P12:-}" && -n "${ORIGINAL_PASSWORD:-}" ]] || fail ORIGINAL_SECRETS_MISSING
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
rm -f "$tmp/keyinfo.txt"
test "$fp" = "$fingerprint" || fail ORIGINAL_CERTIFICATE_MISMATCH
echo CHECKPOINT=ORIGINAL_P12_CERTIFICATE_VERIFIED

# 6. Ausrichten, signieren, unabhängig prüfen.
"$tools/zipalign" -p -f 4 "$unsigned" "$tmp/aligned.apk" >/dev/null || fail APK_ZIPALIGN_FAILED
if ! "$tools/apksigner" sign --ks "$tmp/original.p12" --ks-type PKCS12 --ks-key-alias continuum-local --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --out "$tmp/signed.apk" "$tmp/aligned.apk" 2>/dev/null; then
  fail ORIGINAL_APK_SIGNING_FAILED
fi
unset SIGN_PASS
rm -f "$tmp/original.p12"
verified="$("$tools/apksigner" verify --verbose --print-certs "$tmp/signed.apk")" || fail SIGNED_APK_VERIFY_FAILED
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<< "$verified" || fail SIGNED_APK_V2_MISSING
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$verified" | head -n1 | tr '[:upper:]' '[:lower:]')"
test "$actual" = "$fingerprint" || fail SIGNED_APK_CERTIFICATE_MISMATCH
signed_badging="$("$tools/aapt2" dump badging "$tmp/signed.apk")" || fail SIGNED_APK_BADGING_FAILED
grep -Fq "package: name='$pkg' versionCode='$code' versionName='$name_version'" <<< "$signed_badging" || fail SIGNED_PACKAGE_MISMATCH
size="$(stat -c %s "$tmp/signed.apk")"
[[ "$size" -ge 1000000 && "$size" -le 2000000000 ]] || fail SIGNED_SIZE_INVALID
digest="$(sha256sum "$tmp/signed.apk" | cut -d' ' -f1)"
name="Continuum-Lokal-$digest.apk"
mv "$tmp/signed.apk" "$tmp/$name"
printf 'source_sha=%s\npackage=%s\nversion_code=%s\nversion_name=%s\nsigner_sha256=%s\napk_sha256=%s\napk_bytes=%s\nqa_run_id=%s\nlifecycle_jdk21_job=%s\nfold7_test=NOT_RUN\nstatus=ORIGINAL_SIGNED_VERIFIED\n' \
  "$source_sha" "$pkg" "$code" "$name_version" "$fingerprint" "$digest" "$size" "$qa_run" "$lifecycle" > "$tmp/BUILD-INFO.txt"
echo CHECKPOINT=SIGNED_APK_CERTIFICATE_PACKAGE_AND_HASH_VERIFIED

# 7. Entwurf anlegen, Readback prüfen, erst dann veröffentlichen.
cat > "$tmp/notes.txt" <<EOF
Continuum Lokal Code $code / $name_version (versionCode $code). Originalsigniert.
Quelle: $source_sha
Paket: $pkg
Zertifikat SHA-256: $fingerprint
APK SHA-256: $digest
Größe in Bytes: $size
QA-Lauf (GitHub, exakt dieser Commit): https://github.com/$repo/actions/runs/$qa_run
JDK-21-Lifecycle: $lifecycle (required, included in the fully green Android suite).
Echte Fold7-Geräteprüfung (Thermik, Sprache, Avatar, PDF, Rotation, Update): NICHT AUSGEFÜHRT.
Code50-to-Code51 update compatibility: same package and original certificate, higher versionCode, unchanged existing storage formats. Private data preservation regression passed; a real-device update/data comparison is NOT_RUN. Install only as an update, never uninstall or clear app data. The Code50 updater also validates this GitHub channel.
EOF
gh release create "$tag" "$tmp/$name" "$tmp/BUILD-INFO.txt" --repo "$repo" --target "$source_sha" --title "Continuum Lokal $name_version (Code $code)" --notes-file "$tmp/notes.txt" --draft --latest=false >/dev/null || fail GITHUB_DRAFT_CREATE_FAILED
mkdir "$tmp/readback"
gh release download "$tag" --repo "$repo" --pattern "$name" --dir "$tmp/readback" >/dev/null || fail GITHUB_READBACK_FAILED
rb="$tmp/readback/$name"
test "$(sha256sum "$rb" | cut -d' ' -f1)" = "$digest" || fail GITHUB_READBACK_SHA_MISMATCH
test "$(stat -c %s "$rb")" = "$size" || fail GITHUB_READBACK_SIZE_MISMATCH
verify_rb="$("$tools/apksigner" verify --verbose --print-certs "$rb")" || fail GITHUB_READBACK_SIGNATURE_FAILED
grep -Fq "Signer #1 certificate SHA-256 digest: $fingerprint" <<< "$verify_rb" || fail GITHUB_READBACK_CERT_MISMATCH
rb_badging="$("$tools/aapt2" dump badging "$rb")" || fail GITHUB_READBACK_BADGING_FAILED
grep -Fq "package: name='$pkg' versionCode='$code' versionName='$name_version'" <<< "$rb_badging" || fail GITHUB_READBACK_PACKAGE_MISMATCH
echo CHECKPOINT=DRAFT_RELEASE_READBACK_VERIFIED

gh release edit "$tag" --repo "$repo" --draft=false --latest=true >/dev/null || fail GITHUB_PUBLICATION_FAILED
rollback() { gh release edit "$tag" --repo "$repo" --draft=true >/dev/null 2>&1 || true; fail "$1"; }

# 8. Öffentlicher Readback ohne Anmeldung, exakt wie ihn der In-App-Katalog sieht.
pub="$(curl -fsSL --max-time 60 "https://api.github.com/repos/$repo/releases/latest")" || rollback PUBLIC_LATEST_UNREADABLE
jq -e --arg tag "$tag" --arg name "$name" --arg sha "sha256:$digest" --argjson size "$size" '
  .tag_name==$tag and .draft==false and .prerelease==false
  and ([.assets[] | select(.name|test("^Continuum-Lokal-[a-f0-9]{64}[.]apk$"))] | length==1)
  and ([.assets[] | select(.name==$name and .digest==$sha and .size==$size)] | length==1)
' <<< "$pub" >/dev/null || rollback PUBLIC_LATEST_METADATA_MISMATCH
url="https://github.com/$repo/releases/download/$tag/$name"
curl -fsSL --max-time 900 -o "$tmp/public.apk" "$url" || rollback PUBLIC_DOWNLOAD_FAILED
test "$(sha256sum "$tmp/public.apk" | cut -d' ' -f1)" = "$digest" || rollback PUBLIC_DOWNLOAD_SHA_MISMATCH
pub_verify="$("$tools/apksigner" verify --verbose --print-certs "$tmp/public.apk")" || rollback PUBLIC_DOWNLOAD_SIGNATURE_FAILED
grep -Fq "Signer #1 certificate SHA-256 digest: $fingerprint" <<< "$pub_verify" || rollback PUBLIC_DOWNLOAD_CERT_MISMATCH
echo CHECKPOINT=VERIFIED_PUBLIC_GITHUB_RELEASE_LATEST
printf 'RELEASE_URL=https://github.com/%s/releases/tag/%s\nAPK_URL=%s\nAPK_SHA256=%s\nAPK_BYTES=%s\n' "$repo" "$tag" "$url" "$digest" "$size"
