#!/usr/bin/env bash
# Code51/55 source-bound original signing.
# Historical filename retained for the already registered workflow. Code50 branch is unchanged.
# Modi: preflight (ohne Geheimnisse) | sign (nur im geschützten Environment).
# Signing material is used only inside the protected job and never printed or exported.
set -Eeuo pipefail
set +x
umask 077
fail() { printf '::error::CODE%s_SIGN_%s\n' "${code:-51}" "$1" >&2; exit 69; }

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
if [[ "${GITHUB_REF:-}" == refs/heads/integrate/code55-apk-fable-20261112 ]]; then
  branch="integrate/code55-apk-fable-20261112"
  qa_workflow_path=".github/workflows/continuum-code55-full-qa.yml"
  code="55"
  name_version="0.3.54"
  artifact="Code55-UNSIGNED-NOT-INSTALLABLE"
  unsigned_name="app-release-unsigned.apk"
fi
apk_run="${APK_RUN_ID:-}"

source_sha="${SOURCE_SHA:-}"
qa_run="${QA_RUN_ID:-}"
approved_sha="${OWNER_APPROVED_SHA:-}"

[[ "${GITHUB_REPOSITORY:-}" == "$repo" ]] || fail WRONG_REPOSITORY
[[ "${GITHUB_REF:-}" == "refs/heads/$branch" ]] || fail WRONG_BRANCH
[[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]] || fail WRONG_EVENT
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail SOURCE_SHA_FORMAT
[[ "$approved_sha" =~ ^[0-9a-f]{40}$ ]] || fail APPROVED_SHA_FORMAT
[[ "$qa_run" =~ ^[0-9]{6,20}$ ]] || fail QA_RUN_FORMAT
if [[ "$code" == 55 ]]; then
  [[ "$apk_run" =~ ^[0-9]{6,20}$ && "$apk_run" != "$qa_run" ]] || fail APK_RUN_FORMAT
else
  [[ -z "$apk_run" ]] || fail UNEXPECTED_APK_RUN
fi
[[ "$source_sha" == "$approved_sha" ]] || fail APPROVED_SHA_DIFFERS_FROM_SOURCE
[[ "$source_sha" == "${GITHUB_SHA:-}" ]] || fail SOURCE_DIFFERS_FROM_RUN_COMMIT
tag="continuum-local-code${code}-${source_sha}"
for cmd in gh jq base64 sha256sum stat curl; do command -v "$cmd" >/dev/null || fail TOOL_MISSING; done
tmp="$(mktemp -d)"
chmod 700 "$tmp"
trap 'rm -rf -- "$tmp"' EXIT

