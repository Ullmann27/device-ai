#!/usr/bin/env bash
# Code58 original-certificate APK: protected owner-approved draft only, never publishes.
set -Eeuo pipefail
set +x
umask 077
fail(){ printf '::error::CODE58_SIGN_%s\n' "$1" >&2; exit 69; }
mode="$1"
[[ "$mode" == preflight || "$mode" == sign ]] || fail BAD_MODE
repo=Ullmann27/device-ai
sign_branch=chatgpt/code52-owner-signing-20261109
source_branch=release/code58-full-qa-20261114
qa_workflow=.github/workflows/continuum-code57-voice-full-qa.yml
source_sha="$SOURCE_SHA"
approved_sha="$OWNER_APPROVED_SHA"
qa_run="$QA_RUN_ID"
cert=6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23
pkg=at.heinz.deviceai.continuumlocal
unsigned_name=Continuum-Code58-UNSIGNED-NOT-INSTALLABLE.apk
tag="continuum-local-code58-$source_sha"
[[ "$GITHUB_REPOSITORY" == "$repo" ]] || fail WRONG_REPOSITORY
[[ "$GITHUB_REF" == "refs/heads/$sign_branch" ]] || fail WRONG_SIGN_BRANCH
[[ "$GITHUB_EVENT_NAME" == push ]] || fail WRONG_EVENT
[[ "$source_sha" =~ ^[0-9a-f]{40}$ && "$approved_sha" == "$source_sha" ]] || fail OWNER_SHA_UNAPPROVED
[[ "$qa_run" =~ ^[0-9]{6,20}$ ]] || fail QA_RUN_ID_INVALID
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || fail SIGNER_SOURCE_CHANGED
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] || fail DIRTY_SIGNER
for tool in gh jq git python3 keytool base64 sha256sum stat; do command -v "$tool" >/dev/null || fail TOOL_MISSING; done
tmp="$(mktemp -d)"
chmod 700 "$tmp"
trap 'rm -rf -- "$tmp"' EXIT

# Existing environment guard remains unchanged: owner review and exact signing branch.
gh api "repos/$repo/environments/continuum-original-signing" > "$tmp/env.json" || fail ENV_READ
jq -e '([.protection_rules[]?|select(.type=="required_reviewers")|.reviewers[]?
    |select(.type=="User" and .reviewer.login=="Ullmann27")]|length)>=1
    and .deployment_branch_policy.custom_branch_policies==true' "$tmp/env.json" >/dev/null || fail OWNER_REVIEW_REQUIRED
gh api "repos/$repo/environments/continuum-original-signing/deployment-branch-policies" > "$tmp/branch.json" || fail ENV_BRANCH_READ
jq -e --arg b "$sign_branch" '.branch_policies|length==1 and .[0].name==$b and .[0].type=="branch"' "$tmp/branch.json" >/dev/null || fail EXACT_SIGN_BRANCH_REQUIRED
echo CHECKPOINT=OWNER_REVIEW_AND_BRANCH_POLICY_VERIFIED

# The exact four-job, push-triggered Code58 QA run must be fully successful.
gh api "repos/$repo/actions/runs/$qa_run" > "$tmp/run.json" || fail QA_RUN_READ
jq -e --arg s "$source_sha" --arg b "$source_branch" --arg w "$qa_workflow" --argjson id "$qa_run" '
 .id==$id and .head_sha==$s and .head_branch==$b and .repository.full_name=="Ullmann27/device-ai"
 and .event=="push" and .status=="completed" and .conclusion=="success"
 and (.path==$w or (.path|startswith($w+"@"))) and (.run_attempt|type=="number" and .>=1)
' "$tmp/run.json" >/dev/null || fail FULL_QA_NOT_GREEN
attempt="$(jq -r '.run_attempt' "$tmp/run.json")"
gh api "repos/$repo/actions/runs/$qa_run/attempts/$attempt/jobs?per_page=100" > "$tmp/jobs.json" || fail JOBS_READ
jq -e '.total_count==4 and (.jobs|length)==4
 and ([.jobs[].name]|sort)==(["renderer-preflight","node-backend","full-android","unsigned-arm64"]|sort)
 and ([.jobs[]|select(.conclusion=="success" and .status=="completed")]|length)==4' "$tmp/jobs.json" >/dev/null || fail FOUR_JOBS_NOT_GREEN
