import { appendFileSync } from 'node:fs';

// This preload belongs only to the isolated integration-test child process.
// Production still selects its fixed Inworld URL; no production knob is added.
if (process.env.NODE_ENV !== 'test') {
  throw new Error('The Inworld fetch fixture may only run in tests');
}

const expectedUrl = 'https://api.inworld.ai/v1/chat/completions';
const target = new URL(process.env.INWORLD_TEST_UPSTREAM_URL);
if (target.protocol !== 'http:' || target.hostname !== '127.0.0.1'
    || target.username || target.password || !target.port
    || target.pathname !== '/v1/chat/completions' || target.search || target.hash) {
  throw new Error('The Inworld fixture requires a loopback HTTP endpoint');
}

const originalFetch = globalThis.fetch;
globalThis.fetch = async (input, init) => {
  const url = input instanceof Request ? input.url : String(input);
  appendFileSync(process.env.INWORLD_TEST_AUDIT_PATH, `${JSON.stringify({
    url,
    method: init?.method ?? (input instanceof Request ? input.method : 'GET'),
    redirect: init?.redirect ?? (input instanceof Request ? input.redirect : undefined),
  })}\n`);
  if (url !== expectedUrl) {
    throw new Error('Unexpected outbound fetch blocked by the Inworld test fixture');
  }
  if (input instanceof Request) {
    return originalFetch(new Request(target, input), init);
  }
  return originalFetch(target, init);
};
