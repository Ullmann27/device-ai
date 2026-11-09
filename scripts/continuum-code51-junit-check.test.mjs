import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';

test('Android receipt gate rejects missing, duplicated, failed and changed-skip evidence', () => {
  const result = spawnSync('python3', ['-B', '-c', `
import importlib.util, tempfile, pathlib, xml.etree.ElementTree as ET
spec = importlib.util.spec_from_file_location('gate', 'scripts/continuum-code51-junit-check.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
with tempfile.TemporaryDirectory() as d:
    root = ET.Element('testsuite', tests=str(gate.EXPECTED_TESTS), failures='0', errors='0', skipped=str(len(gate.SKIPS)))
    for identity in sorted(gate.SKIPS):
        klass, name = identity.split('#')
        ET.SubElement(ET.SubElement(root, 'testcase', classname=klass, name=name), 'skipped')
    for klass in sorted(gate.REQUIRED):
        ET.SubElement(root, 'testcase', classname=klass, name='requiredFixture')
    while len(root) < gate.EXPECTED_TESTS:
        ET.SubElement(root, 'testcase', classname='fixture.Padding', name='case'+str(len(root)))
    path = pathlib.Path(d) / 'TEST-fixture.xml'
    def write():
        ET.ElementTree(root).write(path)
    def rejects():
        try: gate.verify(d)
        except ValueError: return
        raise AssertionError('invalid evidence accepted')
    rejects()
    write()
    assert gate.verify(d)['tests'] == gate.EXPECTED_TESTS
    root[-1].set('name', root[-2].get('name')); write(); rejects()
    root[-1].set('name', 'restored')
    ET.SubElement(root[-1], 'failure'); root.set('failures', '1'); write(); rejects()
    root[-1].remove(root[-1][0]); root.set('failures', '0')
    original = root[0].get('classname')
    root[0].set('classname', 'unexpected.Skip'); write(); rejects()
    root[0].set('classname', original)
    required = root[len(gate.SKIPS)]
    required.set('classname', 'missing.Required'); write(); rejects()
`], {encoding: 'utf8'});
  assert.equal(result.status, 0, result.stdout + result.stderr);
});
