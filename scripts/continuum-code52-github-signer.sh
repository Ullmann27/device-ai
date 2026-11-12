#!/usr/bin/env bash
# Code52, frozen Code53, and exact-source Code54 preparation (signing blocked until native PDF gate).
# Exact owner approval remains mandatory.
# Modi: preflight (ohne Geheimnisse) | sign (nur im geschützten Environment).
# Signing material is used only inside the protected job and never printed or exported.
set -Eeuo pipefail
set +x
umask 077
fail() { printf '::error::CODE%s_SIGN_%s\n' "${code:-52}" "$1" >&2; exit 69; }

mode="${1:-}"
[[ "$mode" == preflight || "$mode" == sign ]] || fail BAD_MODE

repo="Ullmann27/device-ai"
branch="chatgpt/code52-owner-signing-20261109"
qa_branch="code52/grok-release-candidate-0351-20261110"
qa_workflow_path=".github/workflows/continuum-code52-cinematic-qa.yml"
environment_name="continuum-original-signing"
owner_login="Ullmann27"
fingerprint="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
pkg="at.heinz.deviceai.continuumlocal"
code="52"
name_version="0.3.51"
artifact="Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE"
unsigned_name="Continuum-Code52-CINEMA-UNSIGNED-NOT-INSTALLABLE.apk"

source_sha="${SOURCE_SHA:-}"
qa_run="${QA_RUN_ID:-}"
approved_sha="${OWNER_APPROVED_SHA:-}"
# Code55 is detected by its exact QA workflow, never by a loose version claim.
# Reuse this already registered protected manual signer entry without altering Code52–54.
if [[ "$qa_run" =~ ^[0-9]{6,20}$ ]]; then
  qa_identity="$(gh api "repos/$repo/actions/runs/$qa_run")" || fail QA_RUN_UNREADABLE
  if jq -e '.head_branch=="integrate/code55-apk-fable-20261112"
     and (.path==".github/workflows/continuum-code55-full-qa.yml" or
          (.path|startswith(".github/workflows/continuum-code55-full-qa.yml@")))' <<< "$qa_identity" >/dev/null; then
    exec bash scripts/continuum-code55-original-signer.sh "$mode"
  fi
fi
profile="code52"
if [[ "$source_sha" == 050a57cb019dcc1b7fe6cfa2fb5c11176b048e05 ]]; then
  profile="code53"
  code="53"
  name_version="0.3.52"
  artifact="Code53-050a57cb-UNSIGNED-NOT-INSTALLABLE"
  unsigned_name="Continuum-Code53-UNSIGNED-NOT-INSTALLABLE.apk"
  binding=".github/continuum/code53-owner-signing-binding.json"
  test -s "$binding" || fail CODE53_BINDING_MISSING
  command -v python3 >/dev/null || fail CODE53_PYTHON_MISSING
elif [[ "$source_sha" == 9713854bfb01d699f666058ac44379735307b66a ]]; then
  profile="code54"
  code="54"
  name_version="0.3.53"
  artifact="Code54-UNSIGNED-NOT-INSTALLABLE"
  unsigned_name="Continuum-Code54-UNSIGNED-NOT-INSTALLABLE.apk"
  qa_branch="integrate/omega-apk54-20261111"
  qa_workflow_path=".github/workflows/continuum-code54-qa.yml"
  binding=".github/continuum/code54-owner-signing-binding.json"
  test -s "$binding" || fail CODE54_BINDING_MISSING
  command -v python3 >/dev/null || fail CODE54_PYTHON_MISSING
fi

[[ "${GITHUB_REPOSITORY:-}" == "$repo" ]] || fail WRONG_REPOSITORY
[[ "${GITHUB_REF:-}" == "refs/heads/$branch" ]] || fail WRONG_BRANCH
[[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]] || fail WRONG_EVENT
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail SOURCE_SHA_FORMAT
[[ "$approved_sha" =~ ^[0-9a-f]{40}$ ]] || fail APPROVED_SHA_FORMAT
[[ "$qa_run" =~ ^[0-9]{6,20}$ ]] || fail QA_RUN_FORMAT
[[ "$source_sha" == "$approved_sha" ]] || fail APPROVED_SHA_DIFFERS_FROM_SOURCE
[[ "$(git rev-parse HEAD)" == "${GITHUB_SHA:-}" ]] || fail SIGNER_WORKTREE_NOT_AT_RUN_COMMIT
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] || fail SIGNER_WORKTREE_DIRTY
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

