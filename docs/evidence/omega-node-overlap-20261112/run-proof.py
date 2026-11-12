import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone

repo = Path('/workspace/scratch/01387b612451/wt-node-audit')
out = Path('/workspace/scratch/01387b612451/node-overlap-qa')
label = sys.argv[1]
command = sys.argv[2:]
paths = ['.github/continuum/coding-mcp.test.mjs', '.github/continuum/coding-mcp-server.mjs']
def snapshot():
    return {p: hashlib.sha256((repo / p).read_bytes()).hexdigest() for p in paths}
before = snapshot()
receipt = {
    'label': label, 'command': command, 'cwd': str(repo),
    'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip(),
    'uid': os.getuid(),
    'node': subprocess.check_output(['node', '--version'], text=True).strip(),
    'startedAt': datetime.now(timezone.utc).isoformat(),
    'sourcesBefore': before,
}
with (out / (label + '.log')).open('wb') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT, timeout=60)
receipt.update(exitCode=result.returncode, finishedAt=datetime.now(timezone.utc).isoformat(),
               sourcesAfter=snapshot(), sourceUnchanged=(before == snapshot()))
(out / (label + '.json')).write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt))
raise SystemExit(result.returncode)
