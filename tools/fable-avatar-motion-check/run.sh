#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CONTRACT=${CONTINUUM_AVATAR_CONTRACT_FILE:-"$ROOT/app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt"}
MAPPER=${CONTINUUM_AVATAR_MAPPER_FILE:-"$ROOT/app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt"}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
command -v kotlinc >/dev/null
command -v java >/dev/null
python3 - "$CONTRACT" "$WORK/AvatarGlbContract.kt" <<'PY'
import pathlib, re, sys
source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
match = re.search(r'val\s+TARGETS\s*=\s*(listOf\([^)]*\))', source)
if match is None:
    raise SystemExit("TARGETS is not a literal listOf declaration; use the real Gradle suite.")
# Only compile the trusted target-name declaration, not the Android/org.json GLB parser.
if not re.fullmatch(r'listOf\(\s*"[A-Za-z]+"(?:\s*,\s*"[A-Za-z]+")*\s*\)', match[1]):
    raise SystemExit("Unexpected target declaration; no arbitrary source extraction allowed.")
pathlib.Path(sys.argv[2]).write_text(
    "package at.heinz.deviceai.avatar3d\n"
    "// Test-only declaration fixture; no GLB validation is performed.\n"
    "object AvatarGlbContract { val TARGETS = " + match[1] + " }\n", encoding="utf-8")
PY
sources=("$ROOT/app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt"
         "$MAPPER"
         "$WORK/AvatarGlbContract.kt"
         "$ROOT/tools/fable-avatar-motion-check/Main.kt")
idle="$ROOT/app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt"
if [[ -f "$idle" ]]; then sources+=("$idle"); fi
printf '%s\n' 'HOST_ONLY: actual mapper/reducer; TARGETS-declaration fixture; no GLB/GPU/Android validation.'
kotlinc -version 2>&1
kotlinc "${sources[@]}" -jvm-target 17 -include-runtime -d "$WORK/checks.jar"
java -jar "$WORK/checks.jar"
