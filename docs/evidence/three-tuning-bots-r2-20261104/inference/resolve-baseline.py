"""Resolve the published round-one commit, with an exact-tree local fallback."""
import json
import subprocess
import sys

repo = sys.argv[1]
public = "04b46f125d1086e6ea540d3c68700407f340f607"
local = "db3295d51c769afdaf809ac1dfae2ada84a3274f"
expected_tree = "d753188fe0e67f6c6f098db6b7073ea199e05530"
present = subprocess.run(["git", "-C", repo, "cat-file", "-e", public],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
resolved = public if present else local
kind = subprocess.check_output(["git", "-C", repo, "cat-file", "-t", resolved], text=True).strip()
if kind != "commit":
    raise SystemExit("Baseline object is not a commit; refusing fallback from an existing public object")
tree = subprocess.check_output(["git", "-C", repo, "rev-parse", resolved + "^{tree}"], text=True).strip()
if tree != expected_tree:
    raise SystemExit("Baseline tree differs from the verified round-one tree")
print(json.dumps({
    "publicBaselineCommit": public, "localFallbackBaselineCommit": local,
    "baselineCommit": resolved, "baselineResolution": "published" if present else "local_fallback",
    "expectedBaselineTree": expected_tree, "resolvedBaselineTree": tree
}, indent=2))
