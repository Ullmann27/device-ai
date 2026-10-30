import test from 'node:test';
import assert from 'node:assert/strict';
import { TASKS, validateTasks } from './continuum-agent-benchmark.mjs';

test('benchmark has exactly 30 unique, scoped defect tasks', () => {
  assert.equal(TASKS.length, 30);
  const ids = new Set();
  for (const task of TASKS) {
    assert.match(task.id, /^[a-z][a-z0-9-]{2,80}$/);
    assert.equal(ids.has(task.id), false, 'duplicate task: ' + task.id);
    ids.add(task.id);
    assert.ok(task.goal.length >= 12);
    assert.ok(['numeric', 'general'].includes(task.grammar));
    assert.ok(task.files && Object.keys(task.files).length > 0);
    assert.ok(task.oracle.some(action => action.tool === 'edit_file'));
    assert.ok(task.oracle.every(action => action.tool !== 'run_tests' && action.tool !== 'apply'));
    for (const action of task.oracle.filter(action => action.tool === 'edit_file')) {
      assert.ok(Object.hasOwn(task.files, action.args.path), 'out-of-scope edit: ' + task.id);
      const source = task.files[action.args.path];
      assert.equal(source.split(action.args.old_text).length, 2,
        'non-unique reference replacement: ' + task.id);
      assert.notEqual(action.args.old_text, action.args.new_text);
    }
  }
});

test('baseline fails and independent reference repair passes on all 30 actual fixtures', () => {
  const validation = validateTasks();
  assert.equal(validation.length, 30);
  const failed = validation.filter(record => !record.valid);
  assert.deepEqual(failed, [], 'invalid benchmark fixtures');
  assert.equal(validation.every(record => record.baselineFails && record.referencePasses), true);
});
