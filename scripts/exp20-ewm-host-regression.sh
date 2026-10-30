#!/usr/bin/env bash
# EXP-20 M6-B4: host JUnit regression over every android-free main/test file that compiles without Android
# (NOT an Android/Gradle build, NOT a Fold7 measurement). The source lists were derived once by dropping every file
# that imports android/androidx/okhttp/com.google/kotlinx.coroutines or depends on such a file; they are frozen in
# docs/evidence/exp20-m6-20261031/ewm-fix/host-{main,test}-sources.txt.
#
#   KOTLINC=.../kotlinc JSON_JAR=.../json-20240303.jar JUNIT_JAR=... HAMCREST_JAR=... OUT=/tmp/ewm \
#     scripts/exp20-ewm-host-regression.sh [extra test FQCN]...
#
# JSON_JAR is the testImplementation org.json:json:20240303 from app/build.gradle.kts (Maven Central SHA-1
# 0ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a). Run on the research branch for the baseline and on
# claude/ewm-owner-subject-fix-kxrxy2 with Ewm1OwnerSupersessionTest appended for the patched result.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${KOTLINC:?set KOTLINC}" "${JSON_JAR:?set JSON_JAR}" "${JUNIT_JAR:?set JUNIT_JAR}" "${HAMCREST_JAR:?set HAMCREST_JAR}" "${OUT:?set OUT}"
LISTS="$ROOT/docs/evidence/exp20-m6-20261031/ewm-fix"
[[ "$(sha1sum "$JSON_JAR" | cut -c1-40)" == 0ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a ]] || { echo "JSON_JAR checksum mismatch" >&2; exit 3; }
mkdir -p "$OUT/shim/at/heinz/deviceai"
sed -n '1,25p' "$ROOT/app/src/main/java/at/heinz/deviceai/CommandInterpreter.kt" | grep -v '^import android\.' \
  > "$OUT/shim/at/heinz/deviceai/HostShimRisk.kt"
srcs=("$OUT/shim/at/heinz/deviceai/HostShimRisk.kt")
while IFS= read -r f; do srcs+=("$ROOT/app/src/main/java/$f"); done < "$LISTS/host-main-sources.txt"
while IFS= read -r f; do srcs+=("$ROOT/app/src/test/java/$f"); done < "$LISTS/host-test-sources.txt"
extra=()
for c in "$@"; do f="$ROOT/app/src/test/java/$(echo "${c%.*}" | tr . /)/${c##*.}.kt"; [[ -f "$f" ]] && srcs+=("$f"); extra+=("$c"); done
JAVA_OPTS="${JAVA_OPTS:--Xmx6g}" "$KOTLINC" "${srcs[@]}" -cp "$JSON_JAR:$JUNIT_JAR:$HAMCREST_JAR" -jvm-target 17 -d "$OUT/ewm-host.jar"
echo "BUILD_OK sources=${#srcs[@]} ewm=$(sha256sum "$ROOT/app/src/main/java/at/heinz/deviceai/intelligence/ewm/Ewm1EvidenceWorkspace.kt" | cut -c1-64)"
STDLIB="$(dirname "$(readlink -f "$KOTLINC")")/../lib/kotlin-stdlib.jar"
cd "$ROOT"
java -Xmx6g -cp "$OUT/ewm-host.jar:$STDLIB:$JSON_JAR:$JUNIT_JAR:$HAMCREST_JAR:app/src/test/resources:app/src/main/resources:app/src/main/assets" \
  org.junit.runner.JUnitCore $(cat "$LISTS/host-test-classes.txt") "${extra[@]}"
