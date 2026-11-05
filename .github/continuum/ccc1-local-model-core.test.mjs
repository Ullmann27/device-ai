// Owner goal: own coding bots reason with a real on-device model. These tests use a REAL loopback HTTP
// server standing in for the model endpoint; they verify transport, schema and fail-closed behaviour,
// not the quality of any particular model.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {LocalModelLanguageCore, loopbackEndpoint, parseModelRanking} from './ccc1-local-model-core.mjs';
import {LexicalLanguageCore} from './ccc1-interpreter.mjs';

const candidates = [
  {module: 'src/text.mjs', export: 'slugify', description: 'convert a title into a url slug', required: 1, max: 1},
  {module: 'src/math.mjs', export: 'sumValues', description: 'add numbers of a list', required: 1, max: 1},
  {module: 'src/date.mjs', export: 'isoWeek', description: 'iso week number of a date', required: 1, max: 1},
];

async function withServer(handler, run) {
  const requests = [];
  const server = http.createServer((req, res) => {
    let body = ''; req.on('data', c => body += c);
    req.on('end', () => { requests.push({url: req.url, body: JSON.parse(body || '{}')}); handler(req, res); });
  });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  try { return await run(`http://127.0.0.1:${server.address().port}`, requests); }
  finally { server.closeAllConnections?.(); await new Promise(r => server.close(r)); }
}
const reply = content => (req, res) => { res.setHeader('content-type', 'application/json'); res.end(JSON.stringify({choices: [{message: {content}}]})); };

test('only loopback model endpoints are accepted (offline/privacy boundary)', () => {
  assert.equal(loopbackEndpoint(undefined), null);
  assert.equal(loopbackEndpoint('http://127.0.0.1:8080').hostname, '127.0.0.1');
  assert.equal(loopbackEndpoint('http://localhost:8080').hostname, 'localhost');
  for (const bad of ['https://api.example.com', 'http://192.168.0.5:8080', 'http://user:pw@127.0.0.1:1', 'file:///etc/passwd'])
    assert.throws(() => loopbackEndpoint(bad), /CCC1_MODEL_ENDPOINT/);
});

test('strict reply schema rejects invented, duplicate or out-of-range rankings', () => {
  assert.equal(parseModelRanking('{"ranking":[{"id":0,"score":0.9},{"id":2,"score":0.1}]}', 3).ok, true);
  assert.equal(parseModelRanking('Sure! {"ranking":[{"id":1,"score":1}]} done', 3).ok, true);
  for (const [text, reason] of [['no json', 'NO_JSON'], ['{bad', 'NO_JSON'], ['{"x":1}', 'NO_RANKING'],
    ['{"ranking":[{"id":7,"score":0.5}]}', 'ID_OUT_OF_RANGE'], ['{"ranking":[{"id":0,"score":1.5}]}', 'SCORE_OUT_OF_RANGE'],
    ['{"ranking":[{"id":0,"score":0.5},{"id":0,"score":0.4}]}', 'DUPLICATE_ID']])
    assert.equal(parseModelRanking(text, 3).reason, reason, text);
});

test('a valid local model ranking re-orders candidates and is recorded as used', async () => {
  await withServer(reply('{"ranking":[{"id":0,"score":0.05},{"id":1,"score":0.05},{"id":2,"score":1}]}'), async (url, requests) => {
    const core = new LocalModelLanguageCore({endpoint: url, model: 'gemma-test'});
    const base = await new LexicalLanguageCore().rank('add numbers of a list', candidates);
    const scores = await core.rank('add numbers of a list', candidates);
    assert.equal(core.last.used, true);
    assert.equal(requests[0].url, '/v1/chat/completions');
    assert.equal(requests[0].body.temperature, 0);
    assert.equal(requests[0].body.model, 'gemma-test');
    assert.match(requests[0].body.messages[1].content, /Goal: add numbers of a list/);
    const shortlistOrder = base.map((s, i) => ({s, i})).sort((a, b) => b.s - a.s || a.i - b.i).map(o => o.i);
    const favoured = shortlistOrder[2];
    assert.equal(scores.indexOf(Math.max(...scores)), favoured, 'model preference must be applied to the shortlisted candidate');
  });
});

test('missing endpoint, HTTP errors, invalid replies and timeouts fall back to the exact base ranking', async () => {
  const base = await new LexicalLanguageCore().rank('slug from title', candidates);
  const none = new LocalModelLanguageCore({endpoint: undefined});
  assert.deepEqual(await none.rank('slug from title', candidates), base);
  assert.equal(none.last.reason, 'NO_ENDPOINT');
  await withServer((req, res) => { res.statusCode = 500; res.end('{}'); }, async url => {
    const core = new LocalModelLanguageCore({endpoint: url});
    assert.deepEqual(await core.rank('slug from title', candidates), base);
    assert.equal(core.last.reason, 'HTTP_500');
  });
  await withServer(reply('I think slugify is best.'), async url => {
    const core = new LocalModelLanguageCore({endpoint: url});
    assert.deepEqual(await core.rank('slug from title', candidates), base);
    assert.equal(core.last.reason, 'NO_JSON');
  });
  await withServer(() => {}, async url => {
    const core = new LocalModelLanguageCore({endpoint: url, timeoutMs: 150});
    assert.deepEqual(await core.rank('slug from title', candidates), base);
    assert.equal(core.last.reason, 'TIMEOUT');
  });
});
