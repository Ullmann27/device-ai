#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo 'Usage: native-hologram-emulator.sh emulator-serial evidence-directory' >&2
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
[[ "$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" == 35 ]]
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
collected=false
collect() {
  local probe_exit=$?
  if [[ "$collected" == true ]]; then return "$probe_exit"; fi
  collected=true
  for name in hologram-state.json hologram-screen.png hologram-texture.png hologram-surface-copy.png; do
    if ! timeout -k 2s 5s "$adb" -s "$serial" exec-out run-as "$package" \
        cat "cache/code59-hologram-diagnostic/$name" > "$evidence/$name" 2> "$evidence/$name-transfer.txt"; then
      rm -f "$evidence/$name"
    elif [[ "$name" == *.png && "$(od -An -tx1 -N8 "$evidence/$name" | tr -d ' \n')" != 89504e470d0a1a0a ]]; then
      mv "$evidence/$name" "$evidence/$name-transfer-stdout.txt"
    fi
  done
  timeout -k 2s 5s "$adb" -s "$serial" logcat -d -v threadtime -t 10000 \
    > "$evidence/logcat.txt" 2>&1 || true
  timeout -k 2s 5s "$adb" -s "$serial" shell dumpsys gfxinfo "$package" \
    > "$evidence/gfxinfo.txt" 2>&1 || true
  return "$probe_exit"
}
trap collect EXIT
timeout -k 2s 60s "$adb" -s "$serial" install -r "$app" > "$evidence/install-app.txt"
timeout -k 2s 60s "$adb" -s "$serial" install -t -r "$test" > "$evidence/install-test.txt"
"$adb" -s "$serial" shell am force-stop "$package"
"$adb" -s "$serial" shell pm clear "$package" | tee "$evidence/clear-empty-data.txt"
test "$(tr -d '\r\n' < "$evidence/clear-empty-data.txt")" == Success
for permission in CAMERA RECORD_AUDIO POST_NOTIFICATIONS; do
  "$adb" -s "$serial" shell pm revoke "$package" "android.permission.$permission"
done
"$adb" -s "$serial" logcat -b all -c
timeout -k 2s 240s "$adb" -s "$serial" shell am instrument -w -r -e probe hologram \
  "$test_package/at.heinz.deviceai.ContinuumVisualInstrumentation" \
  | tee "$evidence/native-hologram.txt"
collect
python3 - "$evidence" <<'PY'
from pathlib import Path
import hashlib
import json
import re
import struct
import sys

directory = Path(sys.argv[1])
raw = (directory / 'native-hologram.txt').read_text()

def value(name):
    found = re.findall(r'^INSTRUMENTATION_RESULT: ' + re.escape(name) + r'=(.*?)\r?$', raw, re.M)
    assert len(found) == 1, (name, raw)
    return found[0]

assert value('status') == 'ok', raw
assert value('outcome') == 'DIAGNOSTIC_CAPTURED', raw
assert value('report_path') == 'cache/code59-hologram-diagnostic/hologram-state.json', raw
assert re.search(r'^INSTRUMENTATION_CODE: -1\r?$', raw, re.M), raw
assert 'INSTRUMENTATION_FAILED' not in raw, raw
report_bytes = (directory / 'hologram-state.json').read_bytes()
assert hashlib.sha256(report_bytes).hexdigest() == value('report_sha256')
report = json.loads(report_bytes)
assert report['schema'] == 1 and report['scope'] == 'EXISTING_HOME_STAGE_DIAGNOSTIC_ONLY'
assert report['outcome'] == 'DIAGNOSTIC_CAPTURED'
assert report['package'] == 'at.heinz.deviceai.mentorpaired'
assert report['sdk'] == 35 and report['hardware'] in ('ranchu', 'goldfish')
assert report['pid'] == int(value('pid')) and report['pid'] > 0
assert report['loading_observation_complete'] is True
samples = report['samples']
assert 2 <= len(samples) <= 100
assert all(s['hologram']['class'] == 'at.heinz.deviceai.ContinuumHologramView' for s in samples)
assert len({s['hologram']['identity'] for s in samples}) == 1
assert all(s['main_thread'] == 'main' for s in samples)
assert all(a['elapsed_nanos'] <= b['elapsed_nanos'] for a, b in zip(samples, samples[1:]))
first = samples[report['comparison_first_sample']]
second = samples[report['comparison_second_sample']]
assert second['elapsed_nanos'] - first['elapsed_nanos'] >= 1_600_000_000
assert report['finished_elapsed_nanos'] >= samples[-1]['elapsed_nanos'] >= report['started_elapsed_nanos']

def image(metadata, expected_name):
    assert metadata['file'] == expected_name
    payload = (directory / expected_name).read_bytes()
    assert len(payload) == metadata['bytes'] and len(payload) > 24
    assert hashlib.sha256(payload).hexdigest() == metadata['sha256']
    assert payload[:8] == b'\x89PNG\r\n\x1a\n' and payload[12:16] == b'IHDR'
    dimensions = struct.unpack('>II', payload[16:24])
    assert dimensions == (metadata['width'], metadata['height'])
    assert dimensions[0] > 0 and dimensions[1] > 0

image(report['screen'], 'hologram-screen.png')
image(report['texture'], 'hologram-texture.png')
copy = report['pixel_copy']
if copy.get('status') == 0:
    image(copy['image'], 'hologram-surface-copy.png')
else:
    assert 'image' not in copy
assert all(a['button'] in ('SCHLIESSEN', 'NEIN') for a in report['actions'])
print('HOLOGRAM_DIAGNOSTIC_CAPTURED; no visible-avatar or UI-flow pass')
PY
