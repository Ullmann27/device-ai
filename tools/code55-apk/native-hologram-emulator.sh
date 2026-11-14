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
    elif [[ "$name" == *.json ]] && ! python3 -c 'import json,sys; json.load(open(sys.argv[1], encoding="utf-8"))' "$evidence/$name" 2>/dev/null; then
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
python3 -B tools/code55-apk/native_hologram_stack.py "$adb" "$serial" "$evidence" "$(git rev-parse HEAD)"
collect
python3 -B tools/code55-apk/native_hologram_evidence.py "$evidence" \
  "$(git rev-parse HEAD)" "$(git rev-parse 'HEAD^{tree}')" \
  | tee "$evidence/surface-evidence.json"
