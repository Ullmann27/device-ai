import { resolve } from 'node:path';
import { mkdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

/**
 * Official NeMo AutoModel 26.10 two-GPU smoke recipe launcher.
 * Never runs remotely, never silently starts Docker, never downloads a model
 * unless the owner explicitly opts into execution on authorized hardware.
 */
export function buildNemoSmokeInvocation(checkpointDirectory) {
  if (typeof checkpointDirectory !== 'string' || checkpointDirectory.trim() === '') {
    throw new TypeError('Checkpoint directory is required');
  }
  const checkpoints = resolve(checkpointDirectory);
  return {
    command: 'docker',
    args: [
      'run', '--gpus', 'all', '--rm', '--shm-size=8g',
      '-v', checkpoints + ':/opt/Automodel/checkpoints',
      'nvcr.io/nvidia/nemo-automodel:26.10.00',
      'automodel', '/opt/Automodel/examples/llm_finetune/qwen/qwen3_0p6b_hellaswag.yaml',
      '--nproc-per-node', '2', '--step_scheduler.max_steps', '10'
    ],
    checkpoints,
    requires: 'Two compatible authorized NVIDIA GPUs, Docker and NVIDIA Container Toolkit',
    purpose: '10-step NeMo training smoke test; not a model-quality claim'
  };
}

function run() {
  const execute = process.argv.includes('--execute');
  const unknown = process.argv.slice(2).filter(a => !['--execute','--dry-run'].includes(a));
  if (unknown.length) throw new Error('Unknown arguments: ' + unknown.join(', '));
  const plan = buildNemoSmokeInvocation(process.env.CONTINUUM_CHECKPOINTS || './checkpoints');
  if (!execute) {
    process.stdout.write(JSON.stringify({ mode: 'DRY_RUN', ...plan }, null, 2) + '\n');
    return;
  }
  if (process.env.CONTINUUM_NEMO_EXECUTE !== 'YES') {
    throw new Error('Explicit consent required: CONTINUUM_NEMO_EXECUTE=YES');
  }
  const gpu = spawnSync('nvidia-smi', ['-L'], { encoding: 'utf8', timeout: 10_000 });
  if (gpu.error || gpu.status !== 0) throw new Error('NVIDIA GPU enumeration failed');
  const count = gpu.stdout.split('\n').filter(s => /^GPU [0-9]+:/.test(s)).length;
  if (count < 2) throw new Error('Official smoke recipe requires two NVIDIA GPUs');
  mkdirSync(plan.checkpoints, { recursive: true });
  const result = spawnSync(plan.command, plan.args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  process.exitCode = result.status ?? 1;
}

if (process.argv[1] && resolve(process.argv[1]) === new URL(import.meta.url).pathname) {
  try { run(); } catch (error) {
    process.stderr.write('NeMo launcher: ' + error.message + '\n');
    process.exitCode = 1;
  }
}
