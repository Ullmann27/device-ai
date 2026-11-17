/** Bounded process bridge to the canonical service reader; never loads a supplied module or command. */
import path from 'node:path';
import fs from 'node:fs';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const WORKER = fileURLToPath(new URL('./coding-mcp-reader-worker.mjs', import.meta.url));
const allowed = Object.freeze(['getStatus', 'listAgents', 'listTasks', 'getEvidence']);
const failed = () => Object.assign(new Error('MCP_CANONICAL_READ_UNAVAILABLE'), { code: 'MCP_CANONICAL_READ_UNAVAILABLE' });

/** The service config is owner-controlled. Only fixed JSON-safe projected observations cross this pipe. */
export function createCodingMcpReader({ configPath, timeoutMs = 9000, maxBytes = 65536 }) {
  if (!path.isAbsolute(configPath) || fs.realpathSync(configPath) !== configPath || !Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 9000 || !Number.isInteger(maxBytes) || maxBytes < 1 || maxBytes > 65536) throw failed();
  const children = new Set(); let closing = false;
  const read = (method, args = {}, { signal } = {}) => new Promise((resolve, reject) => {
    if (closing || signal?.aborted || children.size >= 2) return reject(failed());
    const child = spawn(process.execPath, ['--max-old-space-size=128', WORKER], {
      cwd: path.dirname(configPath), shell: false, detached: true,
      env: { PATH: '/usr/bin:/bin', LANG: 'C.UTF-8' }, stdio: ['pipe', 'pipe', 'ignore'],
    });
    children.add(child);
    let settled = false, buffer = [], size = 0, timer;
    const kill = () => { try { process.kill(-child.pid, 'SIGKILL'); } catch { try { child.kill('SIGKILL'); } catch {} } };
    const finish = (error, result) => {
      if (settled) return; settled = true; clearTimeout(timer); signal?.removeEventListener('abort', abort); kill();
      error ? reject(failed()) : resolve(result);
    };
    const abort = () => finish(failed());
    timer = setTimeout(abort, timeoutMs); signal?.addEventListener('abort', abort, { once: true });
    child.on('error', abort); child.stdin.on('error', abort);
    child.stdout.on('data', chunk => { size += chunk.length; if (size > maxBytes) abort(); else buffer.push(chunk); });
    child.on('close', code => {
      children.delete(child);
      if (settled) return;
      if (code !== 0) return finish(failed());
      let response;
      try { response = JSON.parse(Buffer.concat(buffer).toString('utf8')); } catch { return finish(failed()); }
      if (!response || response.ok !== true || !Object.hasOwn(response, 'value') || Object.keys(response).some(k => !['ok', 'value'].includes(k))) return finish(failed());
      finish(null, response.value);
    });
    child.stdin.end(JSON.stringify({ configPath, method, args }));
  });
  return Object.freeze({
    ...Object.fromEntries(allowed.map(method => [method, (args, options) => read(method, args, options)])),
    close: async () => {
      closing = true;
      await Promise.all([...children].map(child => new Promise(resolve => {
        child.once('close', resolve);
        try { process.kill(-child.pid, 'SIGKILL'); } catch { try { child.kill('SIGKILL'); } catch {} }
        if (child.exitCode !== null) resolve();
      })));
    },
  });
}
