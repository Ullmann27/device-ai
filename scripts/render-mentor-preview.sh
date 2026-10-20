#!/usr/bin/env bash
# Fixed-SHA delivery bridge: render builds exactly the reviewed PR42 Android tree.
# Never writes upstream, main or any signing key.
set -Eeuo pipefail
umask 077
expected_service="srv-dbaue53bc2fs73crkop0"
delivery_branch="astra/render-mentor-apk-builder-20261019"
source_branch="astra/continuum-experiment-charter-20261019"
source_sha="a2a7d65eae983cbbc56d22da74e7e76a6b713102"

if [[ "${RENDER_SERVICE_ID:-}" != "$expected_service" ]]; then
    printf '%s\n' "This immutable APK delivery is restricted to its existing Render service" >&2
    exit 69
fi
actual_branch="$(git branch --show-current)"
if [[ -n "$actual_branch" && "$actual_branch" != "$delivery_branch" ]]; then
    printf '%s\n' "Unexpected Render checkout branch: $actual_branch" >&2
    exit 69
fi
printf '[Continuum] Fetching immutable reviewed training-monitor source: %s\n' "$source_sha"
git fetch --no-tags origin "+refs/heads/$source_branch:refs/remotes/origin/$source_branch"
actual_source_sha="$(git rev-parse --verify "refs/remotes/origin/$source_branch^{commit}")"
if [[ "$actual_source_sha" != "$source_sha" ]]; then
    printf '%s\n' "Reviewed source SHA changed; fail closed and require new review" >&2
    exit 69
fi
git -c advice.detachedHead=false checkout --force --detach "$source_sha"
test "$(git rev-parse HEAD)" = "$source_sha"
grep -q 'expected_branch="astra/continuum-experiment-charter-20261019"' scripts/render-mentor-preview.sh
grep -q 'CONTINUUM_MENTOR_SIGNING_P12_BASE64' scripts/render-mentor-preview.sh
grep -q 'training-monitor-wiring.test.mjs' scripts/render-mentor-preview.sh
printf '[Continuum] Building signed preview from exact PR42 commit %s\n' "$source_sha"
exec bash scripts/render-mentor-preview.sh
