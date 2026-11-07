#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../.."
deps=${CONTINUUM_JVM_DEPS:-/workspace/continuum-build-env/jvm-deps}
classes=$(mktemp -d "${TMPDIR:-/tmp}/omega-intelligence-jvm.XXXXXX")
trap 'rm -rf "$classes"' EXIT
classpath=''
while IFS= read -r jar; do classpath+="$jar:"; done < <(find "$deps" -maxdepth 1 -name '*.jar' -type f | sort)
test -n "$classpath"
names=(OwnerStatementTimeline)
if [[ ${1:-} == --context ]]; then
  names+=(LocalConversationFollowUpPolicy LocalContextIntegrityProbe LocalConversationPromptFirewall)
fi
sources=()
tests=()
for name in "${names[@]}"; do
  sources+=("app/src/main/java/at/heinz/deviceai/cognitive/$name.kt"
    "app/src/test/java/at/heinz/deviceai/cognitive/${name}Test.kt")
  tests+=("at.heinz.deviceai.cognitive.${name}Test")
done
printf 'source_commit=%s\n' "$(git rev-parse HEAD)"
printf 'source_status:\n'
git status --short
sha256sum "${sources[@]}"
java -version
java -Xmx512m -cp "$classpath" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath "$classpath" -jvm-target 17 \
  -d "$classes" "${sources[@]}"
java -Xmx256m -cp "$classes:$classpath" org.junit.runner.JUnitCore \
  "${tests[@]}"