gh api "repos/$repo/actions/runs/$qa_run/artifacts?per_page=100" > "$tmp/artifacts.json" || fail ARTIFACT_METADATA_READ
current="$(gh api "repos/$repo/git/ref/heads/$source_branch" --jq '.object.sha')" || fail RELEASE_BRANCH_READ
[[ "$current" == "$source_sha" ]] || fail SOURCE_MOVED_AFTER_QA
git fetch --no-tags --depth=1 origin "$source_sha" >/dev/null 2>&1 || fail SOURCE_FETCH
git worktree add --detach "$tmp/source" "$source_sha" >/dev/null 2>&1 || fail SOURCE_WORKTREE
source_tree="$(git -C "$tmp/source" rev-parse 'HEAD^{tree}')" || fail SOURCE_TREE_READ

# Every proof is GitHub-source-bound and ZIP-hash-verified before extraction.
proof() {
  local name="$1" dir="$2" id size digest
  jq -e --arg n "$name" --arg s "$source_sha" --arg b "$source_branch" --argjson run "$qa_run" '
   ([.artifacts[]|select(.name==$n)]|length)==1
   and ([.artifacts[]|select(.name==$n)][0]|
      .expired==false and .size_in_bytes>100 and (.digest|test("^sha256:[0-9a-f]{64}$"))
      and .workflow_run.id==$run and .workflow_run.head_sha==$s and .workflow_run.head_branch==$b)
  ' "$tmp/artifacts.json" >/dev/null || fail PROOF_METADATA_INVALID
  id="$(jq -r --arg n "$name" '.artifacts[]|select(.name==$n)|.id' "$tmp/artifacts.json")"
  size="$(jq -r --arg n "$name" '.artifacts[]|select(.name==$n)|.size_in_bytes' "$tmp/artifacts.json")"
  digest="$(jq -r --arg n "$name" '.artifacts[]|select(.name==$n)|.digest' "$tmp/artifacts.json")"
  gh api "repos/$repo/actions/artifacts/$id/zip" > "$tmp/$dir.zip" || fail ARTIFACT_DOWNLOAD
  [[ "$(stat -c %s "$tmp/$dir.zip")" == "$size" ]] || fail ARTIFACT_SIZE
  [[ "sha256:$(sha256sum "$tmp/$dir.zip"|cut -d' ' -f1)" == "$digest" ]] || fail ARTIFACT_DIGEST
  mkdir "$tmp/$dir"
  python3 -B - "$tmp/$dir.zip" "$tmp/$dir" <<'PY' || fail ARCHIVE_INVALID
import pathlib,sys,zipfile,shutil
archive,root=pathlib.Path(sys.argv[1]),pathlib.Path(sys.argv[2])
with zipfile.ZipFile(archive) as z:
    es=z.infolist()
    assert 0<len(es)<15000 and sum(e.file_size for e in es)<650_000_000 and z.testzip() is None
    for e in es:
        p=pathlib.PurePosixPath(e.filename)
        assert not p.is_absolute() and '..' not in p.parts
        assert ((e.external_attr>>16)&0o170000)!=0o120000
        t=root.joinpath(*p.parts)
        assert t.resolve().is_relative_to(root.resolve())
        if e.is_dir(): t.mkdir(parents=True,exist_ok=True); continue
        t.parent.mkdir(parents=True,exist_ok=True)
        with z.open(e) as a,t.open('xb') as b: shutil.copyfileobj(a,b)
PY
}
proof Code58-UNSIGNED-NOT-INSTALLABLE unsigned
proof Code58-Android-full-QA android
proof Code58-Node-backend-QA node
echo CHECKPOINT=SHA_BOUND_ARTIFACTS_VERIFIED

# Recompute actual JUnit results and preserve the declared 17-skip contract.
python3 -B - "$tmp/android" "$tmp/source" "$source_sha" <<'PY' || fail JUNIT_LINT_EVIDENCE
import json,os,pathlib,subprocess,sys,xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1]);source=pathlib.Path(sys.argv[2]);sha=sys.argv[3]
xmls=sorted(root.rglob('TEST-*.xml'))
assert len(xmls)>500
cases=[c for x in xmls for c in ET.parse(x).getroot().iter('testcase')]
assert len(cases)>5400
assert not any(c.find('failure') is not None or c.find('error') is not None for c in cases)
assert sum(c.find('skipped') is not None for c in cases)==17
summary=subprocess.run([sys.executable,'-B',str(source/'tools/code55-apk/junit-summary.py'),str(xmls[0].parent),'--strict-skips'],
    env={**os.environ,'GITHUB_SHA':sha},text=True,capture_output=True,check=True)
