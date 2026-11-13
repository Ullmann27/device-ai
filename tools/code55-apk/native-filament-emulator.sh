#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo 'Usage: native-filament-emulator.sh emulator-serial evidence-directory' >&2
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
[[ "$sdk" =~ ^[0-9]+$ ]] && (( sdk == 35 ))
[[ "$("$adb" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" == x86_64 ]]
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
trap '"$adb" -s "$serial" logcat -d -v threadtime -s Filament AndroidRuntime libc DEBUG > "$evidence/native-filament-logcat.txt" 2>&1 || true' EXIT
"$adb" -s "$serial" shell dumpsys SurfaceFlinger > "$evidence/surfaceflinger.txt"
python3 - "$evidence/surfaceflinger.txt" <<'PY'
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text()
gles = [line.strip() for line in text.splitlines() if line.strip().startswith("GLES:")]
assert len(gles) == 1 and "swiftshader" in gles[0].lower(), "Software GLES renderer required: " + str(gles)
print(gles[0])
PY
"$adb" -s "$serial" install -r "$app" > "$evidence/install-app.txt"
"$adb" -s "$serial" install -t -r "$test" > "$evidence/install-test.txt"
timeout 240s "$adb" -s "$serial" shell am instrument -w -r -e probe filament \
  "$test_package/at.heinz.deviceai.ContinuumVisualInstrumentation" \
  | tee "$evidence/native-filament.txt"
python3 - "$evidence/native-filament.txt" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text()

def value(name):
    found = re.findall(r'^INSTRUMENTATION_RESULT: ' + re.escape(name) + r'=(.*?)\r?$', text, re.M)
    assert len(found) == 1, (name, text)
    return found[0]

assert value("status") == "ok", text
assert value("sdk") == "35", text
assert value("context").startswith("SYNTHETIC FILAMENT TEST;"), text
for name in ("native_quads", "native_materials", "native_textures"):
    assert value(name) == "2", (name, text)
assert value("upload_completions") == "1", text
for name in ("rendered_frames", "presented_frames", "output_frames"):
    assert int(value(name)) > 0, (name, text)
for name in ("content_cleared", "panels_released", "engine_released"):
    assert value(name) == "true", (name, text)
assert re.search(r'^INSTRUMENTATION_CODE: -1\r?$', text, re.M), text
assert "INSTRUMENTATION_FAILED" not in text, text
print("NATIVE_FILAMENT_EMULATOR_PASS")
PY
