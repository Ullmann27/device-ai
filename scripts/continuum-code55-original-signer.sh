#!/usr/bin/env bash
# Code55 only: source-bound, owner-approved original-signature draft.
set -Eeuo pipefail
set +x
umask 077
fail() { printf '::error::CODE55_SIGN_%s\n' "$1" >&2; exit 69; }
mode="${1:-}"
[[ "$mode" == preflight || "$mode" == sign ]] || fail BAD_MODE
repo=Ullmann27/device-ai
signing_branch=chatgpt/code52-owner-signing-20261109
target_branch=integrate/code55-apk-fable-20261112
qa_path=.github/workflows/continuum-code55-full-qa.yml
transport_path=.github/workflows/continuum-code55-owner-apk.yml
qa_workflow_blob=c2070c5a0ed943dcf8ee8181f83011f5a41a8959
transport_workflow_blob=ba6df861bcff41580637394a0ebfb21a066acb7f
certificate_sha=6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23
source_sha="${SOURCE_SHA:-}"
approved_sha="${OWNER_APPROVED_SHA:-}"
qa_run_id="${QA_RUN_ID:-}"
transport_run_id="${TRANSPORT_RUN_ID:-}"
[[ "${GITHUB_REPOSITORY:-}" == "$repo" ]] || fail REPOSITORY
[[ "${GITHUB_REF:-}" == "refs/heads/$signing_branch" ]] || fail SIGNER_BRANCH
[[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]] || fail SIGNER_EVENT
[[ "$source_sha" =~ ^[a-f0-9]{40}$ ]] || fail SOURCE_SHA
[[ "$approved_sha" == "$source_sha" ]] || fail OWNER_SHA_APPROVAL
[[ "$qa_run_id" =~ ^[0-9]{6,20}$ ]] || fail QA_RUN_FORMAT
[[ "$transport_run_id" =~ ^[0-9]{6,20}$ ]] || fail TRANSPORT_RUN_FORMAT
[[ "$(git rev-parse HEAD)" == "${GITHUB_SHA:-}" ]] || fail SIGNER_SOURCE
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] || fail DIRTY_SIGNER
for tool in gh jq python3 sha256sum keytool; do command -v "$tool" >/dev/null || fail MISSING_TOOL; done
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
# The existing protected environment must be owner-reviewed and pinned to this branch.
gh api "repos/$repo/environments/continuum-original-signing" > "$tmp/environment.json" || fail ENVIRONMENT
jq -e '[.protection_rules[]? | select(.type=="required_reviewers") | .reviewers[]? | select(.type=="User" and .reviewer.login=="Ullmann27")] | length>=1' "$tmp/environment.json" >/dev/null || fail OWNER_REVIEWER_MISSING
jq -e '.deployment_branch_policy.custom_branch_policies==true' "$tmp/environment.json" >/dev/null || fail ENVIRONMENT_BRANCH_POLICY
gh api "repos/$repo/environments/continuum-original-signing/deployment-branch-policies" > "$tmp/branches.json" || fail ENVIRONMENT_BRANCH_POLICY_UNREADABLE
jq -e --arg branch "$signing_branch" '.branch_policies | length==1 and .[0].name==$branch and .[0].type=="branch"' "$tmp/branches.json" >/dev/null || fail ENVIRONMENT_BRANCH_POLICY_CHANGED
# Source SHA and both QA workflow blobs are immutable and owner-verifiable.
gh api "repos/$repo/git/commits/$source_sha" > "$tmp/source.json" || fail SOURCE_MISSING
source_tree="$(jq -er '.tree.sha' "$tmp/source.json")"
for kind in qa transport; do
  if [[ "$kind" == qa ]]; then p="$qa_path"; expected="$qa_workflow_blob"; else p="$transport_path"; expected="$transport_workflow_blob"; fi
  actual="$(gh api -X GET "repos/$repo/contents/$p?ref=$source_sha" --jq '.sha')" || fail WORKFLOW_SOURCE_UNREADABLE
  [[ "$actual" == "$expected" ]] || fail WORKFLOW_SOURCE_CHANGED
