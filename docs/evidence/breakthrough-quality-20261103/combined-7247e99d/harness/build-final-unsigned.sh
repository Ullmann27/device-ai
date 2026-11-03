#!/bin/bash
set -euo pipefail
qa_sha=${1:?};qa_out=${2:?}
[[ "$(git rev-parse HEAD)" == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
source /workspace/continuum-build-env/env.sh
mkdir -p "$qa_out"
{ git rev-parse HEAD; git rev-parse HEAD^{tree}; date -u +%FT%TZ; } > "$qa_out/unsigned-build-source.txt"
set +e
gradle --no-daemon --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalRenderUnsigned=true -PcontinuumStandaloneVersionCode=40 -Pkotlin.compiler.execution.strategy=in-process :app:assembleRelease > "$qa_out/gradle-unsigned-apk.log" 2>&1
qa_result=$?
printf 'exit_code=%s\n' "$qa_result" > "$qa_out/gradle-unsigned-apk.exit"
[[ "$qa_result" == 0 ]] || exit "$qa_result"
qa_apk="$PWD/app/build/outputs/apk/release/app-release-unsigned.apk"
python3 /workspace/continuum-build-env/verify-final-apk.py "$qa_apk" "$qa_out/apk-checks" > "$qa_out/apk-verification.txt" 2>&1
qa_result=$?
printf 'exit_code=%s\n' "$qa_result" > "$qa_out/apk-verification.exit"
mkdir -p /workspace/continuum-artifacts
cp "$qa_apk" "/workspace/continuum-artifacts/breakthrough-${qa_sha:0:8}-code40-unsigned.apk"
sha256sum "/workspace/continuum-artifacts/breakthrough-${qa_sha:0:8}-code40-unsigned.apk" > "$qa_out/durable-apk.sha256"
exit "$qa_result"