# 2. Source-bound QA.
run="$(gh api "repos/$repo/actions/runs/$qa_run")" || fail QA_RUN_UNREADABLE
if [[ "$profile" == code53 || "$profile" == code54 ]]; then
  printf '%s\n' "$run" > "$tmp/run.json"
  unset run
  gh api "repos/$repo/actions/runs/$qa_run/jobs?per_page=100" > "$tmp/jobs.json" || fail QA_JOBS_UNREADABLE
  gh api "repos/$repo/actions/runs/$qa_run/artifacts?per_page=100" > "$tmp/artifacts.json" || fail QA_ARTIFACTS_UNREADABLE
  gh api "repos/$repo/git/commits/$source_sha" > "$tmp/source.json" || fail SOURCE_COMMIT_UNREADABLE
  if [[ "$profile" == code53 ]]; then
    python3 -I scripts/continuum-code53-signing-evidence.py metadata "$binding" \
      "$source_sha" "$approved_sha" "$qa_run" "$tmp/run.json" "$tmp/jobs.json" \
      "$tmp/artifacts.json" "$tmp/source.json" || fail CODE53_METADATA_MISMATCH
    for kind in unsigned evidence; do
      artifact_id="$(jq -er --arg kind "$kind" '.[$kind].id' "$binding")"
      gh api "repos/$repo/actions/artifacts/$artifact_id/zip" > "$tmp/$kind.zip" || fail CODE53_ARCHIVE_DOWNLOAD_FAILED
    done
    python3 -I scripts/continuum-code53-signing-evidence.py archives "$binding" \
      "$tmp/unsigned.zip" "$tmp/evidence.zip" "$tmp/unsigned" || fail CODE53_ARCHIVE_MISMATCH
    lifecycle="PASS_IN_CODE53_ANDROID_HOST"
  else
    # The Code54 binding has a locked release_gate until the owner separately
    # approves a source-exact native PDF result or a reviewed test exception.
    python3 -I scripts/continuum-code54-signing-evidence.py metadata "$binding" \
      "$source_sha" "$approved_sha" "$qa_run" "$tmp/run.json" "$tmp/jobs.json" \
      "$tmp/artifacts.json" "$tmp/source.json" || fail CODE54_METADATA_MISMATCH
    artifact_id="$(jq -er '.unsigned.id' "$binding")"
    gh api "repos/$repo/actions/artifacts/$artifact_id/zip" > "$tmp/unsigned.zip" || fail CODE54_ARCHIVE_DOWNLOAD_FAILED
    gate="$(jq -er '.release_gate' "$binding")" || fail CODE54_GATE_UNREADABLE
    if [[ "$gate" == DEVICE_TEST_NATIVE_PDF_EXCEPTION_VERIFIED ]]; then
      # Narrow owner-approved device-test scope only. All other test cases must pass.
      evidence_id="$(jq -er '.qa_evidence.id' "$binding")" || fail CODE54_QA_EVIDENCE_UNBOUND
      gh api "repos/$repo/actions/artifacts/$evidence_id/zip" > "$tmp/evidence.zip" || fail CODE54_QA_EVIDENCE_DOWNLOAD_FAILED
      python3 -I scripts/continuum-code54-signing-evidence.py archives "$binding" \
        "$tmp/unsigned.zip" "$tmp/unsigned" "$tmp/evidence.zip" || fail CODE54_ARCHIVE_MISMATCH
      lifecycle="CODE54_HOST_PDF_FAILURE_FOLD7_PDF_REQUIRED_NOT_FULL_PASS"
    else
      python3 -I scripts/continuum-code54-signing-evidence.py archives "$binding" \
        "$tmp/unsigned.zip" "$tmp/unsigned" || fail CODE54_ARCHIVE_MISMATCH
      lifecycle="PASS_IN_CODE54_STRICT_ANDROID_HOST"
    fi
  fi
else
jq -e --arg sha "$source_sha" --arg b "$qa_branch" --arg path "$qa_workflow_path" '
  .head_sha==$sha and .head_branch==$b and .event=="push" and .status=="completed"
  and .conclusion=="success" and (.path==$path or (.path|startswith($path+"@")))
