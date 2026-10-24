#!/usr/bin/env bash
# Public, already-open TEST2 diagnostics only. No Gradle, SDK, network or product writes.
set -euo pipefail

diagnostic_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ $# -gt 1 || ${1:-} == --help ]]; then
  printf '%s\n' 'Usage: KOTLINC=/path/to/kotlinc ./reproduce-ctx2-open-taxonomy.sh [empty-output-directory]'
  exit "$(( $# > 1 ? 2 : 0 ))"
fi
for required_tool in git python3 java; do
  command -v "$required_tool" >/dev/null || { printf 'UNAVAILABLE: %s\n' "$required_tool" >&2; exit 2; }
done
repository_root=$(git -C "$diagnostic_dir" rev-parse --show-toplevel)
kotlin_compiler=${KOTLINC:-kotlinc}
command -v "$kotlin_compiler" >/dev/null || { printf 'UNAVAILABLE: %s; set KOTLINC to an installed compiler\n' "$kotlin_compiler" >&2; exit 2; }
if [[ $# -eq 1 ]]; then
  output_dir=$(python3 -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "$1")
  mkdir -p -- "$output_dir"
  [[ -z $(find "$output_dir" -mindepth 1 -maxdepth 1 -print -quit) ]] || { printf '%s\n' 'Output directory must be empty.' >&2; exit 2; }
else
  output_dir=$(mktemp -d "${TMPDIR:-/tmp}/ctx2-open-taxonomy-results.XXXXXX")
fi
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/ctx2-open-taxonomy-build.XXXXXX")
cleanup() { python3 -c 'import shutil,sys; shutil.rmtree(sys.argv[1])' "$work_dir"; }
trap cleanup EXIT

python3 "$diagnostic_dir/verify-ctx2-open-taxonomy.py" prepare "$repository_root" "$work_dir" "$output_dir"
{
  "$kotlin_compiler" -version
  java -version
  python3 --version
} > "$output_dir/tool-versions.txt" 2>&1
if ! "$kotlin_compiler" "$work_dir"/*.kt "$diagnostic_dir/TaxonomyTrace.kt" \
  -jvm-target 17 -include-runtime -d "$work_dir/trace.jar" > "$output_dir/compile.log" 2>&1; then
  cat "$output_dir/compile.log" >&2
  exit 1
fi
java -cp "$work_dir/trace.jar" at.heinz.deviceai.intelligence.maxultra.TaxonomyTraceKt \
  "$work_dir/wrong-ids.txt" "$work_dir/continuum-neural-swb1.base64" "$output_dir/traces.json" "$work_dir"
python3 "$diagnostic_dir/verify-ctx2-open-taxonomy.py" verify "$repository_root" "$work_dir" "$output_dir"
printf 'Evidence directory: %s\n' "$output_dir"