done
gh api "repos/$repo/actions/runs/$qa_run_id" > "$tmp/qa-run.json" || fail QA_RUN_UNREADABLE
gh api "repos/$repo/actions/runs/$transport_run_id" > "$tmp/transport-run.json" || fail TRANSPORT_RUN_UNREADABLE
for kind in qa transport; do
  if [[ "$kind" == qa ]]; then p="$qa_path"; else p="$transport_path"; fi
  jq -e --arg sha "$source_sha" --arg branch "$target_branch" --arg path "$p" '
    .head_sha==$sha and .head_branch==$branch and .event=="push" and
    .status=="completed" and .conclusion=="success" and
    (.path==$path or (.path|startswith($path+"@")))
  ' "$tmp/$kind-run.json" >/dev/null || fail QA_RUN_NOT_GREEN
done
gh api "repos/$repo/actions/runs/$qa_run_id/jobs?per_page=100" > "$tmp/qa-jobs.json" || fail QA_JOBS
jq -e '([.jobs[] | select(.name=="full-android" and .conclusion=="success")] | length)==1 and
    ([.jobs[] | select(.name=="full-android" and .conclusion=="success") | .steps[]?
      | select(.name=="Full Android JUnit and UI tests - no filters, strict native PDF" and .conclusion=="success")] | length)==1 and
    ([.jobs[] | select(.name=="full-android" and .conclusion=="success") | .steps[]?
      | select(.name=="Configured Android lint" and .conclusion=="success")] | length)==1' "$tmp/qa-jobs.json" >/dev/null || fail FULL_ANDROID_GATE
gh api "repos/$repo/actions/runs/$transport_run_id/jobs?per_page=100" > "$tmp/transport-jobs.json" || fail TRANSPORT_JOBS
jq -e '([.jobs[] | select(.name=="scoped-android" and .conclusion=="success")] | length)==1 and
  ([.jobs[] | select(.name=="unsigned-arm64" and .conclusion=="success")] | length)==1 and
  ([.jobs[] | select(.name=="unsigned-arm64" and .conclusion=="success") | .steps[]?
   | select(.name=="Verify APK against original assets, package and 16 KiB alignment" and .conclusion=="success")] | length)==1' "$tmp/transport-jobs.json" >/dev/null || fail UNSIGNED_AND_SCOPED_GATE
for kind in qa transport; do
  if [[ "$kind" == qa ]]; then run="$qa_run_id"; name="Code55-full-Android-source-QA"; else run="$transport_run_id"; name="Code55-UNSIGNED-NOT-INSTALLABLE"; fi
  gh api "repos/$repo/actions/runs/$run/artifacts?per_page=100" > "$tmp/$kind-artifacts.json" || fail ARTIFACTS_UNREADABLE
  aid="$(jq -er --arg name "$name" '[.artifacts[] | select(.name==$name and .expired==false)] | if length==1 then .[0].id else empty end' "$tmp/$kind-artifacts.json")" || fail ARTIFACT_UNBOUND
  digest="$(jq -er --arg name "$name" '[.artifacts[] | select(.name==$name and .expired==false)] | .[0].digest' "$tmp/$kind-artifacts.json")" || fail ARTIFACT_DIGEST
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || fail ARTIFACT_DIGEST_INVALID
  gh api "repos/$repo/actions/artifacts/$aid/zip" > "$tmp/$kind.zip" || fail ARTIFACT_DOWNLOAD
  echo "${digest#sha256:}  $tmp/$kind.zip" | sha256sum -c --status || fail ARTIFACT_HASH
done
# Inspect full JUnit and unsigned receipt inside original authenticated ZIPs, without trusting ZIP paths.
python3 - "$tmp/qa.zip" "$tmp/transport.zip" "$tmp/unsigned.apk" "$source_sha" "$source_tree" <<'PY'
import hashlib,json,pathlib,sys,xml.etree.ElementTree as ET,zipfile,stat
qa,transport,destination,commit,tree=map(str,sys.argv[1:])
def read_zip(path):
    z=zipfile.ZipFile(path)
    names=z.namelist()
    if len(names)!=len(set(names)) or z.testzip() is not None: raise ValueError("INVALID_ZIP")
    for member in z.infolist():
        p=pathlib.PurePosixPath(member.filename)
        if p.is_absolute() or ".." in p.parts or chr(92) in member.filename: raise ValueError("UNSAFE_ZIP_PATH")
        if stat.S_IFMT(member.external_attr>>16)==stat.S_IFLNK: raise ValueError("UNSAFE_ZIP_LINK")
    return z
