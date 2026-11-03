#!/usr/bin/env bash
# QA-owned runner. Requires a Chief-assigned frozen checkout and full SHA.
set -uo pipefail
qa_runner=$(realpath "${BASH_SOURCE[0]}")
qa_repo=${1:?frozen checkout}; qa_sha=${2:?full source SHA}; qa_out=${3:?external evidence output}
source /workspace/momega1-build-env/env.sh
qa_init=${MOMEGA_QA_GRADLE_INIT:-/workspace/momega1-build-env/test-jvm21.init.gradle}
cd "$qa_repo" || exit 69
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
mkdir -p "$qa_out"
qa_out=$(realpath "$qa_out")
[[ "$qa_out" != "$PWD" && "$qa_out" != "$PWD/"* ]] || exit 69
sha256sum "$qa_init" /workspace/momega1-build-env/env.sh "$qa_runner" > "$qa_out/harness-inputs.sha256"
export CONTINUUM_CRE1_PARITY_DIR="$qa_out/cre1-parity"
export CONTINUUM_CONTRASTIVE_PARITY_DIR="$qa_out/contrastive-parity"
{ date -u +%FT%TZ; git rev-parse HEAD; git rev-parse HEAD^{tree}; git status --porcelain; java -version; /usr/bin/java -version; node --version; gradle --version; } > "$qa_out/source-environment.txt" 2>&1
node .github/continuum/cre-parity.mjs prepare "$CONTINUUM_CRE1_PARITY_DIR" > "$qa_out/parity-prepare.txt" 2>&1
qa_cre=$?
node .github/continuum/contrastive-parity.mjs prepare "$CONTINUUM_CONTRASTIVE_PARITY_DIR" >> "$qa_out/parity-prepare.txt" 2>&1
printf 'cre1=%s\ncontrastive=%s\n' "$qa_cre" "$?" > "$qa_out/parity-prepare.exit"
gradle --no-daemon --max-workers=2 --console=plain -I "$qa_init" -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir="$qa_out/ui" -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin > "$qa_out/gradle-full.txt" 2>&1
qa_gradle=$?
printf '%s\n' "$qa_gradle" > "$qa_out/gradle-full.exit"
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR" > "$qa_out/parity-verify.txt" 2>&1
qa_cre=$?
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR" >> "$qa_out/parity-verify.txt" 2>&1
qa_contrast=$?
printf 'cre1=%s\ncontrastive=%s\n' "$qa_cre" "$qa_contrast" > "$qa_out/parity-verify.exit"
[[ -d app/build/test-results/testDebugUnitTest ]] && tar -czf "$qa_out/junit-results.tar.gz" -C app/build/test-results/testDebugUnitTest .
[[ -f app/build/reports/lint-results-debug.xml ]] && cp app/build/reports/lint-results-debug.xml "$qa_out/"
{ git rev-parse HEAD; git status --porcelain; } > "$qa_out/source-after.txt"
[[ $(git rev-parse HEAD) == "$qa_sha" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
[[ "$qa_gradle" == 0 && "$qa_cre" == 0 && "$qa_contrast" == 0 ]] || exit 1