' <<< "$run" >/dev/null || fail QA_IDENTITY_OR_RESULT_MISMATCH
unset run
jobs="$(gh api "repos/$repo/actions/runs/$qa_run/jobs?per_page=100")" || fail QA_JOBS_UNREADABLE
jq -e '[.jobs[] | select(.name=="validate-source-and-build" and .conclusion=="success")] | length==1' <<< "$jobs" >/dev/null || fail CODE52_QA_NOT_GREEN
lifecycle="PASS_IN_CODE52_ANDROID_HOST"
unset jobs
fi
echo CHECKPOINT=EXACT_SOURCE_QA_RUN_GREEN
echo "INFO=lifecycle_jdk21_job=$lifecycle"

# 3. Never publish over an existing/newer release or accept unreadable latest metadata.
if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then fail RELEASE_ALREADY_EXISTS; fi
latest_tag="$(gh api "repos/$repo/releases/latest" --jq .tag_name)" || fail LATEST_RELEASE_UNREADABLE
[[ "$latest_tag" =~ ^continuum-local-code([0-9]{2,4})-[0-9a-f]{40}$ ]] || fail LATEST_RELEASE_IDENTITY_INVALID
[[ "${BASH_REMATCH[1]}" -lt "$code" ]] || fail NOT_HIGHER_THAN_LATEST_RELEASE
[[ "$latest_tag" == continuum-local-code51-6c23d295e2fca2d4e5950a866547733ec5ff22b0 ]] || fail CODE51_BASELINE_CHANGED
echo CHECKPOINT=RELEASE_PREFLIGHT_VALID
[[ "$mode" == sign ]] || { echo STATUS=PREFLIGHT_OK; exit 0; }

# 4. Ungesignierte QA-APK aus genau diesem Lauf prüfen.
[[ -n "${ANDROID_HOME:-}" ]] || fail ANDROID_HOME_MISSING
if [[ "$profile" != code52 ]]; then
  unsigned="$tmp/unsigned/$unsigned_name"
else
mkdir "$tmp/unsigned"
gh run download "$qa_run" --repo "$repo" --name "$artifact" --dir "$tmp/unsigned" >/dev/null || fail APK_ARTIFACT_DOWNLOAD_FAILED
unsigned="$tmp/unsigned/$unsigned_name"
test -s "$unsigned" || fail UNSIGNED_APK_MISSING
test -s "$tmp/unsigned/SHA256SUMS.txt" || fail CHECKSUM_MANIFEST_MISSING
grep -Eq "^[0-9a-f]{64}  ${unsigned_name//./[.]}\$" "$tmp/unsigned/SHA256SUMS.txt" || fail CHECKSUM_MANIFEST_INVALID
(cd "$tmp/unsigned" && sha256sum -c SHA256SUMS.txt >/dev/null) || fail UNSIGNED_APK_SHA_MISMATCH
receipt="$tmp/unsigned/BUILD-INFO.txt"
test -s "$receipt" || fail QA_RECEIPT_MISSING
for field in "release_state=UNSIGNED_QA_ONLY" "source_sha=$source_sha" "package=$pkg" "versionCode=$code" "versionName=$name_version" "original_signature=NOT_USED" "fold7=NOT_RUN"; do
  grep -Fxq "$field" "$receipt" || fail QA_RECEIPT_MISMATCH
done
fi
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
if [[ "$profile" != code52 ]]; then
  "$tools/zipalign" -P 16 -f 4 "$unsigned" "$tmp/aligned.apk" >/dev/null || fail APK_ZIPALIGN_FAILED
else
  "$tools/zipalign" -p -f 4 "$unsigned" "$tmp/aligned.apk" >/dev/null || fail APK_ZIPALIGN_FAILED
fi
if ! "$tools/apksigner" sign --ks "$tmp/original.p12" --ks-type PKCS12 --ks-key-alias continuum-local --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --out "$tmp/signed.apk" "$tmp/aligned.apk" 2>/dev/null; then
  fail ORIGINAL_APK_SIGNING_FAILED
