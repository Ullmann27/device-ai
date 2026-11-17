/** Fixed private child entrypoint. This is not an executable tool and accepts no plugin/module/path selection. */
const METHODS = new Set(['getStatus', 'listAgents', 'listTasks', 'getEvidence']);
let chunks = [], bytes = 0;
process.stdin.on('data', chunk => {
  bytes += chunk.length;
  if (bytes > 16384) process.exit(1);
  chunks.push(chunk);
});
process.stdin.on('end', async () => {
  try {
    const request = JSON.parse(Buffer.concat(chunks).toString('utf8')); chunks = [];
    if (!request || Object.keys(request).some(key => !['configPath', 'method', 'args'].includes(key)) || !METHODS.has(request.method) || typeof request.configPath !== 'string') process.exit(1);
    const { createCodingServiceReader } = await import('./coding-service-reader.mjs');
    const reader = createCodingServiceReader({ configPath: request.configPath });
    const value = await reader[request.method](request.args);
    const result = JSON.stringify({ ok: true, value });
    if (Buffer.byteLength(result) > 65536) process.exit(1);
    process.stdout.write(result);
  } catch { process.exitCode = 1; }
});
