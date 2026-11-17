#!/usr/bin/env bash
# Kotlin unit tests of app/src/test WITHOUT the Android SDK or Gradle (when dl.google.com is unreachable).
#
# Builds on android-kotlin-compile-check.sh (same jars, same generated R/BuildConfig/AIDL stubs). The main source set
# is compiled with the compile-only LiteRT-LM stand-in in scripts/android-jvm-stubs (never shipped), app/src/test is
# compiled as a friend module (internal declarations visible, as in Gradle), and every *Test class runs with JUnit 4
# plus the *Checks programs, from app/ with main and test resources on the classpath, as Gradle runs unit tests.
# NOT covered: Robolectric UI tests (src/uiTest), Android lint, the Gradle/AGP build, an APK and any device behaviour.
# Report KOTLIN_UNIT_TESTS_CHECKED, never APK_BUILT or DEVICE_VERIFIED.
#
# Usage: CONTINUUM_ANDROID_DEPS=/path/to/jars CONTINUUM_JUNIT_JARS=/path/junit-4.13.2.jar:/path/hamcrest-core-1.3.jar \
#        [CONTINUUM_KOTLIN_STDLIB=/path/kotlin-stdlib.jar] scripts/android-kotlin-unit-check.sh [out-dir]
set -euo pipefail
deps=${CONTINUUM_ANDROID_DEPS:?set CONTINUUM_ANDROID_DEPS to the jars of android-kotlin-compile-check.sh}
junit=${CONTINUUM_JUNIT_JARS:?set CONTINUUM_JUNIT_JARS to junit-4.13.2.jar:hamcrest-core-1.3.jar}
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:-$(mktemp -d)}
for tool in kotlinc javac java; do command -v "$tool" >/dev/null || { echo "$tool not on PATH" >&2; exit 2; }; done
stdlib=${CONTINUUM_KOTLIN_STDLIB:-$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib/kotlin-stdlib.jar}
[ -s "$stdlib" ] || { echo "kotlin-stdlib.jar not found; set CONTINUUM_KOTLIN_STDLIB" >&2; exit 2; }
mkdir -p "$out"

"$root/scripts/android-kotlin-compile-check.sh" "$out/compile-check" >"$out/compile-check.txt"   # generated stubs
cp="$deps/android-all-15.jar"
for jar in okhttp.jar okio.jar shizuku-api.jar shizuku-aidl.jar shizuku-shared.jar shizuku-provider.jar; do cp="$cp:$deps/$jar"; done
mkdir -p "$out/stubclasses" "$out/main" "$out/test"
mapfile -t stubs < <(find "$out/compile-check/stubs" -name '*.java' | sort)
javac -nowarn -d "$out/stubclasses" -cp "$cp" "${stubs[@]}"
mapfile -t sources < <(find "$root/app/src/main/java" -name '*.kt' | sort)
kotlinc "${sources[@]}" "$root/scripts/android-jvm-stubs/LiteRtLmCompileStub.kt" -module-name app -cp "$cp:$out/stubclasses" \
  -jvm-target 17 -nowarn -d "$out/main" -Xnullability-annotations=@android.annotation:warn \
  -XXLanguage:+BreakContinueInInlineLambdas >"$out/kotlinc-main.log" 2>&1 \
  || { echo "KOTLIN_UNIT_CHECK_FAILED: main source set does not compile; see $out/kotlinc-main.log"; exit 1; }
mapfile -t tests < <(find "$root/app/src/test/java" -name '*.kt' | sort)
kotlinc "${tests[@]}" -module-name app -Xfriend-paths="$out/main" -cp "$out/main:$out/stubclasses:$cp:$junit" \
  -jvm-target 17 -nowarn -d "$out/test" >"$out/kotlinc-test.log" 2>&1 \
  || { echo "KOTLIN_UNIT_CHECK_FAILED: test source set does not compile; see $out/kotlinc-test.log"; exit 1; }
mapfile -t classes < <(cd "$out/test" && find . -name '*.class' ! -name '*$*' | sed 's#^\./##; s#\.class$##; s#/#.#g' | grep -E 'Tests?$' | sort)
mapfile -t checks < <(cd "$out/test" && find . -name '*Checks.class' | sed 's#^\./##; s#\.class$##; s#/#.#g' | sort)
run_cp="$out/test:$out/main:$out/stubclasses:$stdlib:$junit:$cp:src/test/resources:src/main/resources"
cd "$root/app"
status=0
java -Xmx2g -cp "$run_cp" org.junit.runner.JUnitCore "${classes[@]}" >"$out/junit.txt" 2>&1 || status=1
for check in "${checks[@]}"; do java -cp "$run_cp" "$check" >>"$out/checks.txt" 2>&1 || { echo "check failed: $check" >>"$out/checks.txt"; status=1; }; done
summary=$(grep -E '^(OK \(|Tests run:)' "$out/junit.txt" | tail -1)
echo "classes=${#classes[@]} checks=${#checks[@]} junit: $summary"
if [ "$status" -ne 0 ]; then
  grep -E '^[0-9]+\) ' "$out/junit.txt" | head -20 || true
  grep 'check failed' "$out/checks.txt" || true
  echo "KOTLIN_UNIT_CHECK_FAILED: see $out/junit.txt and $out/checks.txt"; exit 1
fi
echo "KOTLIN_UNIT_TESTS_CHECKED: all JUnit classes and Checks programs pass (outside Gradle; not lint, not an APK, not a device)"