fi
unset SIGN_PASS
rm -f "$tmp/original.p12"
verified="$("$tools/apksigner" verify --verbose --print-certs "$tmp/signed.apk")" || fail SIGNED_APK_VERIFY_FAILED
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<< "$verified" || fail SIGNED_APK_V2_MISSING
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$verified" | head -n1 | tr '[:upper:]' '[:lower:]')"
test "$actual" = "$fingerprint" || fail SIGNED_APK_CERTIFICATE_MISMATCH
if [[ "$profile" != code52 ]]; then
  "$tools/zipalign" -c -P 16 -v 4 "$tmp/signed.apk" >/dev/null || fail SIGNED_APK_ALIGNMENT_MISMATCH
fi
signed_badging="$("$tools/aapt2" dump badging "$tmp/signed.apk")" || fail SIGNED_APK_BADGING_FAILED
grep -Fq "package: name='$pkg' versionCode='$code' versionName='$name_version'" <<< "$signed_badging" || fail SIGNED_PACKAGE_MISMATCH
size="$(stat -c %s "$tmp/signed.apk")"
[[ "$size" -ge 1000000 && "$size" -le 2000000000 ]] || fail SIGNED_SIZE_INVALID
digest="$(sha256sum "$tmp/signed.apk" | cut -d' ' -f1)"
name="Continuum-Lokal-$digest.apk"
mv "$tmp/signed.apk" "$tmp/$name"
printf 'source_sha=%s\npackage=%s\nversion_code=%s\nversion_name=%s\nsigner_sha256=%s\napk_sha256=%s\napk_bytes=%s\nqa_run_id=%s\nlifecycle_jdk21_job=%s\nfold7_test=NOT_RUN\nstatus=ORIGINAL_SIGNED_VERIFIED\n' \
  "$source_sha" "$pkg" "$code" "$name_version" "$fingerprint" "$digest" "$size" "$qa_run" "$lifecycle" > "$tmp/BUILD-INFO.txt"
if [[ "$profile" == code53 ]]; then
  jq -r '"source_tree=\(.source_tree)", "qa_workflow_sha=\(.workflow_sha)",
    "qa_job_id=\(.qa_job_id)", "unsigned_apk_sha256=\(.apk_sha256)",
    "unsigned_artifact_id=\(.unsigned.id)", "unsigned_artifact_digest=\(.unsigned.digest)",
    "evidence_artifact_id=\(.evidence.id)", "evidence_artifact_digest=\(.evidence.digest)"' \
    "$binding" >> "$tmp/BUILD-INFO.txt"
elif [[ "$profile" == code54 ]]; then
  jq -r '"source_tree=\(.source_tree)", "qa_workflow_blob=\(.workflow_blob_sha)",
    "qa_android_job_id=\(.qa_job_android_id)", "qa_unsigned_job_id=\(.qa_job_unsigned_id)",
    "unsigned_apk_sha256=\(.apk_sha256)", "unsigned_artifact_id=\(.unsigned.id)",
    "unsigned_artifact_digest=\(.unsigned.digest)", "release_gate=\(.release_gate)"' \
    "$binding" >> "$tmp/BUILD-INFO.txt"
  if [[ "$gate" == DEVICE_TEST_NATIVE_PDF_EXCEPTION_VERIFIED ]]; then
    jq -r '"qa_evidence_artifact_id=\(.qa_evidence.id)", "qa_evidence_artifact_digest=\(.qa_evidence.digest)",
      "owner_pdf_exception=\(.owner_exception.authorization_id)",
      "native_pdf_device_status=NOT_RUN",
      "test_draft_only=true", "publication_permitted=false",
      "requires_fold7_pdf_test=true"' "$binding" >> "$tmp/BUILD-INFO.txt"
  fi
fi
echo CHECKPOINT=SIGNED_APK_CERTIFICATE_PACKAGE_AND_HASH_VERIFIED

