#!/bin/bash
# Run only from the accepted combined checkout, on a fresh output directory.
set -euo pipefail
qa_expected=${1:?exact expected source SHA}
qa_out=${2:?absolute fresh evidence directory}
[[ "$qa_out" == /* && ! -e "$qa_out" ]] || exit 68
[[ "$(git rev-parse HEAD)" == "$qa_expected" ]] || exit 69
git diff --quiet HEAD -- app scripts .github tools backend || exit 69
source /workspace/continuum-build-env/env.sh
mkdir -p "$qa_out"
export CONTINUUM_CRE1_PARITY_DIR="$qa_out/cre1-parity"
export CONTINUUM_CONTRASTIVE_PARITY_DIR="$qa_out/contrastive-parity"
node .github/continuum/cre-parity.mjs prepare "$CONTINUUM_CRE1_PARITY_DIR" > "$qa_out/parity-prepare.txt" 2>&1
node .github/continuum/contrastive-parity.mjs prepare "$CONTINUUM_CONTRASTIVE_PARITY_DIR" >> "$qa_out/parity-prepare.txt" 2>&1
{
 date -u +%FT%TZ
 git rev-parse HEAD
 git rev-parse HEAD^{tree}
 git status --porcelain
 java -version
 /usr/bin/java -version
 node --version
 gradle --version
 sha256sum /workspace/continuum-build-env/test-jvm21.init.gradle .github/continuum/cre-parity.mjs .github/continuum/contrastive-parity.mjs
} > "$qa_out/environment-source.txt" 2>&1
set +e
gradle --no-daemon --max-workers=2 --console=plain -I /workspace/continuum-build-env/test-jvm21.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir="$qa_out/ui" -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin > "$qa_out/gradle-full.log" 2>&1
qa_gradle=$?
printf 'exit_code=%s\n' "$qa_gradle" > "$qa_out/gradle-full.exit"
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR" > "$qa_out/parity-verify.txt" 2>&1
qa_cre=$?
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR" >> "$qa_out/parity-verify.txt" 2>&1
qa_swb=$?
printf 'cre1_exit=%s\ncontrastive_exit=%s\n' "$qa_cre" "$qa_swb" > "$qa_out/parity-verify.exit"
[[ "$qa_gradle" == 0 && "$qa_cre" == 0 && "$qa_swb" == 0 ]]