with read_zip(qa) as z:
    tests=[x for x in z.namelist() if "/test-results/testDebugUnitTest/TEST-" in x and x.endswith(".xml")]
    if len(tests)<50: raise ValueError("INCOMPLETE_ANDROID_SUITE")
    total=failures=errors=0
    native=[]
    for name in tests:
        root=ET.fromstring(z.read(name))
        total+=int(root.attrib["tests"])
        failures+=int(root.attrib.get("failures",0))
        errors+=int(root.attrib.get("errors",0))
        for case in root.findall("testcase"):
            if case.attrib.get("classname")=="at.heinz.deviceai.PdfIntakeNativeDecodeProbeUiTest" and case.attrib.get("name")=="actualProviderReadRendersAndExtractsSyntheticPdf":
                native.append(case)
    if total<4000 or failures or errors or len(native)!=1: raise ValueError("ANDROID_CASES_NOT_FULL_GREEN")
    if native[0].find("failure") is not None or native[0].find("error") is not None or native[0].find("skipped") is not None: raise ValueError("NATIVE_PDF_NOT_PASS")
with read_zip(transport) as z:
    def exactly(suffix):
        names=[n for n in z.namelist() if n.endswith(suffix)]
        if len(names)!=1: raise ValueError("MISSING_OR_DUPLICATE_REQUIRED_FILE_"+suffix)
        return z.read(names[0])
    receipt=json.loads(exactly("/code55-apk-audit/RESULT.json"))
    apk=exactly("/app/build/outputs/apk/release/app-release-unsigned.apk")
    digest=hashlib.sha256(apk).hexdigest()
    if receipt.get("status")!="PASS" or receipt.get("artifactStatus")!="UNSIGNED_NOT_INSTALLABLE":
        raise ValueError("UNSIGNED_VALIDATION_NOT_PASS")
    if receipt.get("expectedSource")!=commit or receipt.get("expectedTree")!=tree or receipt.get("sha256")!=digest or receipt.get("bytes")!=len(apk):
        raise ValueError("SOURCE_OR_APK_HASH_MISMATCH")
    identity=receipt.get("identity",{})
    if identity.get("package")!="at.heinz.deviceai.continuumlocal" or identity.get("versionCode")!=55 or identity.get("versionName")!="0.3.54" or identity.get("abi")!="arm64-v8a" or identity.get("debuggable")!=False:
        raise ValueError("APK_PACKAGE_VERSION_OR_ABI_MISMATCH")
    if receipt.get("sourceBefore",{}).get("commit")!=commit or receipt.get("sourceAfter",{}).get("commit")!=commit or receipt.get("sourceBefore",{}).get("tree")!=tree or receipt.get("sourceAfter",{}).get("tree")!=tree:
        raise ValueError("SOURCE_NOT_STABLE")
    pathlib.Path(destination).write_bytes(apk)
