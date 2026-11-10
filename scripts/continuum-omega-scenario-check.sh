#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?Output directory required}
: "${CONTINUUM_APP_CLASSES:?Compiled Android Kotlin classes required}"
: "${CONTINUUM_JUNIT_JARS:?JUnit and Hamcrest classpath required}"
compiler=${KOTLINC:-kotlinc}
mkdir -p "$out"
out=$(cd "$out" && pwd)
cd "$root"
sources=(
  app/src/main/java/at/heinz/deviceai/AgentToolLayer.kt
  app/src/main/java/at/heinz/deviceai/ToolExecutionReceiptStore.kt
  app/src/main/java/at/heinz/deviceai/ToolRegistry.kt
  app/src/main/java/at/heinz/deviceai/cognitive/BoundedAgent.kt
  app/src/main/java/at/heinz/deviceai/cognitive/AgentTaskEvidence.kt
  app/src/main/java/at/heinz/deviceai/cognitive/ReadOnlyToolPlan.kt
  app/src/main/java/at/heinz/deviceai/cognitive/ApprovedWebToolGate.kt
  app/src/main/java/at/heinz/deviceai/cognitive/InternetConsentPolicy.kt
  app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationEvent.kt
  app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationTriggers.kt
  app/src/test/java/at/heinz/deviceai/cognitive/OmegaDownstreamScenarioTest.kt
)
tests=(at.heinz.deviceai.cognitive.OmegaDownstreamScenarioTest)
if [[ ${CONTINUUM_OMEGA_REGRESSIONS:-0} == 1 ]]; then
  sources+=(
    app/src/test/java/at/heinz/deviceai/ToolDispatchObserverTest.kt
    app/src/test/java/at/heinz/deviceai/cognitive/AgentTaskEvidenceTest.kt
    app/src/test/java/at/heinz/deviceai/cognitive/BoundedAgentOutcomeEvidenceTest.kt
    app/src/test/java/at/heinz/deviceai/cockpit/CockpitAnimationTriggersTest.kt
  )
  tests+=(at.heinz.deviceai.ToolDispatchObserverTest
    at.heinz.deviceai.cognitive.AgentTaskEvidenceTest
    at.heinz.deviceai.cognitive.BoundedAgentOutcomeEvidenceTest
    at.heinz.deviceai.cockpit.CockpitAnimationTriggersTest)
fi
sha256sum "${sources[@]}" > "$out/source-sha256.txt"
git rev-parse HEAD > "$out/source-commit.txt"
python3 - "$CONTINUUM_APP_CLASSES" "$out" <<'PY'
import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2])
rows=[(str(p.relative_to(root)),hashlib.sha256(p.read_bytes()).hexdigest())
      for p in sorted(root.rglob('*.class'))]
canonical=''.join(f'{name}\0{sha}\n' for name,sha in rows)
(out/'dependency-class-hashes.json').write_text(json.dumps(rows,indent=2)+'\n')
(out/'dependency-manifest.json').write_text(json.dumps({
 'kind':'precompiled app dependencies; critical sources recompiled',
 'class_count':len(rows),'manifest_sha256':hashlib.sha256(canonical.encode()).hexdigest(),
 'full_android_build':'NOT_RUN','model_inference':'NOT_RUN','Fold7':'NOT_RUN'},indent=2)+'\n')
PY
"$compiler" -version > "$out/toolchain.txt" 2>&1
java -version >> "$out/toolchain.txt" 2>&1
"$compiler" "${sources[@]}" -cp "$CONTINUUM_APP_CLASSES:$CONTINUUM_JUNIT_JARS" \
  -Xfriend-paths="$CONTINUUM_APP_CLASSES" -jvm-target 17 -nowarn -include-runtime \
  -d "$out/scenario.jar" > "$out/compile.log" 2>&1
java -Domega.scenario.output="$out/evidence" \
  -cp "$out/scenario.jar:$CONTINUUM_APP_CLASSES:$CONTINUUM_JUNIT_JARS" \
  org.junit.runner.JUnitCore "${tests[@]}" \
  > "$out/result.log" 2>&1 || { cat "$out/result.log"; exit 1; }
cat "$out/result.log"
