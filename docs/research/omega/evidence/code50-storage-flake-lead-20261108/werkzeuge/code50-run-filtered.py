"""Startet einen Befehl mit Node 24 im PATH und entfernt NUR den von der Sandbox injizierten GIT_CONFIG-Eintrag core.fsmonitor.

Aufruf: code50-run-filtered.py <Arbeitsverzeichnis> <Befehl> [Argumente...]

Alle übrigen injizierten GIT_CONFIG-Einträge (safe.bareRepository, credential.interactive) bleiben erhalten.
Der Eintrag core.fsmonitor wird nur im Kindprozess entfernt; er überlagert sonst die repository-lokale
Hook-Einstellung in .github/continuum/branch-discovery.test.mjs. Auf GitHub-Runnern tritt das nicht auf.
Der Code ist funktional identisch mit der Version, die für alle Messungen verwendet wurde (nur Kommentare übersetzt).
"""
import os
import re
import subprocess
import sys

cwd, cmd = sys.argv[1], sys.argv[2:]
env = dict(os.environ)
count = int(env.get("GIT_CONFIG_COUNT", "0"))
entries = [(env.get(f"GIT_CONFIG_KEY_{i}"), env.get(f"GIT_CONFIG_VALUE_{i}")) for i in range(count)]
kept = [(k, v) for k, v in entries if k != "core.fsmonitor"]
for name in list(env):
    if re.fullmatch(r"GIT_CONFIG_(COUNT|KEY_\d+|VALUE_\d+)", name):
        del env[name]
env["GIT_CONFIG_COUNT"] = str(len(kept))
for i, (k, v) in enumerate(kept):
    env[f"GIT_CONFIG_KEY_{i}"] = k
    env[f"GIT_CONFIG_VALUE_{i}"] = v
env["PATH"] = "/tmp/node24/node_modules/node/bin:" + env["PATH"]
print(f"[launcher] kept {len(kept)}/{count} GIT_CONFIG entries; node={subprocess.run(['node', '-v'], env=env, capture_output=True, text=True).stdout.strip()}; user uid={os.getuid()}", flush=True)
sys.exit(subprocess.run(cmd, cwd=cwd, env=env).returncode)
