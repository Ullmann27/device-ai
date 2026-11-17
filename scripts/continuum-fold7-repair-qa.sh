#!/usr/bin/env bash
# Local-only, fail-closed QA for the Fold7 repair branch; never signs/installs an APK.
# LiteRT 0.18 host lifecycle tests require JDK21; record their JDK17 skip honestly.
set -Eeuo pipefail
umask 077
fail(){ printf 'CONTINUUM_FOLD7_QA_BLOCKED: %s\n' "$*" >&2; exit 69; }
[[ $# -eq 0 || ( $# -eq 1 && "$1" == --offline ) ]] ||
  { echo 'Usage: bash scripts/continuum-fold7-repair-qa.sh [--offline]' >&2; exit 64; }
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$root"
[[ -f app/build.gradle.kts && -f settings.gradle.kts ]] ||
  fail 'Full Android checkout required'
for cmd in git javac python3 node; do command -v "$cmd" >/dev/null || fail "Missing $cmd"; done
sha="$(git rev-parse --verify HEAD 2>/dev/null)" || fail 'Missing commit'
[[ "$sha" =~ ^[a-f0-9]{40}$ && "${CONTINUUM_FOLD7_QA_SHA:-}" == "$sha" ]] ||
  fail 'Explicit exact-source CONTINUUM_FOLD7_QA_SHA required'
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] ||
  fail 'Source checkout is dirty'
for key in CONTINUUM_LOCAL_SIGNING_FILE CONTINUUM_LOCAL_SIGNING_PASSWORD \
  CONTINUUM_LOCAL_SIGNING_P12_BASE64 CONTINUUM_MENTOR_SIGNING_FILE \
  CONTINUUM_MENTOR_SIGNING_PASSWORD CONTINUUM_MENTOR_SIGNING_P12_BASE64; do
  [[ -z "${!key:-}" ]] || fail 'Owner signing credentials are forbidden'
done
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -s "$ANDROID_HOME/platforms/android-36/android.jar" ]] ||
  fail 'Android SDK36 not installed'
[[ "$(javac -version 2>&1)" == "javac 17."* ]] || fail 'JDK17 required'
if [[ -x "$root/gradlew" ]]; then
  gradle="$root/gradlew"
else
  gradle="$(command -v gradle || true)"
fi
[[ -n "$gradle" && -x "$gradle" ]] || fail 'Gradle 8.11.1 not available'
output="${CONTINUUM_FOLD7_QA_OUTPUT_DIR:-$HOME/.local/state/device-ai-continuum/fold7-repair-qa}"
mkdir -p -- "$output"
output="$(cd -- "$output" && pwd -P)"
[[ "$output" != "$root" && "$output" != "$root/"* ]] ||
  fail 'Evidence directory must be outside the repository'
date -u +%FT%TZ > "$output/start-$sha.txt"
args=(--no-daemon --max-workers=2 --console=plain --rerun-tasks
  -Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m"
  -Pkotlin.compiler.execution.strategy=in-process
  -PdeviceAiMentorPreview=true
  -PcontinuumLocalStandaloneQa=true
  -PcontinuumStandaloneVersionCode=39
  -PcontinuumUiWidgetTests=true)
[[ $# -eq 0 ]] || args+=(--offline)
if [[ -n "${CONTINUUM_AAPT2_PATH:-}" ]]; then
  [[ -x "$CONTINUUM_AAPT2_PATH" ]] || fail 'Invalid aapt2 path'
  args+=("-Pandroid.aapt2FromMavenOverride=$CONTINUUM_AAPT2_PATH")
fi
log="$output/android-$sha.log"
if ! "$gradle" "${args[@]}" :app:compileDebugKotlin \
  :app:testDebugUnitTest :app:lintDebug > "$log" 2>&1; then
  tail -n 80 "$log" >&2 || true
  fail "Android Kotlin/JUnit/lint failed (log: $log)"
fi
python3 - "$root/app/build/test-results/testDebugUnitTest" \
  "$output/start-$sha.txt" "$output/junit-$sha.json" <<'PY'
import json, pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
started = pathlib.Path(sys.argv[2]).stat().st_mtime
dest = pathlib.Path(sys.argv[3])
mandatory = {
    "at.heinz.deviceai.ShizukuBridgeLifecycleTest": 11,
    "at.heinz.deviceai.ui.ContinuumControlsTest": 1,
    "at.heinz.deviceai.cognitive.OwnerChatIntentRouterTest": 1,
    "at.heinz.deviceai.cognitive.HybridMemoryRetrieverTest": 1,
}
executed = {k:0 for k in mandatory}
skipped = {k:0 for k in mandatory}
count = 0
other_skips = 0
files = sorted(root.glob("TEST-*.xml"))
if not files:
    raise SystemExit("No JUnit report XML")
for path in files:
    if path.stat().st_mtime + 2 < started:
        raise SystemExit("Stale JUnit report: "+path.name)
    tree = ET.parse(path)
    if tree.getroot().tag != "testsuite":
        raise SystemExit("Unexpected JUnit XML root: "+path.name)
    for case in tree.iter("testcase"):
        count += 1
        if case.find("failure") is not None or case.find("error") is not None:
            raise SystemExit("JUnit failure in "+path.name)
        classname = case.attrib.get("classname", "")
        if classname in executed:
            executed[classname] += 1
            if case.find("skipped") is not None: skipped[classname] += 1
        elif case.find("skipped") is not None:
            other_skips += 1
for classname, minimum in mandatory.items():
    if executed[classname] < minimum or skipped[classname]:
        raise SystemExit("Missing or skipped mandatory suite: "+classname)
dest.write_text(json.dumps({"total_test_cases":count,
    "mandatory_executed":executed,"mandatory_skipped":skipped,
    "junit_reports":len(files),"other_skipped_cases":other_skips,
    "jdk21_litert_reload_tests":"NOT_VERIFIED_UNDER_JDK17"},
    indent=2,sort_keys=True)+"\n",encoding="utf-8")
print("CONTINUUM_FOLD7_REQUIRED_JUNIT_GREEN",count)
PY
node_log="$output/node-$sha.log"
if ! env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 \
  scripts/continuum-ui-routing.test.mjs \
  scripts/continuum-gemma-picker-lifecycle.test.mjs \
  scripts/continuum-typed-coding-bridge.test.mjs > "$node_log" 2>&1; then
  tail -n 70 "$node_log" >&2 || true
  fail "Focused Node tests failed (log: $node_log)"
fi
[[ "$(git rev-parse HEAD)" == "$sha" &&
   -z "$(git status --porcelain --untracked-files=normal)" ]] ||
   fail 'Git source mutated during QA'
printf 'sha=%s\njvm=PASS\nlint=PASS\nnode=PASS\njdk21_native_type_lifecycle=NOT_VERIFIED\nsigning=NOT_RUN\napk=NOT_BUILT\nfold7_device=NOT_RUN\n' "$sha" \
  > "$output/status-$sha.txt"
printf 'CONTINUUM_FOLD7_QA_HOST_GREEN=%s\nCONTINUUM_FOLD7_DEVICE_NOT_RUN=true\n' "$sha"
