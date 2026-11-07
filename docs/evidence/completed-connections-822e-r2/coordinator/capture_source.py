"""Record the actual clean source commit and review-critical file bytes."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys

repo = Path(sys.argv[1]).resolve()
destination = Path(sys.argv[2]).resolve()

def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=repo, text=True).strip()

status = git("status", "--porcelain")
if status:
    raise SystemExit("Refusing source freeze on a dirty checkout: " + status)

critical = [
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt",
    "app/src/main/java/at/heinz/deviceai/learning/lm/LmRuntime.kt",
    "app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt",
    "app/src/main/java/at/heinz/deviceai/AgentActivity.kt",
    ".github/continuum/development-backlog.mjs",
    ".github/continuum/autonomous-teamleader.mjs",
    ".github/continuum/local-teamleader-loop.mjs",
    ".github/continuum/coding-orchestrator.mjs",
    ".github/continuum/coding-service-worker.mjs",
    ".github/continuum/coding-service-reader.mjs",
]
manifest = {
    "checkout": str(repo),
    "local_commit": git("rev-parse", "HEAD"),
    "complete_tree": git("rev-parse", "HEAD^{tree}"),
    "worktree_clean": True,
    "subtrees": {name: git("rev-parse", "HEAD:" + name)
                 for name in ["app", ".github", "scripts", "tools"]},
    "critical_sha256": {name: hashlib.sha256((repo / name).read_bytes()).hexdigest()
                        for name in critical},
}
destination.write_text(json.dumps(manifest, indent=2) + "\n")
print(json.dumps({"local_commit": manifest["local_commit"],
                  "complete_tree": manifest["complete_tree"], "worktree_clean": True}))
