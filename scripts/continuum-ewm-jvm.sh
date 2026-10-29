#!/usr/bin/env bash
# EWM1/EWM2 research (Continuum brain: discourse memory, source router, claim check) without Gradle or the Android SDK.
# Open for every AI and every human: plain kotlinc + JUnit 4, no hidden state, every input is a file in docs/research/ewm1,
# nothing here locks, seals or hides code. Reports what ran on the JVM only - never APK_BUILT or DEVICE_VERIFIED.
#
# Prerequisite: one run of scripts/android-kotlin-unit-check.sh <harness-out> with the same environment
# (CONTINUUM_ANDROID_DEPS, CONTINUUM_JUNIT_JARS, optional CONTINUUM_KOTLIN_STDLIB); it compiles app/src/main into
# <harness-out>/main, app/src/test into <harness-out>/test and the generated stubs into <harness-out>/stubclasses.
#
# Usage (from anywhere; paths in arguments are relative to the repository root):
#   scripts/continuum-ewm-jvm.sh <harness-out> build             compile intelligence/ewm1 from the working tree
#   scripts/continuum-ewm-jvm.sh <harness-out> unit              Ewm1WorkspaceTest, Ewm1SourceIndexTest, Ewm1BrainTest
#   scripts/continuum-ewm-jvm.sh <harness-out> run <Main> [args] e.g. run Ewm2Experiment DEV docs/research/ewm1/ewm2-dev5-20261029.json
#   scripts/continuum-ewm-jvm.sh <harness-out> dev               every DEV number (DEV4, TEST3-as-dev, DEV5, DEV5b, router diff)
#   scripts/continuum-ewm-jvm.sh <harness-out> mutants           mutation check from docs/research/ewm1/ewm2-mutants.tsv
#   scripts/continuum-ewm-jvm.sh <harness-out> h7 <test5-report.json> <e1-output.json>   H7 decision rule (EWM2-PREREG)
# EWM_AT=<commit> builds and runs the intelligence/ewm1 sources of that commit instead (git archive), e.g. the frozen
# EWM1 baseline: EWM_AT=0d2b021b … build && EWM_AT=0d2b021b … run Ewm1Experiment DEV <corpus> > e1-output.json
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
hout=${1:?harness output directory of scripts/android-kotlin-unit-check.sh}; cmd=${2:?build|unit|run|dev|mutants|h7}; shift 2
hout=$(cd "$hout" && pwd -P)
[[ "$hout" != "$root" && "$hout" != "$root/"* ]] || { echo "harness output must lie outside the repository" >&2; exit 2; }
for d in main test stubclasses; do [ -d "$hout/$d" ] || { echo "missing $hout/$d - run scripts/android-kotlin-unit-check.sh first" >&2; exit 2; }; done
deps=${CONTINUUM_ANDROID_DEPS:?set CONTINUUM_ANDROID_DEPS as for android-kotlin-unit-check.sh}
junit=${CONTINUUM_JUNIT_JARS:?set CONTINUUM_JUNIT_JARS as for android-kotlin-unit-check.sh}
stdlib=${CONTINUUM_KOTLIN_STDLIB:-$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib/kotlin-stdlib.jar}
cp="$deps/android-all-15.jar"
for jar in okhttp.jar okio.jar shizuku-api.jar shizuku-aidl.jar shizuku-shared.jar shizuku-provider.jar; do cp="$cp:$deps/$jar"; done
at=${EWM_AT:-}
classes="$hout/ewm${at:+-$at}"
pkg=at.heinz.deviceai.intelligence.ewm1
rel=app/src/test/java/at/heinz/deviceai/intelligence/ewm1
cd "$root"

