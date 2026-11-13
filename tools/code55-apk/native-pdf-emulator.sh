#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo 'Usage: native-pdf-emulator.sh emulator-serial evidence-directory' >&2
  exit 2
fi
serial="$1"
evidence="$2"
[[ "$serial" =~ ^emulator-[0-9]+$ ]]
test -z "$(git status --porcelain --untracked-files=normal)"
: "${ANDROID_HOME:?Android SDK required}"
adb="$ANDROID_HOME/platform-tools/adb"
aapt="$ANDROID_HOME/build-tools/35.0.0/aapt"
[[ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
sdk="$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ "$sdk" =~ ^[0-9]+$ ]] && (( sdk >= 35 ))
app=app/build/outputs/apk/debug/app-debug.apk
test=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
test -s "$app" && test -s "$test"
package="$("$aapt" dump badging "$app" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
test_package="$("$aapt" dump badging "$test" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
[[ "$package" == at.heinz.deviceai.mentorpaired ]]
[[ "$test_package" == "$package.test" ]]
mkdir -p "$evidence"
git rev-parse HEAD > "$evidence/source-sha.txt"
git rev-parse 'HEAD^{tree}' > "$evidence/source-tree.txt"
sha256sum "$app" "$test" > "$evidence/apk-sha256.txt"
"$adb" -s "$serial" shell getprop ro.build.fingerprint > "$evidence/android-fingerprint.txt"
"$adb" -s "$serial" install -r "$app" > "$evidence/install-app.txt"
"$adb" -s "$serial" install -t -r "$test" > "$evidence/install-test.txt"
"$adb" -s "$serial" shell am instrument -w -r -e probe pdf \
  "$test_package/at.heinz.deviceai.ContinuumVisualInstrumentation" \
  | tee "$evidence/native-pdf.txt"
python3 - "$evidence/native-pdf.txt" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text()
assert re.search(r'^INSTRUMENTATION_RESULT: status=ok\r?$', text, re.M), text
assert re.search(r'^INSTRUMENTATION_CODE: -1\r?$', text, re.M), text
assert re.search(r'^INSTRUMENTATION_RESULT: provider_queries=1\r?$', text, re.M), text
assert re.search(r'^INSTRUMENTATION_RESULT: provider_opens=1\r?$', text, re.M), text
assert re.search(r'^INSTRUMENTATION_RESULT: page_count=1\r?$', text, re.M), text
ink = re.search(r'^INSTRUMENTATION_RESULT: rendered_ink_pixels=(\d+)\r?$', text, re.M)
assert ink and int(ink.group(1)) > 0, text
assert 'INSTRUMENTATION_FAILED' not in text, text
print('NATIVE_PDF_EMULATOR_PASS')
PY
