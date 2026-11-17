#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
evidence_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(git -C "$evidence_dir" rev-parse --show-toplevel)
revision=${1:?exact source revision required}
mode=${2:-all}
run_root=$(mktemp -d /workspace/continuum-build-env/gamma-challenger-b.XXXXXX)
trap 'rm -rf -- "$run_root"' EXIT
git -C "$repo_dir" rev-parse "$revision"
for filename in ContinuumLearningExperience.kt Lex3LearningExperienceStore.kt; do
  git -C "$repo_dir" show "$revision:app/src/main/java/at/heinz/deviceai/learning/$filename" > "$run_root/$filename"
done
sha256sum "$evidence_dir/GammaChallenger.kt" "$run_root/ContinuumLearningExperience.kt" "$run_root/Lex3LearningExperienceStore.kt"
kotlinc -version
kotlinc "$run_root/ContinuumLearningExperience.kt" "$run_root/Lex3LearningExperienceStore.kt" "$evidence_dir/GammaChallenger.kt" -include-runtime -d "$run_root/gamma.jar"
java -Xmx384m -cp "$run_root/gamma.jar" at.heinz.deviceai.learning.GammaChallenger "$mode"