data=json.loads(summary.stdout)
assert data['errors']==0 and data['failures']==0 and data['skipped']==17
lint=list(root.rglob('lint-results-debug.xml'))
assert len(lint)==1
result=ET.parse(lint[0]).getroot()
assert result.tag=='issues' and not [v for v in result.findall('issue') if v.get('severity') in ('Fatal','Error')]
print('CHECKPOINT=STRICT_JUNIT_17_SKIPS_AND_LINT_VERIFIED')
PY

# Verify original APK package and immutable version.
unsigned="$(find "$tmp/unsigned" -type f -name Continuum-Code58-UNSIGNED-NOT-INSTALLABLE.apk -print -quit)"
[[ -n "$unsigned" && -s "$unsigned" ]] || fail UNSIGNED_APK_MISSING
[[ "$(find "$tmp/unsigned" -type f -name Continuum-Code58-UNSIGNED-NOT-INSTALLABLE.apk|wc -l)" == 1 ]] || fail DUPLICATE_APK
info="$(find "$tmp/unsigned" -type f -name BUILD-INFO.txt -print -quit)"
hashes="$(find "$tmp/unsigned" -type f -name SHA256SUMS.txt -print -quit)"
[[ -s "$info" && -s "$hashes" ]] || fail APK_RECEIPT_MISSING
for field in "source=$source_sha" "source_tree=$source_tree" "package=$pkg" \
  "versionCode=58" "versionName=0.3.57" "abi=arm64-v8a" \
  "status=UNSIGNED_NOT_INSTALLABLE" "fold7_test=NOT_RUN"; do
    grep -Fxq "$field" "$info" || fail SOURCE_APK_RECEIPT_INVALID
done
unsigned_sha="$(sha256sum "$unsigned"|cut -d' ' -f1)"
grep -Eq "^$unsigned_sha[[:space:]]+.*Continuum-Code58-UNSIGNED-NOT-INSTALLABLE[.]apk$" "$hashes" || fail APK_CHECKSUM_INVALID
tools="$ANDROID_HOME/build-tools/35.0.0"
[[ -x "$tools/aapt2" && -x "$tools/apksigner" && -x "$tools/zipalign" ]] || fail SIGNING_TOOLS_MISSING
"$tools/aapt2" dump badging "$unsigned" > "$tmp/badging" || fail UNSIGNED_BADGING
grep -Fq "package: name='$pkg' versionCode='58' versionName='0.3.57'" "$tmp/badging" || fail PACKAGE_MISMATCH
grep -Fxq "native-code: 'arm64-v8a'" "$tmp/badging" || fail ABI_MISMATCH
"$tools/zipalign" -c -P 16 -v 4 "$unsigned" >/dev/null || fail ALIGNMENT_INVALID
if "$tools/apksigner" verify "$unsigned" >/dev/null 2>&1;then fail UNEXPECTED_SIGNED_APK;fi
latest="$(gh api "repos/$repo/releases/latest" --jq '.tag_name')" || fail LATEST_READ
[[ "$latest" == continuum-local-code55-889e06d47fffd2230fa5b062dd1f31b65992e728 ]] || fail LATEST_BASELINE_CHANGED
if gh release view "$tag" --repo "$repo" >/dev/null 2>&1;then fail DUPLICATE_CODE58_RELEASE;fi
echo CHECKPOINT=CODE58_SIGNING_PREFLIGHT_VALID
[[ "$mode" == sign ]] || { echo STATUS=AWAITING_OWNER_REVIEW;exit 0; }

# Secrets only in the environment-protected job after exact-owner approval.
[[ -n "$ORIGINAL_P12" && -n "$ORIGINAL_PASSWORD" ]] || fail ORIGINAL_SECRETS_MISSING
printf %s "$ORIGINAL_P12"|base64 --decode > "$tmp/original.p12" 2>/dev/null || fail ORIGINAL_P12_INVALID
unset ORIGINAL_P12
chmod 600 "$tmp/original.p12"
export SIGN_PASS="$ORIGINAL_PASSWORD"
unset ORIGINAL_PASSWORD
keytool -list -v -storetype PKCS12 -alias continuum-local -keystore "$tmp/original.p12" \
  -storepass:env SIGN_PASS > "$tmp/key-info" 2>/dev/null || fail ORIGINAL_CERT_UNREADABLE
