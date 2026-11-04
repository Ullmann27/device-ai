// Read-only availability gate. Never submits an owner task or invokes a runtime.
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {performance} from 'node:perf_hooks';
const source = path.resolve(process.argv[2]);
const {queryPinnedLocalCoding} = await import(pathToFileURL(path.join(source, 'scripts/continuum-local-model-observation.mjs')).href);
const required = ['CONTINUUM_LOCAL_BRAIN_MODEL_PATH', 'CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH',
  'CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST', 'CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256', 'CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND'];
const missing = required.filter(name => !process.env[name]);
const start = performance.now();
let status, error = null;
if (!missing.length) {
  status = 'CONFIGURATION_PRESENT_REQUIRES_SEPARATE_AUTHORIZED_EXECUTION';
} else {
  // The inspected production function checks these missing prerequisites before
  // task binding, state access or runtime execution. No observation is fabricated.
  try { await queryPinnedLocalCoding({env: process.env}); status = 'UNEXPECTED_ADMISSION'; }
  catch (caught) { error = caught.message; status = error === 'BLOCKED_LOCAL_MODEL_UNAVAILABLE' ? 'BLOCKED_LOCAL_MODEL_UNAVAILABLE' : 'OTHER_PREFLIGHT_ERROR'; }
}
process.stdout.write(JSON.stringify({status, error, missing_variable_names: missing,
  model_invocations: 0, owner_task_submissions: 0, wall_ms: performance.now() - start,
  evidence_class: 'READ_ONLY_PRODUCTION_PREREQUISITE_CHECK'}, null, 2) + '\n');
if (status === 'UNEXPECTED_ADMISSION' || status === 'OTHER_PREFLIGHT_ERROR') process.exitCode = 1;
