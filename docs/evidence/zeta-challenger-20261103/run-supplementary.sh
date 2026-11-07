#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
repo=$(git rev-parse --show-toplevel)
evidence="$repo/docs/evidence/zeta-challenger-20261103"
cache=/workspace/zeta-challenger-run
cp_path=$(cat /workspace/continuum-build-env/baseline64-ui-classpath.txt)
kotlinc "$evidence/ZetaCardAudit.kt" -jvm-target 17 -cp "$cache/candidate-de86:$cp_path" -d "$cache/audit" > "$evidence/card-audit-compile.txt" 2>&1
for name in baseline candidate candidate-de86; do
 java -Xmx256m -Djava.awt.headless=true -cp "$cache/audit:$cache/$name:$cp_path" at.heinz.deviceai.ui.ZetaCardAudit > "$evidence/$name/card-audit.tsv" 2>&1
done
kotlinc "$evidence/ZetaInkAudit.kt" -jvm-target 17 -cp "$cache/candidate-de86:$cp_path" -d "$cache/ink-audit" > "$evidence/ink-audit-compile.txt" 2>&1
java -Xmx256m -Djava.awt.headless=true -cp "$cache/ink-audit:$cache/candidate-de86:$cp_path" at.heinz.deviceai.ui.ZetaInkAudit "$evidence/candidate-de86/ink-images" > "$evidence/candidate-de86/ink-audit.txt" 2>&1