print("CODE55_SOURCE_BOUND_STRICT_QA_AND_UNSIGNED_APK_VERIFIED")
PY
[[ -s "$tmp/unsigned.apk" ]] || fail UNSIGNED_MISSING
tools="${ANDROID_HOME:-}/build-tools/35.0.0"
[[ -x "$tools/aapt2" && -x "$tools/apksigner" && -x "$tools/zipalign" ]] || fail ANDROID_BUILD_TOOLS_MISSING
badging="$("$tools/aapt2" dump badging "$tmp/unsigned.apk")" || fail BADGING
grep -Fq "package: name='at.heinz.deviceai.continuumlocal' versionCode='55' versionName='0.3.54'" <<< "$badging" || fail APK_ID
grep -Fxq "native-code: 'arm64-v8a'" <<< "$badging" || fail ABI
if "$tools/apksigner" verify "$tmp/unsigned.apk" >/dev/null 2>&1; then fail UNEXPECTED_SIGNED_INPUT; fi
latest="$(gh api "repos/$repo/releases/latest" --jq .tag_name)" || fail LATEST_RELEASE
[[ "$latest" =~ ^continuum-local-code([0-9]{2,4})-[a-f0-9]{40}$ ]] || fail LATEST_UNKNOWN
[[ "${BASH_REMATCH[1]}" -lt 55 ]] || fail REFUSE_DOWNGRADE
tag="continuum-local-code55-$source_sha"
if gh release view "$tag" -R "$repo" >/dev/null 2>&1; then fail ALREADY_SIGNED; fi
[[ "$mode" == sign ]] || { echo CODE55_PREFLIGHT_PASS_NO_SECRETS; exit 0; }
[[ -n "${ORIGINAL_P12:-}" && -n "${ORIGINAL_PASSWORD:-}" ]] || fail ORIGINAL_SECRETS_NOT_AVAILABLE
printf '%s' "$ORIGINAL_P12" | base64 --decode > "$tmp/original.p12" 2>/dev/null || fail P12_INVALID
unset ORIGINAL_P12
chmod 600 "$tmp/original.p12"
export SIGN_PASS="$ORIGINAL_PASSWORD"
unset ORIGINAL_PASSWORD
keytool -list -v -storetype PKCS12 -alias continuum-local -keystore "$tmp/original.p12" -storepass:env SIGN_PASS > "$tmp/certinfo.txt" 2>/dev/null || fail PRIVATE_KEY
fingerprint="$(sed -n 's/^[[:space:]]*SHA256: //p' "$tmp/certinfo.txt" | head -1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"
rm -f "$tmp/certinfo.txt"
[[ "$fingerprint" == "$certificate_sha" ]] || fail ORIGINAL_CERTIFICATE_MISMATCH
"$tools/zipalign" -P 16 -f 4 "$tmp/unsigned.apk" "$tmp/aligned.apk" >/dev/null || fail ALIGN
"$tools/apksigner" sign --ks "$tmp/original.p12" --ks-type PKCS12 --ks-key-alias continuum-local --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --out "$tmp/signed.apk" "$tmp/aligned.apk" >/dev/null 2>&1 || fail SIGNING
unset SIGN_PASS
"$tools/apksigner" verify --verbose --print-certs "$tmp/signed.apk" > "$tmp/verification.txt" || fail SIGNED_VERIFY
grep -Fq "$certificate_sha" "$(tr -d ':' < "$tmp/verification.txt" | tr '[:upper:]' '[:lower:]' > "$tmp/cert-normalized.txt"; echo "$tmp/cert-normalized.txt")" || fail SIGNED_CERTIFICATE
"$tools/zipalign" -c -P 16 -v 4 "$tmp/signed.apk" >/dev/null || fail SIGNED_ALIGNMENT
hash="$(sha256sum "$tmp/signed.apk" | cut -d' ' -f1)"
name="Continuum-Lokal-Code55-$hash.apk"
mv "$tmp/signed.apk" "$tmp/$name"
cat > "$tmp/notes.txt" <<EOF
Code55 original-signed private Fold7 device-test draft. Not published or marked Latest.
source_sha=$source_sha
source_tree=$source_tree
qa_run_id=$qa_run_id
transport_run_id=$transport_run_id
signed_sha256=$hash
certificate_sha256=$certificate_sha
package=at.heinz.deviceai.continuumlocal
versionCode=55
versionName=0.3.54
Fold7=NOT_RUN
EOF
gh release create "$tag" "$tmp/$name" -R "$repo" --draft --latest=false --title "Continuum Code55 0.3.54 Originalsigniert - Testentwurf" --notes-file "$tmp/notes.txt" --target "$source_sha" >/dev/null || fail DRAFT_CREATION
[[ "$(gh release view "$tag" -R "$repo" --json isDraft --jq .isDraft)" == true ]] || fail DRAFT_NOT_PRIVATE
mkdir "$tmp/readback"
gh release download "$tag" -R "$repo" --pattern "$name" --dir "$tmp/readback" >/dev/null || fail RELEASE_READBACK
cmp -s "$tmp/$name" "$tmp/readback/$name" || fail RELEASE_READBACK_MISMATCH
echo CODE55_ORIGINAL_SIGNED_DRAFT_READBACK_VERIFIED
