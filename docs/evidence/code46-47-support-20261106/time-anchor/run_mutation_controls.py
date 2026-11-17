from pathlib import Path
import hashlib
import json
import subprocess

root = Path('/workspace/scratch/b31a9d731016/time-anchor-test-contract')
evidence = Path(__file__).resolve().parent
relative = Path('app/src/main/java/at/heinz/deviceai/AgentActivity.kt')
source = (root / relative).read_text()
source_hash = hashlib.sha256((root / relative).read_bytes()).hexdigest()
command = json.loads((evidence / 'green-result.json').read_text())['test_command']
clock = 'at.heinz.deviceai.cognitive.LocalClockIntent.promptAnchor(java.time.ZonedDateTime.now())'
old_tail = 'val promptEnd = promptTail + briefInstruction + "\\n" +\n                    ' + clock
short_tail = 'val promptEnd = promptTail + briefInstruction + "\\n"'
body_start = 'val promptBody = buildString {'
assert source.count(old_tail) == 1
assert source.count(body_start) == 1
assert source.count('requiredTail=promptEnd,') == 1
clock_in_prefix = source.replace(body_start, body_start + '\n                    append(' + clock + ').append(\'\\n\')', 1)
cases = {
    'unchanged-source-control': source,
    'clock-only-in-prefix': clock_in_prefix.replace(old_tail, short_tail, 1),
    'clock-also-in-prefix': clock_in_prefix,
    'required-tail-disconnected': source.replace('requiredTail=promptEnd,', 'requiredTail=promptTail,', 1),
    'required-tail-truncated': source.replace('requiredTail=promptEnd,', 'requiredTail=promptEnd.take(0),', 1),
    'clock-computed-but-unused': source.replace(old_tail, short_tail + '\n                ' + clock, 1),
}
results = []
for name, text in cases.items():
    fixture = evidence / 'source-controls' / name
    target = fixture / relative
    target.parent.mkdir(parents=True)
    target.write_text(text)
    run = subprocess.run(command, cwd=fixture, capture_output=True, text=True, timeout=30)
    output = run.stdout + run.stderr
    (evidence / (name + '-junit.txt')).write_text(output)
    expected = 0 if name == 'unchanged-source-control' else 1
    if expected == 0:
        assert run.returncode == 0 and 'OK (8 tests)' in output, output
    else:
        assert run.returncode == 1 and 'Tests run: 8,  Failures: 1' in output, output
        assert 'timeAnchorIsInTheRequiredTailNotInTheStaticPrefix' in output, output
    result = {'case': name, 'exit': run.returncode, 'expected_exit': expected,
              'source_sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
              'assertion': next((line for line in output.splitlines() if line.startswith('java.lang.AssertionError:')), None)}
    results.append(result)
    print(json.dumps(result, ensure_ascii=False))
assert hashlib.sha256((root / relative).read_bytes()).hexdigest() == source_hash
(evidence / 'mutation-results.json').write_text(json.dumps({
    'production_activity_unchanged_sha256': source_hash,
    'test_command': command, 'results': results}, indent=2, ensure_ascii=False) + '\n')