grep -Fq 'Entry type: PrivateKeyEntry' "$tmp/key-info" || fail KEY_NOT_PRIVATE
key_fingerprint="$(sed -n 's/^[[:space:]]*SHA256: //p' "$tmp/key-info"|head -n1|tr -d ':'|tr '[:upper:]' '[:lower:]')"
rm -f "$tmp/key-info"
[[ "$key_fingerprint" == "$cert" ]] || fail WRONG_ORIGINAL_KEY
"$tools/zipalign" -P 16 -f 4 "$unsigned" "$tmp/aligned.apk" >/dev/null || fail PRE_SIGN_ALIGNMENT
"$tools/apksigner" sign --ks "$tmp/original.p12" --ks-type PKCS12 --ks-key-alias continuum-local \
  --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --out "$tmp/signed.apk" "$tmp/aligned.apk" 2>/dev/null || fail SIGN_FAILED
unset SIGN_PASS
rm -f "$tmp/original.p12"
"$tools/apksigner" verify --verbose --print-certs "$tmp/signed.apk" > "$tmp/verification" || fail SIGNATURE_INVALID
grep -Fqx 'Verified using v2 scheme (APK Signature Scheme v2): true' "$tmp/verification" || fail SIGNATURE_V2_MISSING
grep -Fqx "Signer #1 certificate SHA-256 digest: $cert" "$tmp/verification" || fail SIGNER_CERT_MISMATCH
"$tools/zipalign" -c -P 16 -v 4 "$tmp/signed.apk" >/dev/null || fail SIGNED_ALIGNMENT
"$tools/aapt2" dump badging "$tmp/signed.apk" > "$tmp/signed-badging"
grep -Fq "package: name='$pkg' versionCode='58' versionName='0.3.57'" "$tmp/signed-badging" || fail SIGNED_BADGING
digest="$(sha256sum "$tmp/signed.apk"|cut -d' ' -f1)"
apk="Continuum-Lokal-$digest.apk"
mv "$tmp/signed.apk" "$tmp/$apk"
cat > "$tmp/BUILD-INFO.txt" <<INFO
source_sha=$source_sha
source_tree=$source_tree
versionCode=58
versionName=0.3.57
package=$pkg
original_signer_sha256=$cert
apk_sha256=$digest
apk_bytes=$(stat -c %s "$tmp/$apk")
qa_run_id=$qa_run
fold7_test=NOT_RUN
status=ORIGINAL_SIGNED_UNPUBLISHED_DRAFT
INFO
cat > "$tmp/notes.txt" <<NOTES
Continuum 0.3.57 (Code58), originalsignierter Besitzer-Testkandidat.
Quell-SHA: $source_sha
GitHub-QA: https://github.com/$repo/actions/runs/$qa_run
Paket: $pkg / versionCode 58 / originaler Fingerabdruck $cert.
Fold7-Realtests nicht ausgefuehrt. Bestehende App-Daten nicht loeschen, nur als Update installieren.
Nicht veroeffentlicht; nur fuer den Besitzer freigegebener Testentwurf.
NOTES
gh release create "$tag" "$tmp/$apk" "$tmp/BUILD-INFO.txt" --repo "$repo" --target "$source_sha" \
  --title 'Continuum Lokal 0.3.57 (Code58)' --notes-file "$tmp/notes.txt" --draft --latest=false >/dev/null || fail GITHUB_DRAFT_CREATE
mkdir "$tmp/readback"
gh release download "$tag" --repo "$repo" --pattern "$apk" --dir "$tmp/readback" >/dev/null || fail DRAFT_DOWNLOAD
cmp -s "$tmp/$apk" "$tmp/readback/$apk" || fail DRAFT_BYTES_MISMATCH
"$tools/apksigner" verify --verbose --print-certs "$tmp/readback/$apk" > "$tmp/draft-cert" || fail DRAFT_SIGNATURE_INVALID
grep -Fqx "Signer #1 certificate SHA-256 digest: $cert" "$tmp/draft-cert" || fail DRAFT_CERT_MISMATCH
[[ "$(gh api "repos/$repo/releases/latest" --jq '.tag_name')" == "$latest" ]] || fail UNAUTHORIZED_PUBLICATION
echo CHECKPOINT=CODE58_SIGNED_GITHUB_DRAFT_READBACK_VERIFIED
echo STATUS=SIGNED_DRAFT_NOT_PUBLISHED
echo "DRAFT_RELEASE_TAG=$tag"
echo "APK_SHA256=$digest"
