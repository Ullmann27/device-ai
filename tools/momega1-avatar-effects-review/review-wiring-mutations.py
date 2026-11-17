#!/usr/bin/env python3
"""Run real Node wiring contracts against read-only in-memory source mutations."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1]).resolve()
out = Path(sys.argv[2]).resolve()
out.mkdir(parents=True, exist_ok=True)
tests = [f'.github/continuum/{n}.test.mjs' for n in (
    'android-integration-wiring', 'continuum-local-dialogue-quality-wiring',
    'm2-owner-brain-contract', 'm5-voice-duplex-wiring')]
activity = 'app/src/main/java/at/heinz/deviceai/AgentActivity.kt'
view = 'app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt'
cases = [
    ('owner-turn-binding', activity, 'createToolGate(ownerTurn = text,', 'createToolGate(ownerTurn = agentTask,'),
    ('complete-task-budget', activity, 'at.heinz.deviceai.cognitive.LocalChatPromptBudget.acceptsCompleteTask(resolvedQuestion)', 'true'),
    ('camera-permission-auto-start', activity,
     '// Permission is not a new start gesture; a late callback never opens either sensor.\n            refreshReadinessGuide()',
     '// Mutation: a permission callback opens the camera without a new owner gesture.\n            readinessCameraInput().start(true, foregroundLearningActive, 0)\n            refreshReadinessGuide()'),
    ('motion-foreground', view, 'foregroundActive && motionAllowed && isAttachedToWindow && isShown && windowVisibility == VISIBLE',
     'motionAllowed && isAttachedToWindow && isShown && windowVisibility == VISIBLE'),
    ('planning-private-memory', activity, 'memories = { if(codingPlanning == null) memoryStore.all() + ownerFactMemories() else emptyList() }',
     'memories = { memoryStore.all() + ownerFactMemories() }'),
    ('planning-tool-authority', activity, 'AgentLimits(maxToolCalls=0,maxRetries=0)', 'AgentLimits(maxToolCalls=1,maxRetries=0)'),
    ('model-binding', activity, 'check(LocalTurnModelBinding.stillBound(imported, localRuntime.loadedModel)) { LocalTurnModelBinding.CHANGED }',
     'check(true) { LocalTurnModelBinding.CHANGED }'),
]
receipt = {'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
           'testHashes': {p: hashlib.sha256((root/p).read_bytes()).hexdigest() for p in tests}, 'runs': []}
with tempfile.TemporaryDirectory(prefix='independent-wiring-') as tmp:
    tmp = Path(tmp)
    hook = tmp/'override.mjs'
    hook.write_text("""import fs from 'node:fs';
import path from 'node:path';
import {syncBuiltinESMExports} from 'node:module';
const original=fs.readFileSync;
fs.readFileSync=function(file,...args) {
  if(typeof file==='string' && path.resolve(file)===process.env.REVIEW_TARGET)
    return original(process.env.REVIEW_REPLACEMENT,...args);
  return original(file,...args);
};
syncBuiltinESMExports();
""")
    for name, source, old, new in [('positive-control', None, None, None)] + cases:
        env = os.environ.copy()
        command = ['node', '--test', '--test-concurrency=1', *tests]
        item = {'name': name, 'command': command}
        if source:
            raw = (root/source).read_text()
            expected = 2 if name == 'model-binding' else 1
            assert raw.count(old) == expected, (name, raw.count(old), expected)
            mutated = raw.replace(old, new)
            replacement = tmp/'source.txt'
            replacement.write_text(mutated)
            env.update(REVIEW_TARGET=str(root/source), REVIEW_REPLACEMENT=str(replacement))
            command = ['node', '--import', str(hook), *command[1:]]
            item.update(source=source, before=old, after=new,
                        originalSha256=hashlib.sha256(raw.encode()).hexdigest(),
                        mutatedSha256=hashlib.sha256(mutated.encode()).hexdigest())
        run = subprocess.run(command, cwd=root, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (out/(name+'.txt')).write_bytes(run.stdout)
        item['exit'] = run.returncode
        item['expectedFailure'] = bool(source)
        receipt['runs'].append(item)
        print(name, run.returncode, flush=True)
(out/'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
assert all((r['exit'] != 0) == r['expectedFailure'] for r in receipt['runs']), 'mutation survived or positive control failed'