build() {
    local src="$root/$rel"
    if [ -n "$at" ]; then
        src="$hout/ewm-src-$at"; rm -rf "$src"; mkdir -p "$src"
        git archive "$at" "$rel" | tar -x -C "$src"; src="$src/$rel"
    fi
    rm -rf "$classes"; mkdir -p "$classes"
    kotlinc "$src"/*.kt -module-name app -Xfriend-paths="$hout/main" -cp "$hout/main:$hout/test:$hout/stubclasses:$cp:$junit" \
        -jvm-target 17 -nowarn -d "$classes" >"$classes.log" 2>&1 || true
    if grep -q "error:" "$classes.log" || [ ! -f "$classes/${pkg//.//}/Ewm1Experiment.class" ]; then
        grep "error:" "$classes.log" | head -20; echo "EWM_BUILD_FAILED: see $classes.log"; return 1
    fi
    echo "EWM_BUILT ${at:-working-tree} -> $classes"
}

java_run() {   # fresh EWM classes first, so they shadow the copies the harness compiled earlier
    env -u JAVA_TOOL_OPTIONS java -Dfile.encoding=UTF-8 \
        -cp "$classes:$hout/test:$hout/main:$hout/stubclasses:$stdlib:$junit:$cp:app/src/test/resources:app/src/main/assets" "$@"
}

main_class() { [[ "$1" == *.* ]] && echo "$1" || echo "$pkg.$1"; }

unit() { java_run org.junit.runner.JUnitCore $pkg.Ewm1WorkspaceTest $pkg.Ewm1SourceIndexTest $pkg.Ewm1BrainTest; }

case "$cmd" in
    build) build ;;
    unit) unit ;;
    run) main=$(main_class "${1:?main class}"); shift; java_run "$main" "$@" ;;
    dev)
        for c in docs/research/ewm1/ewm1-dev4-20261029.json docs/research/ctx3-independent-test3-20261024.json; do
            echo "== Ewm1Experiment DEV $c"; java_run $pkg.Ewm1Experiment DEV "$c" | grep '^{"B0' || true
        done
        for c in docs/research/ewm1/ewm2-dev5-20261029.json docs/research/ewm1/ewm2-dev5b-20261029.json; do
            echo "== Ewm2Experiment DEV $c"; java_run $pkg.Ewm2Experiment DEV "$c" | grep '^{' || true
        done
        for c in docs/research/ewm1/ewm1-dev4-20261029.json docs/research/ctx3-independent-test3-20261024.json; do
            echo "== router diff (must be 0 lines) $c"; EWM2_DIFF=1 java_run $pkg.Ewm2Experiment DEV "$c" | grep '^DIFF' || echo "0 DIFF"
        done ;;
    mutants)
        [ -z "$at" ] || { echo "mutants run on the working tree only" >&2; exit 2; }
        git diff --quiet -- "$rel" || { echo "commit or stash your changes in $rel first (mutants restore files from git)" >&2; exit 2; }
        trap 'git checkout -- "$rel"' EXIT
        echo "== baseline"; build >/dev/null; unit | grep -E '^(OK|Tests run)'
        killed=0; total=0
        while IFS=$'\t' read -r id file old new; do
            [[ -z "$id" || "$id" == \#* ]] && continue
            total=$((total + 1))
            python3 -I - "$rel/$file" "$old" "$new" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding='utf-8').read()
assert s.count(old) == 1, f"mutation anchor must occur exactly once in {p}: {old!r}"
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
            # stdin of the loop is the mutation list: compiler and JVM must not read it
            if build </dev/null >/dev/null && unit </dev/null >"$classes.unit.txt" 2>&1 && grep -q '^OK' "$classes.unit.txt"; then
                echo "SURVIVED $id"; else echo "KILLED   $id"; killed=$((killed + 1)); fi
            git checkout -- "$rel"
        done < docs/research/ewm1/ewm2-mutants.tsv
        build >/dev/null
        echo "MUTANTS_KILLED $killed/$total" ;;
    h7)
        python3 -I - "${1:?TEST5 report json}" "${2:?E1 output json (Ewm1Experiment DEV line)}" <<'PY'
import json, sys
k1 = json.load(open(sys.argv[1]))['systems']['K1']
e1_raw = json.load(open(sys.argv[2]))
e1 = e1_raw['EWM'] if 'EWM' in e1_raw else e1_raw['E1']['EWM']
ERR = {'ABBREVIATION', 'ABBREVIATION_UNKNOWN', 'PLACE_UNKNOWN', 'ATTRIBUTE_NOUN', 'BARE_NUMBER'}
OTHER = {'MEMORY', 'MEMORY_UNKNOWN', 'NEITHER'}
def count(c, kinds, outcomes=None):
    return sum(v for k, v in c.items() if k.startswith('kind:') and k.split(':')[1] in kinds
               and (outcomes is None or k.split(':')[2] in outcomes))
n = count(e1, ERR); solved_k1 = count(k1, ERR, {'CORRECT', 'ABSTAINED'}); solved_e1 = count(e1, ERR, {'CORRECT', 'ABSTAINED'})
wrong_k1 = count(k1, OTHER, {'WRONG'}); wrong_e1 = count(e1, OTHER, {'WRONG'})
print(json.dumps({'errorClassTurns': n, 'solvedK1': solved_k1, 'solvedE1': solved_e1, 'required': solved_e1 + n / 4,
                  'wrongOtherK1': wrong_k1, 'wrongOtherE1': wrong_e1,
                  'H7': 'PASSED' if solved_k1 >= solved_e1 + n / 4 and wrong_k1 <= wrong_e1 else 'FAILED'}))
PY
        ;;
    *) echo "unknown command $cmd" >&2; exit 2 ;;
esac
