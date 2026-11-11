#!/usr/bin/env bash
set -euo pipefail
voice_root=$(cd "$(dirname "$0")" && pwd)
voice_compiler=${1:?path to installed kotlinc}
voice_out=${2:?external output directory}
mkdir -p "$voice_out"
"$voice_compiler" "$voice_root/native/PersonalVoiceCorrectionBridge.kt" \
  "$voice_root/native/PersonalVoiceLearningStore.kt" "$voice_root/native/PersonalVoicePrivacyChecks.kt" \
  -jvm-target 17 -include-runtime -d "$voice_out/privacy-checks.jar"
java -Xmx256m -cp "$voice_out/privacy-checks.jar" at.heinz.deviceai.research.voice.PersonalVoicePrivacyChecks
