#!/bin/bash
set -uo pipefail
source /workspace/continuum-build-env/env.sh
qa_out=/workspace/continuum-quality-runs/combined
export CONTINUUM_CRE1_PARITY_DIR="$qa_out/cre1-parity"
export CONTINUUM_CONTRASTIVE_PARITY_DIR="$qa_out/contrastive-parity"
{
  date -u +%FT%TZ
  git rev-parse HEAD
  git rev-parse HEAD^{tree}
  git status --porcelain
  java -version
  /usr/bin/java -version
  node --version
  gradle --version
  sha256sum /workspace/continuum-build-env/test-jvm21.init.gradle
  sha256sum -c "$qa_out/parity-generators.sha256"
} > "$qa_out/environment-source.txt" 2>&1
gradle --no-daemon --max-workers=2 --console=plain -I /workspace/continuum-build-env/test-jvm21.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir="$qa_out/ui" -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin > "$qa_out/gradle-full.log" 2>&1
qa_gradle=$?
printf 'exit_code=%s\n' "$qa_gradle" > "$qa_out/gradle-full.exit"
node .github/continuum/cre-parity.mjs verify "$CONTINUUM_CRE1_PARITY_DIR" > "$qa_out/parity-verify.log" 2>&1
qa_cre=$?
node .github/continuum/contrastive-parity.mjs verify "$CONTINUUM_CONTRASTIVE_PARITY_DIR" >> "$qa_out/parity-verify.log" 2>&1
printf 'cre1_exit=%s\ncontrastive_exit=%s\n' "$qa_cre" "$?" > "$qa_out/parity-verify.exit"
