#!/usr/bin/env bash
set -euo pipefail
avatar_repo="${1:?Pass exact a980 repository}"
avatar_evidence="${2:?Pass pose evidence directory}"
avatar_output="${3:?Pass fresh output directory}"
test "$(git -C "$avatar_repo" rev-parse HEAD)" = a980a19ed3ab44323009ae27eb299d322ff11679
test -z "$(git -C "$avatar_repo" status --porcelain)"
test ! -e "$avatar_output"
blender -b -t 1 --python "$avatar_repo/tools/avatar-3d/reference-atlas/render-production-poses.py" -- \
  --asset "$avatar_repo/app/src/main/assets/avatar3d/continuum-avatar.glb" \
  --poses "$avatar_evidence/production-poses.json" --output "$avatar_output" \
  --expected-asset-sha256 c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a \
  --expected-poses-sha256 5d28d8d6759bf7c15ed6f0d3782f114aa2b071016a586228a217134d388f7ee3 \
  --frames all --width 512 --height 640 --samples 16