verify_code55_run() {
  local run_id="$1" workflow="$2" events="$3" required="$4" prefix="$5"
  gh api "repos/$repo/actions/runs/$run_id" > "$tmp/$prefix-run.json" || fail QA_RUN_UNREADABLE
  jq -e --arg sha "$source_sha" --arg b "$branch" --arg path "$workflow" --argjson run "$run_id" --argjson events "$events" '
    .id==$run and .head_sha==$sha and .head_branch==$b and .repository.full_name=="Ullmann27/device-ai"
    and .head_repository.id==.repository.id and .status=="completed" and .conclusion=="success"
    and (.event as $e | $events | index($e)!=null)
    and (.path==$path or .path==($path+"@refs/heads/"+$b))
    and (.run_attempt|type=="number" and .>=1 and floor==.)
    and (.run_started_at|test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"))
  ' "$tmp/$prefix-run.json" >/dev/null || fail QA_IDENTITY_OR_RESULT_MISMATCH
  local attempt
  attempt="$(jq -r .run_attempt "$tmp/$prefix-run.json")"
  gh api "repos/$repo/actions/runs/$run_id/attempts/$attempt/jobs?per_page=100" > "$tmp/$prefix-jobs.json" ||
    fail QA_JOBS_UNREADABLE
  jq -e --argjson names "$required" '
    .total_count==(.jobs|length) and (.jobs|length)==($names|length)
    and ([.jobs[].name]|sort)==($names|sort)
    and all(.jobs[]; .status=="completed" and .conclusion=="success")
  ' "$tmp/$prefix-jobs.json" >/dev/null || fail REQUIRED_JOBS_NOT_GREEN
}

download_code55_proof() {
  local run_id="$1" name="$2" prefix="$3" metadata="$4"
  gh api "repos/$repo/actions/runs/$run_id/artifacts?per_page=100" > "$tmp/$prefix-artifacts.json" ||
    fail ARTIFACT_METADATA_UNREADABLE
  local started id digest size repository_id
  started="$(jq -r .run_started_at "$tmp/$metadata-run.json")"
  repository_id="$(jq -r .repository.id "$tmp/$metadata-run.json")"
  jq -e --arg name "$name" --arg sha "$source_sha" --arg b "$branch" --arg started "$started" --argjson run "$run_id" --argjson repository "$repository_id" '
    .total_count==(.artifacts|length)
    and ([.artifacts[]|select(.name==$name)]|length)==1
    and ([.artifacts[]|select(.name==$name)][0] |
      .expired==false and (.id|type=="number" and .>0 and floor==.)
      and (.size_in_bytes|type=="number" and .>0 and .<=2147483648)
      and (.digest|test("^sha256:[0-9a-f]{64}$"))
      and (.created_at|test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$"))
      and .created_at >= $started and .workflow_run.id==$run
      and .workflow_run.head_sha==$sha and .workflow_run.head_branch==$b
      and .workflow_run.repository_id==$repository and .workflow_run.head_repository_id==$repository)
  ' "$tmp/$prefix-artifacts.json" >/dev/null || fail ARTIFACT_IDENTITY_OR_EXPIRY
  id="$(jq -r --arg name "$name" '.artifacts[]|select(.name==$name)|.id' "$tmp/$prefix-artifacts.json")"
  digest="$(jq -r --arg name "$name" '.artifacts[]|select(.name==$name)|.digest' "$tmp/$prefix-artifacts.json")"
  size="$(jq -r --arg name "$name" '.artifacts[]|select(.name==$name)|.size_in_bytes' "$tmp/$prefix-artifacts.json")"
  gh api "repos/$repo/actions/artifacts/$id/zip" > "$tmp/$prefix.zip" || fail ARTIFACT_DOWNLOAD_FAILED
  [[ "$(stat -c %s "$tmp/$prefix.zip")" == "$size" ]] || fail ARTIFACT_SIZE_MISMATCH
  [[ "sha256:$(sha256sum "$tmp/$prefix.zip" | cut -d' ' -f1)" == "$digest" ]] || fail ARTIFACT_DIGEST_MISMATCH
  python3 - "$tmp/$prefix.zip" "$tmp/$prefix" <<'PY' || fail ARTIFACT_ARCHIVE_UNSAFE
from pathlib import Path, PurePosixPath
import shutil, stat, sys, zipfile
archive, destination = map(Path, sys.argv[1:])
destination.mkdir(mode=0o700)
with zipfile.ZipFile(archive) as source:
    entries = source.infolist()
    assert 0 < len(entries) <= 50000
    assert sum(x.file_size for x in entries) <= 4294967296
    names = set()
    for entry in entries:
        name = entry.filename
        path = PurePosixPath(name)
        assert path.parts and name and not name.startswith("/") and "\\" not in name and all(ord(c) >= 32 and ord(c) != 127 for c in name)
        assert ".." not in path.parts and name.rstrip("/") == path.as_posix()
        assert path.as_posix() not in names
        names.add(path.as_posix())
        kind = stat.S_IFMT(entry.external_attr >> 16)
        assert kind in (0, stat.S_IFREG, stat.S_IFDIR)
        assert 0 <= entry.file_size <= 2147483648
    assert source.testzip() is None
    for entry in entries:
        target = destination / entry.filename
        if entry.is_dir():
            target.mkdir(parents=True, exist_ok=True)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            with source.open(entry) as incoming, target.open("xb") as outgoing:
                shutil.copyfileobj(incoming, outgoing)
PY
}

verify_code55_evidence() {
  for cmd in git node python3; do command -v "$cmd" >/dev/null || fail TOOL_MISSING; done
  [[ -n "${ANDROID_HOME:-}" ]] || fail ANDROID_HOME_MISSING
  tools="$ANDROID_HOME/build-tools/35.0.0"
  test -x "$tools/apksigner" && test -x "$tools/aapt2" && test -x "$tools/zipalign" || fail ANDROID_SIGN_TOOLS_MISSING
  [[ "$(git rev-parse HEAD)" == "$source_sha" ]] || fail CHECKOUT_SHA_MISMATCH
  [[ -z "$(git status --porcelain --untracked-files=normal)" ]] || fail CHECKOUT_NOT_CLEAN
  source_tree="$(git rev-parse 'HEAD^{tree}')"
  [[ "$source_tree" =~ ^[0-9a-f]{40}$ ]] || fail CHECKOUT_TREE_INVALID
  verify_code55_run "$qa_run" "$qa_workflow_path" '["push"]' \
    '["node-backend","full-android","native-pdf","complete-qa"]' full
  verify_code55_run "$apk_run" ".github/workflows/continuum-code55-owner-apk.yml" '["push","workflow_dispatch"]' \
    '["scoped-android","unsigned-arm64"]' apk
  download_code55_proof "$qa_run" "code55-github-node-evidence" node full
  download_code55_proof "$qa_run" "Code55-full-Android-source-QA" host full
  download_code55_proof "$qa_run" "Code55-native-PDF-emulator-QA" native full
  download_code55_proof "$apk_run" "$artifact" unsigned apk
  python3 - "$tmp" "$source_sha" "$source_tree" "$tools" <<'PY' || fail CODE55_RECEIPT_OR_APK_INVALID
from pathlib import Path
import hashlib, importlib.util, json, re, subprocess, sys, xml.etree.ElementTree as ET
root, sha, tree, tools = Path(sys.argv[1]), sys.argv[2], sys.argv[3], Path(sys.argv[4])
def require(ok):
    if not ok: raise ValueError("Code55 proof rejected")
def pairs(values):
    result = {}
    for key, value in values:
        require(key not in result)
        result[key] = value
    return result
def read_json(path):
    return json.loads(path.read_text(), object_pairs_hook=pairs, parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
def one(directory, name):
    found = list(directory.rglob(name))
    require(len(found) == 1 and found[0].is_file() and not found[0].is_symlink())
    return found[0]
def fields(path):
    result = {}
    for line in path.read_text().splitlines():
        key, separator, value = line.partition("=")
        require(separator and key not in result)
        result[key] = value
    return result
def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()
def source(directory):
    require((directory / "source-sha.txt").read_text().strip() == sha)
    require((directory / "source-tree.txt").read_text().strip() == tree)
def instrument(path):
    result = {}
    text = path.read_text()
    for key, value in re.findall(r"^INSTRUMENTATION_RESULT: ([^=\r\n]+)=(.*?)\r?$", text, re.M):
        require(key not in result)
        result[key] = value
    require(re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)\r?$", text, re.M) == ["-1"])
    require("INSTRUMENTATION_FAILED" not in text and result.get("status") == "ok")
    source(path.parent)
    return result
for path in (root / "node").rglob("report.json"):
    read_json(path)
pdf = instrument(one(root / "native", "native-pdf.txt"))
require(all(pdf.get(key) == "1" for key in ("provider_queries", "provider_opens", "page_count")))
require(int(pdf.get("rendered_ink_pixels", "0")) > 0)
filament_path = one(root / "native", "native-filament.txt")
filament = instrument(filament_path)
require(filament.get("sdk") == "35" and filament.get("context", "").startswith("SYNTHETIC FILAMENT TEST;"))
require(all(filament.get(key) == "2" for key in ("native_quads", "native_materials", "native_textures")))
require(filament.get("upload_completions") == "1")
require(all(int(filament.get(key, "0")) > 0 for key in ("rendered_frames", "presented_frames", "output_frames")))
require(all(filament.get(key) == "true" for key in ("content_cleared", "panels_released", "engine_released")))
gles = [line.strip() for line in (filament_path.parent / "surfaceflinger.txt").read_text().splitlines() if line.strip().startswith("GLES:")]
require(len(gles) == 1 and "swiftshader" in gles[0].lower())
host_source = fields(one(root / "host", "code55-source.txt"))
require(host_source == {"source_sha": sha, "source_tree": tree})
xml_files = list((root / "host").rglob("TEST-*.xml"))
require(xml_files and len({path.parent for path in xml_files}) == 1)
case_ids = set()
for path in xml_files:
    suite = ET.parse(path).getroot()
    require(suite.tag == "testsuite")
    require(not suite.findall(".//failure") and not suite.findall(".//error"))
    for case in suite.findall("testcase"):
        identity = (case.get("classname"), case.get("name"))
        require(all(identity) and identity not in case_ids)
        case_ids.add(identity)
summary = subprocess.run([sys.executable, "-B", "tools/code55-apk/junit-summary.py",
    str(xml_files[0].parent), "--strict-skips"], text=True, capture_output=True, check=True)
junit = json.loads(summary.stdout, object_pairs_hook=pairs)
require(junit["source_sha"] == sha and not junit["failed_cases"] and not junit["unexpected_skips"])
# Baseline 41306257 enumerated 5,321 cases.
require(junit["tests"] >= 5321 and junit["executed"] >= 5303)
require(junit["failures"] == 0 and junit["errors"] == 0)
(root / "code55-junit.json").write_text(json.dumps(junit))
apk = one(root / "unsigned", "app-release-unsigned.apk")
receipt = read_json(one(root / "unsigned", "RESULT.json"))
require(receipt["status"] == "PASS" and receipt["artifactStatus"] == "UNSIGNED_NOT_INSTALLABLE")
require(receipt["expectedSource"] == sha and receipt["expectedTree"] == tree and receipt["signing"] == "NOT_USED")
require(receipt["sourceBefore"] == receipt["sourceAfter"])
require(receipt["sourceBefore"]["commit"] == sha and receipt["sourceBefore"]["tree"] == tree and receipt["sourceBefore"]["status"] == "")
require(receipt["sourceBefore"]["original_avatar_git_blob"] == subprocess.check_output(
    ["git", "rev-parse", "HEAD:app/src/main/assets/avatar3d/continuum-avatar.glb"], text=True).strip())
require(receipt["validatorSha256"] == digest(Path("tools/code55-apk/validator.py")))
require(receipt["zipalign16KiB"] == "PASS" and receipt["unsignedDiagnostic"] == "EXPECTED_MISSING_SIGNATURE")
require(receipt["archive"]["zipCrc"] == "PASS" and receipt["archive"]["jarSignatureFiles"] == []
        and receipt["archive"]["apkSigningBlock"] is False and receipt["archive"]["assetFilesCompared"] > 0)
source_fields = fields(one(root / "unsigned", "CODE55-SOURCE.txt"))
require(source_fields == {"source": sha, "tree": tree, "package": "at.heinz.deviceai.continuumlocal",
    "versionCode": "55", "versionName": "0.3.54", "originalSigner": "NOT_USED", "Fold7": "NOT_RUN"})
require(apk.stat().st_size == receipt["bytes"] and digest(apk) == receipt["sha256"])
require(one(root / "unsigned", "SHA256SUMS.txt").read_text() == receipt["sha256"] + "  app-release-unsigned.apk\n")
spec = importlib.util.spec_from_file_location("code55_validator", "tools/code55-apk/validator.py")
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)
require(sorted(receipt["archive"]["nativeLibraries"]) == sorted(validator.EXPECTED_LIBRARIES))
badging = subprocess.run([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True, capture_output=True, check=True)
require(validator.check_badging(badging.stdout) == receipt["identity"])
subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "-v", "4", str(apk)], check=True, capture_output=True)
signature = subprocess.run([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)],
    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
validator.check_unsigned_diagnostic(signature.returncode, signature.stdout)
(root / "code55-unsigned-path.txt").write_text(str(apk) + "\n")
(root / "code55-apk-proof.json").write_text(json.dumps({"source": sha, "tree": tree, "sha256": receipt["sha256"], "bytes": receipt["bytes"]}))
print(json.dumps({"source": sha, "junit": junit, "native_pdf": "PASS", "native_filament": "PASS",
    "unsigned_sha256": receipt["sha256"], "Fold7": "NOT_RUN"}, sort_keys=True))
PY
  node --input-type=module - "$tmp/node" "$source_sha" "$branch" <<'NODE' || fail CODE55_NODE_PROOF_INVALID
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {assessNodeOutcome, GATES} from './scripts/continuum-reliability-qa.mjs';
const [directory, sha, branch] = process.argv.slice(2);
const require = (condition) => { if (!condition) throw Error('Node proof rejected'); };
function walk(dir) {
  return fs.readdirSync(dir, {withFileTypes: true}).flatMap(item => item.isDirectory()
    ? walk(path.join(dir, item.name)) : [path.join(dir, item.name)]);
}
for (const profile of ['critical', 'full-node']) {
  const matches = walk(directory).filter(file => path.basename(file) === 'report.json'
    && path.basename(path.dirname(file)) === (profile === 'critical' ? 'code55-critical' : 'code55-full'));
  require(matches.length === 1);
  const file = matches[0];
  // Python rejects duplicate JSON fields.
  const raw = fs.readFileSync(file, 'utf8');
  const report = JSON.parse(raw);
  require(report.schemaVersion === 1 && report.profile === profile && report.status === 'PASS'
    && report.sourceUnchanged === true && report.cancelled === false
    && report.source.commit === sha && report.source.branch === branch);
  const required = profile === 'critical' ? GATES.map(gate => gate.id) : ['full-node'];
  const actual = report.checks.filter(check => required.includes(check.id));
  require(actual.length === required.length && new Set(actual.map(check => check.id)).size === required.length);
  for (const check of actual) {
    require(check.status === 'PASS' && check.code === 0 && check.error === null && check.exitSignal === null
      && check.terminationConfirmed === true);
    const xml = fs.readFileSync(path.join(path.dirname(file), check.id + '.xml'), 'utf8');
    require(crypto.createHash('sha256').update(xml).digest('hex') === check.junitSha256);
    const assessed = assessNodeOutcome({ok: true, code: check.code, error: check.error,
      exitSignal: check.exitSignal, terminationConfirmed: check.terminationConfirmed}, xml, process.cwd());
    require(assessed.status === 'PASS');
    if (profile === 'full-node') require(assessed.tests.total >= 2498 && assessed.tests.executed >= 2461);
    for (const key of ['total', 'passed', 'failed', 'skipped', 'executed'])
      require(assessed.tests[key] === check.tests[key]);
  }
  console.log(profile + ' SOURCE_BOUND_PROOF_VALID');
}
NODE
  IFS= read -r unsigned < "$tmp/code55-unsigned-path.txt"
  # Reject attempt changes during download.
  for prefix in full apk; do
    local run_id="$qa_run"
    [[ "$prefix" == full ]] || run_id="$apk_run"
    gh api "repos/$repo/actions/runs/$run_id" > "$tmp/$prefix-run-after.json" || fail QA_RUN_UNREADABLE
    jq -e -s '.[0].id==.[1].id and .[0].head_sha==.[1].head_sha and .[0].run_attempt==.[1].run_attempt
      and .[1].status=="completed" and .[1].conclusion=="success"' \
      "$tmp/$prefix-run.json" "$tmp/$prefix-run-after.json" >/dev/null || fail QA_ATTEMPT_CHANGED
  done
  echo CHECKPOINT=CODE55_SOURCE_BOUND_NODE_JUNIT_NATIVE_UNSIGNED_PROOFS_VERIFIED
}


# 1. Environment-Schutz: ohne Pflicht-Reviewer (Besitzer) und enge Branch-Regel wird NICHT signiert.
env_json="$(gh api "repos/$repo/environments/$environment_name")" || fail ENVIRONMENT_UNREADABLE
jq -e --arg owner "$owner_login" '
  ([.protection_rules[]? | select(.type=="required_reviewers") | .reviewers[]? | select(.type=="User" and .reviewer.login==$owner)] | length) >= 1
' <<< "$env_json" >/dev/null || fail ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING
jq -e '.deployment_branch_policy.custom_branch_policies==true' <<< "$env_json" >/dev/null || fail ENVIRONMENT_BRANCH_POLICY_NOT_CUSTOM
policies="$(gh api "repos/$repo/environments/$environment_name/deployment-branch-policies")" || fail ENVIRONMENT_POLICIES_UNREADABLE
jq -e --arg b "$branch" '.branch_policies | length==1 and .[0].type=="branch" and .[0].name==$b' <<< "$policies" >/dev/null || fail ENVIRONMENT_BRANCH_POLICY_NOT_EXACT
echo CHECKPOINT=ENVIRONMENT_OWNER_REVIEWER_AND_EXACT_BRANCH_POLICY_VERIFIED

# 2. Source-bound successful QA.
if [[ "$code" == 55 ]]; then
  verify_code55_evidence
  lifecycle="PASS_REQUIRED_IN_FULL_ANDROID"
else
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
fi
echo CHECKPOINT=EXACT_SOURCE_QA_RUN_GREEN
echo "INFO=lifecycle_jdk21_job=$lifecycle"

# 3. Never publish over an existing/newer release or accept unreadable latest metadata.
if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then fail RELEASE_ALREADY_EXISTS; fi
latest_tag="$(gh api "repos/$repo/releases/latest" --jq .tag_name)" || fail LATEST_RELEASE_UNREADABLE
[[ "$latest_tag" =~ ^continuum-local-code([0-9]{2,4})-[0-9a-f]{40}$ ]] || fail LATEST_RELEASE_IDENTITY_INVALID
[[ "${BASH_REMATCH[1]}" -lt "$code" ]] || fail NOT_HIGHER_THAN_LATEST_RELEASE
if [[ "$code" == 51 ]]; then
  [[ "$latest_tag" == continuum-local-code50-473f79727e4b52509cc7d1af71e59b80ab9b6be5 ]] || fail CODE50_BASELINE_CHANGED
else
  baseline_tag="continuum-local-code51-6c23d295e2fca2d4e5950a866547733ec5ff22b0"
  baseline_digest="1928f58609eeb71a674f75cd66faaa6b4c4ad44f693d060223c664ac63cadfc1"
  [[ "$latest_tag" == "$baseline_tag" ]] || fail CODE51_BASELINE_CHANGED
  mkdir "$tmp/baseline"
  gh release download "$baseline_tag" --repo "$repo" --pattern "Continuum-Lokal-$baseline_digest.apk" --dir "$tmp/baseline"     >/dev/null || fail BASELINE_DOWNLOAD_FAILED
  baseline="$tmp/baseline/Continuum-Lokal-$baseline_digest.apk"
  [[ -f "$baseline" && ! -L "$baseline" && "$(stat -c %s "$baseline")" == 102197134 ]] || fail BASELINE_SIZE_MISMATCH
  [[ "$(sha256sum "$baseline" | cut -d' ' -f1)" == "$baseline_digest" ]] || fail BASELINE_SHA_MISMATCH
  baseline_signature="$("$tools/apksigner" verify --verbose --print-certs "$baseline")" || fail BASELINE_SIGNATURE_INVALID
  grep -Fxq "Signer #1 certificate SHA-256 digest: $fingerprint" <<< "$baseline_signature" || fail BASELINE_CERTIFICATE_MISMATCH
  grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<< "$baseline_signature" || fail BASELINE_V2_MISSING
  baseline_badging="$("$tools/aapt2" dump badging "$baseline")" || fail BASELINE_BADGING_FAILED
  grep -Fq "package: name='$pkg' versionCode='51' versionName='0.3.50'" <<< "$baseline_badging" || fail BASELINE_PACKAGE_MISMATCH
  echo CHECKPOINT=PINNED_CODE51_BASELINE_VERIFIED
fi
echo CHECKPOINT=RELEASE_PREFLIGHT_VALID
[[ "$mode" == sign ]] || { echo STATUS=PREFLIGHT_OK; exit 0; }

# 4. Ungesignierte QA-APK aus genau diesem Lauf prüfen.
[[ -n "${ANDROID_HOME:-}" ]] || fail ANDROID_HOME_MISSING
if [[ "$code" == 51 ]]; then
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
else
  [[ "$(sha256sum "$unsigned" | cut -d' ' -f1)" == "$(jq -r .sha256 "$tmp/code55-apk-proof.json")" ]] || fail UNSIGNED_APK_SHA_MISMATCH
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
if [[ "$code" == 55 ]]; then
  "$tools/zipalign" -P 16 -f 4 "$unsigned" "$tmp/aligned.apk" >/dev/null || fail APK_ZIPALIGN_FAILED
else
  "$tools/zipalign" -p -f 4 "$unsigned" "$tmp/aligned.apk" >/dev/null || fail APK_ZIPALIGN_FAILED
fi
if ! "$tools/apksigner" sign --ks "$tmp/original.p12" --ks-type PKCS12 --ks-key-alias continuum-local --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS --out "$tmp/signed.apk" "$tmp/aligned.apk" 2>/dev/null; then
  fail ORIGINAL_APK_SIGNING_FAILED
fi
unset SIGN_PASS
rm -f "$tmp/original.p12"
if [[ "$code" == 55 ]]; then
  "$tools/zipalign" -c -P 16 -v 4 "$tmp/signed.apk" >/dev/null || fail SIGNED_16K_ALIGNMENT_FAILED
fi
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
if [[ "$code" == 55 ]]; then
  printf 'apk_run_id=%s\nsource_tree=%s\nunsigned_apk_sha256=%s\nnative_pdf=PASS\nnative_filament=PASS_EMULATOR\n'     "$apk_run" "$source_tree" "$(jq -r .sha256 "$tmp/code55-apk-proof.json")" >> "$tmp/BUILD-INFO.txt"
  jq -r '"tests_total=\(.tests)\ntests_failed=\(.failures)\ntests_errors=\(.errors)\ntests_skipped=\(.skipped)\ntests_executed=\(.executed)"'     "$tmp/code55-junit.json" >> "$tmp/BUILD-INFO.txt"
fi
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
Update compatibility: same package and original certificate, higher versionCode. The pinned published predecessor was checked; real-device update/data comparison is NOT_RUN. Install only as an update, never uninstall or clear app data. Signing approval explicitly includes publishing this source as the latest GitHub release.
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
