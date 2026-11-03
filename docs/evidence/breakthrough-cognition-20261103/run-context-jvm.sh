#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../.."
deps=${CONTINUUM_JVM_DEPS:-/workspace/continuum-build-env/jvm-deps}
# Real Gradle-built dependencies at Chief 64ec06a7, never source/model stubs.
# QA confirmed their source identity before this lane used them. A fresh complete
# Gradle gate is still required for the combined candidate.
base_classes=${CONTINUUM_COGNITION_BASE_CLASSES:-/workspace/continuum-quality/app/build/tmp/kotlin-classes/debug}
scratch=$(mktemp -d "${TMPDIR:-/tmp}/breakthrough-cognition-jvm.XXXXXX")
trap 'rm -rf "$scratch"' EXIT
mkdir "$scratch/dependencies" "$scratch/classes"
cp -a "$base_classes/." "$scratch/dependencies/"
classpath=''
while IFS= read -r jar; do classpath+="$jar:"; done < <(find "$deps" -maxdepth 1 -name '*.jar' -type f | sort)
test -n "$classpath"
classpath+="$scratch/dependencies"
production=app/src/main/java/at/heinz/deviceai/cognitive/OwnerStatementTimeline.kt
if [[ ${1:-} == --baseline ]]; then
  git show 64ec06a73f50cae9ee49035c840710d87777d50f:"$production" > "$scratch/OwnerStatementTimeline.kt"
  production="$scratch/OwnerStatementTimeline.kt"
fi
names=(OwnerStatementTimeline OwnerStatementTimelineContextIntegration LocalConversationFollowUpIntegration
  LocalFollowUpTaskFrame LocalPromptFrame LocalContextRetention LocalConversationFollowUpPolicy
  LocalContextIntegrityProbe LocalConversationPromptFirewall)
sources=("$production")
tests=()
for name in "${names[@]}"; do
  sources+=("app/src/test/java/at/heinz/deviceai/cognitive/${name}Test.kt")
  tests+=("at.heinz.deviceai.cognitive.${name}Test")
done
printf 'source_commit=%s\nmode=%s\n' "$(git rev-parse HEAD)" "${1:-candidate}"
git status --short
printf 'real_gradle_dependency_tree_sha256='
(cd "$scratch/dependencies" && find . -type f -print0 | sort -z | xargs -0 sha256sum) | sha256sum
sha256sum "${sources[@]}"
java -version
java -Xmx512m -cp "$classpath" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath "$classpath" -jvm-target 17 \
  -Xfriend-paths="$scratch/dependencies" -d "$scratch/classes" "${sources[@]}"
java -Xmx256m -cp "$scratch/classes:$classpath" org.junit.runner.JUnitCore "${tests[@]}"
