from pathlib import Path
import datetime, hashlib, json, re, subprocess

ROOT = Path('/workspace/scratch/174c84456ee3/continuum-c1')
QA = ROOT.parent / 'qa'
FIXTURE = QA / 'contract-controls'
FIXTURE.mkdir(exist_ok=True)
(FIXTURE / '.github/continuum').mkdir(parents=True, exist_ok=True)
app = FIXTURE / 'app'
if not app.exists():
    app.symlink_to(ROOT / 'app', target_is_directory=True)

# These controls change only the strings read by copies of existing contracts.
# They do not modify Android sources or simulate Android/model execution.
CASES = [
    ('owner-input', 'android-integration-wiring', 'activity',
     'createToolGate(ownerTurn = text,', 'createToolGate(ownerTurn = resolvedQuestion,',
     'cognitive runtime owns'),
    ('bot-memory', 'android-integration-wiring', 'activity',
     'if(codingPlanning == null && botTask == null) memoryStore.all()',
     'if(codingPlanning == null) memoryStore.all()', 'cognitive runtime owns'),
    ('planning-tool-budget', 'android-integration-wiring', 'activity',
     'AgentLimits(maxToolCalls=0,maxRetries=0)', 'AgentLimits(maxToolCalls=1,maxRetries=0)',
     'cognitive runtime owns'),
    ('trace-drop', 'cognitive-trace-wiring', 'activity',
     'generateLocal(cancellation,turnTrace,', 'generateLocal(cancellation,null,',
     'one correlation follows'),
    ('speech-locale', 'coding-agent-speech-wiring', 'recognizer',
     'RecognizerIntent.EXTRA_LANGUAGE, "de-AT"', 'RecognizerIntent.EXTRA_LANGUAGE, "en-US"',
     'developer speech uses'),
    ('development-route', 'coding-agent-speech-wiring', 'menu',
     '"development" to Slot.LABEL_DEVELOPING', '"disconnected-development" to Slot.LABEL_DEVELOPING',
     'coding agent has one integrated entry'),
    ('avatar-audio-frame', 'android-avatar-wiring', 'activity',
     'avatarDirector.wordRange(utteranceId, start, end, audioFrame = frame)',
     'avatarDirector.wordRange(utteranceId, start, end)', 'avatar director is wired'),
    ('avatar-visible-sink', 'android-avatar-wiring', 'activity',
     'avatarDirector.attach(it)', 'avatarDirector.detach(it)', 'avatar director is wired'),
    ('reference-navigation', 'aaa-cockpit-and-effects', 'referenceLayout',
     'if (wide) boxes[Slot.RAIL]', 'if (!wide) boxes[Slot.RAIL]',
     'no new Flutter dependency'),
]
results = []
for name, file, variable, original, replacement, pattern in CASES:
    source = (ROOT / '.github/continuum' / (file + '.test.mjs')).read_text()
    initializer = re.compile(r'^(const\s+' + re.escape(variable) + r'\s*=\s*[^\n]+);$', re.M)
    match = initializer.search(source)
    assert match is not None, (name, 'initializer unavailable')
    transformed = (source[:match.start()] + match.group(1) + '.replaceAll(' +
                   json.dumps(original) + ',' + json.dumps(replacement) + ');' + source[match.end():])
    probe = FIXTURE / '.github/continuum' / (name + '.test.mjs')
    probe.write_text(transformed)
    command = ['node', '--test', '--test-concurrency=1', '--test-reporter=tap',
               '--test-name-pattern=' + pattern, str(probe)]
    log = QA / ('contract-negative-' + name + '.tap')
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    with log.open('x') as output:
        run = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
    raw = log.read_text()
    expected = run.returncode == 1 and re.search(r'^not ok \d+ - .*' + re.escape(pattern), raw, re.M) is not None
    results.append({'case': name, 'sourceTest': '.github/continuum/' + file + '.test.mjs',
                    'sourceTestSha256': hashlib.sha256(source.encode()).hexdigest(),
                    'mutationOnlyInMemory': {'variable': variable, 'original': original, 'replacement': replacement},
                    'command': command, 'startedAt': started,
                    'finishedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    'exitCode': run.returncode, 'expectedFailureObserved': expected,
                    'log': log.name, 'logSha256': hashlib.sha256(log.read_bytes()).hexdigest()})
(QA / 'contract-negative-controls.json').write_text(json.dumps(results, indent=2) + '\n')
print(json.dumps({'controls': len(results), 'expectedFailuresObserved': sum(x['expectedFailureObserved'] for x in results)}))
raise SystemExit(0 if all(x['expectedFailureObserved'] for x in results) else 1)
