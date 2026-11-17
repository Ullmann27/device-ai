#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v kotlinc >/dev/null 2>&1 || { printf '%s\n' 'UNAVAILABLE: kotlinc not installed' >&2; exit 2; }
command -v java >/dev/null 2>&1 || { printf '%s\n' 'UNAVAILABLE: Java runtime not installed' >&2; exit 2; }

work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT

kotlinc \
  "$repo_root/app/src/main/java/at/heinz/deviceai/ContinuumWorkAreaNavigationSpec.kt" \
  "$repo_root/scripts/continuum-navigation-contract-checks.kt" \
  -include-runtime -d "$work_dir/navigation-checks.jar"
java -cp "$work_dir/navigation-checks.jar" at.heinz.deviceai.ContinuumNavigationContractChecks
