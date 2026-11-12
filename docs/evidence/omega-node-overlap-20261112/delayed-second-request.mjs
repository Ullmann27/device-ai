const originalFetch = globalThis.fetch;
let first;
globalThis.fetch = async (input, init) => {
  let body;
  try { body = JSON.parse(init?.body); } catch {}
  if (body?.method === 'tools/call' && body.id === 2 && body.params?.name === 'get_status') {
    first = originalFetch(input, init);
    return first;
  }
  if (body?.method === 'tools/call' && body.id === 3 && body.params?.name === 'list_tasks' && first) {
    await first;
    process.stderr.write('CONTROL: second request dispatched after first response\n');
  }
  return originalFetch(input, init);
};