# 7. Signierte APK ausschließlich als unveröffentlichten GitHub-Entwurf anlegen und prüfen.
cat > "$tmp/notes.txt" <<EOF
Continuum Lokal Code $code / $name_version (versionCode $code). Originalsigniert.
Quelle: $source_sha
Paket: $pkg
Zertifikat SHA-256: $fingerprint
APK SHA-256: $digest
Größe in Bytes: $size
QA-Lauf (GitHub, exakt dieser Commit): https://github.com/$repo/actions/runs/$qa_run
JDK-21-Lifecycle: $lifecycle (exakter Host-Pruefstand, keine Fold7-Garantie).
Echte Fold7-Geräteprüfung (Thermik, Sprache, Avatar, PDF, Rotation, Update): NICHT AUSGEFÜHRT.
Code51-to-Code$code: original package and signing certificate, higher versionCode. Exact Fold7 update/data preservation still NOT_RUN. Install only as an update; never uninstall or clear existing app data.
EOF
if [[ "$profile" == code54 && "$gate" == DEVICE_TEST_NATIVE_PDF_EXCEPTION_VERIFIED ]]; then
  cat >> "$tmp/notes.txt" <<EOF

*** UNVEROEFFENTLICHTER FOLD7-GERAETETEST-ENTWURF ***
Der strikte native PDF Hosttest ist NICHT bestanden. Er scheiterte an
java.io.FileDescriptor.getOwnerId$; andere Hosttests und exakt die
17 festgelegten Skip-Faelle wurden quellgebunden verifiziert.
Kein vollstaendiges Android QA PASS. PDF-Test auf Fold7: NOT_RUN.
Nur Besitzer-Installation als Update fuer gezielte lokale Abnahme.
NICHT veroeffentlichen, NICHT als Release-freigegeben bezeichnen.
EOF
fi
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

# 8. Signierung und unabhängige Prüfung beenden diesen Workflow.
# Das Release bleibt ausdrücklich ENTWURF. Veröffentlichung nur durch
# separaten, ausdrücklichen Besitzerentscheid; kein automatisches Publish.
select_exact_draft_id() {
  jq -ers --arg tag "$1" --arg source "$2" '
    [.[][] | select(.tag_name==$tag)] as $matches
    | if ($matches|length)==1 and $matches[0].draft==true
      and $matches[0].prerelease==false and $matches[0].target_commitish==$source
      and ($matches[0].id | type=="number" and .>0 and .==floor)
      then $matches[0].id else error("DRAFT_RELEASE_IDENTITY_MISMATCH") end
  '
}
verify_exact_draft_metadata() {
  jq -e --argjson id "$1" --arg tag "$2" --arg source "$3" --arg name "$4" \
    --arg sha "sha256:$5" --argjson size "$6" --arg url_prefix "$7" '
    .id==$id and .tag_name==$tag and .target_commitish==$source
    and .draft==true and .prerelease==false
    and (.html_url | type=="string" and startswith($url_prefix) and (test("[[:space:][:cntrl:]]")|not))
    and ([.assets[] | select(.name|test("[.]apk$";"i"))] | length==1)
    and ([.assets[] | select(.name==$name and .digest==$sha and .size==$size)] | length==1)
  '
}
gh api --paginate "repos/$repo/releases?per_page=100" > "$tmp/releases.json" || fail DRAFT_READBACK_UNREADABLE
draft_id="$(select_exact_draft_id "$tag" "$source_sha" < "$tmp/releases.json")" || fail DRAFT_RELEASE_IDENTITY_MISMATCH
[[ "$draft_id" =~ ^[1-9][0-9]*$ ]] || fail DRAFT_RELEASE_IDENTITY_MISMATCH
draft_json="$(gh api "repos/$repo/releases/$draft_id")" || fail DRAFT_READBACK_UNREADABLE
verify_exact_draft_metadata "$draft_id" "$tag" "$source_sha" "$name" "$digest" "$size" "https://github.com/$repo/releases/" \
  <<< "$draft_json" >/dev/null || fail DRAFT_RELEASE_METADATA_MISMATCH
draft_url="$(jq -er '.html_url' <<< "$draft_json")" || fail DRAFT_RELEASE_URL_UNREADABLE
latest_after="$(gh api "repos/$repo/releases/latest" --jq .tag_name)" || fail LATEST_AFTER_DRAFT_UNREADABLE
[[ "$latest_after" == "$latest_tag" ]] || fail DRAFT_CHANGED_LATEST_RELEASE
echo CHECKPOINT=SIGNED_DRAFT_READBACK_VERIFIED
echo STATUS=SIGNED_DRAFT_VERIFIED_NOT_PUBLISHED
printf 'DRAFT_RELEASE_URL=%s\nAPK_SHA256=%s\nAPK_BYTES=%s\nPUBLISHED=false\n' "$draft_url" "$digest" "$size"
