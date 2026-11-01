#!/usr/bin/env bash
# Independent, read-only verification of the *already published* 0.3.34
# owner APK. Never signs, publishes, installs or reads private owner data.
set -Eeuo pipefail
umask 077
cd "$(git rev-parse --show-toplevel)"
expected_sha="6e68b9ce867b683a7c2350c1589269796a8166c3a9f1696fa4cb12da54cc7de6"
expected_signer="6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
expected_commit="97b5b9006f8c60f1a6fc4fb10981894330c55fe4"
base="https://continuum-owner-v23-release-preflight.onrender.com/continuum-local"
apk_url="$base/Continuum-Lokal-$expected_sha.apk"
out_dir="continuum-m8-render-qa"
mkdir -p "$out_dir"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fail() { echo "OWNER_APK_READBACK=FAIL: $*" >&2; exit 78; }

# The manifest is checked against pinned release evidence, not trusted to
# redirect a download to an arbitrary host or to approve a new release.
curl --fail --location --retry 2 --max-time 120 --silent --show-error \
    "$base/latest.json" -o "$work/latest.json" || fail "Manifest unavailable"
python3 - "$work/latest.json" "$apk_url" "$expected_sha" "$expected_signer" "$expected_commit" <<'PY' || fail "Manifest mismatch"
import json, sys
path, url, sha, signer, commit = sys.argv[1:]
with open(path, encoding="utf-8") as handle:
    manifest = json.load(handle)
expected = {
    "schema": 1,
    "channel": "OWNER_RELEASE",
    "packageName": "at.heinz.deviceai.continuumlocal",
    "versionCode": 35,
    "versionName": "0.3.34",
    "byteLength": 54521884,
    "apkUrl": url,
    "apkSha256": sha,
    "signerSha256": signer,
    "sourceCommit": commit,
}
for key, value in expected.items():
    if manifest.get(key) != value:
        raise SystemExit("Unexpected pinned release field: " + key)
if manifest.get("approved") is not True:
    raise SystemExit("Published manifest lacks approved flag")
PY

# Download exactly the pinned binary and compare its actual bytes and length.
curl --fail --location --retry 2 --max-time 420 --silent --show-error \
    "$apk_url" -o "$work/owner.apk" || fail "APK download failed"
[[ "$(wc -c < "$work/owner.apk" | tr -d '[:space:]')" == "54521884" ]] || fail "APK length mismatch"
printf '%s  %s\n' "$expected_sha" "$work/owner.apk" | sha256sum --check --status || fail "APK SHA-256 mismatch"

# Android build-tools are installed by the preceding isolated Render QA job.
sdk=".continuum-render-build/android-sdk/build-tools/35.0.0"
[[ -x "$sdk/apksigner" && -x "$sdk/aapt" ]] || fail "Android signature verifier unavailable"
export JAVA_HOME="$PWD/.continuum-render-build/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
"$sdk/apksigner" verify --verbose --print-certs "$work/owner.apk" > "$work/signature.txt" \
    || fail "Android APK signature verification failed"
"$sdk/aapt" dump badging "$work/owner.apk" > "$work/badging.txt" \
    || fail "Android APK metadata inspection failed"
python3 - "$work/signature.txt" "$work/badging.txt" "$expected_signer" <<'PY' || fail "Signer or package mismatch"
import re, sys
sig = open(sys.argv[1], encoding="utf-8").read()
badging = open(sys.argv[2], encoding="utf-8").read()
expected_signer = sys.argv[3]
digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", sig, re.M)
if len(digests) != 1 or digests[0].lower() != expected_signer:
    raise SystemExit("Unexpected signer count or certificate fingerprint")
match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
if not match or match.groups() != ("at.heinz.deviceai.continuumlocal", "35", "0.3.34"):
    raise SystemExit("Unexpected package/version in signed APK")
PY

printf 'status=SIGNED_PUBLISHED_APK_READBACK_PASS\nversion=0.3.34\nversion_code=35\nsource_commit=%s\napk_sha256=%s\nsigner_sha256=%s\nnew_release_approved=FALSE\n' \
    "$expected_commit" "$expected_sha" "$expected_signer" > "$out_dir/BINARY-READBACK.txt"
echo "OWNER_APK_READBACK=PASS VERSION=0.3.34 SHA=$expected_sha SIGNER=$expected_signer NO_NEW_RELEASE"
