#!/usr/bin/env bash
set -euo pipefail
audit_root=/workspace/scratch/319f3e4563d9/agents-r2/gradient/experimental/weight-space-20261104/support-round2
source_root=/workspace/scratch/319f3e4563d9/weight16m-worktree/app/src/main/java/at/heinz/deviceai/learning/lm
tool_root=/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib
evidence_root="$audit_root/evidence-gradient"
java -version > "$evidence_root/java-version.log" 2>&1
java -cp "$tool_root/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -version > "$evidence_root/kotlin-version.log" 2>&1
sha256sum "$source_root"/*.kt > "$evidence_root/source-sha256.txt"
sha256sum "$tool_root/kotlin-compiler-embeddable-2.0.20.jar" "$tool_root/kotlin-stdlib-2.0.20.jar" "$tool_root/annotations-24.0.1.jar" > "$evidence_root/tool-sha256.txt"
java -cp "$tool_root/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 \
  -classpath "$tool_root/kotlin-stdlib-2.0.20.jar:$tool_root/annotations-24.0.1.jar" \
  -d "$evidence_root/audit.jar" \
  "$source_root/LmTensor.kt" "$source_root/LmModel.kt" "$source_root/LmBpeTokenizer.kt" "$audit_root/LmGradientAudit.kt" \
  > "$evidence_root/compile.log" 2>&1
java -cp "$evidence_root/audit.jar:$tool_root/kotlin-stdlib-2.0.20.jar" \
  at.heinz.deviceai.learning.lm.LmGradientAudit "$evidence_root" > "$evidence_root/run.log" 2>&1
sha256sum "$audit_root/LmGradientAudit.kt" "$evidence_root/audit.jar" "$evidence_root/replay.sh" "$evidence_root/run.log" "$evidence_root"/*.tsv > "$evidence_root/artifact-sha256.txt"
