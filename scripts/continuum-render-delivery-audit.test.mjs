import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { auditPublishedApk, validateOptions } from './continuum-render-delivery-audit.mjs';

const origin = 'https://device-ai-mentor-apk-preview.onrender.com';
const apkBytes = Buffer.from('synthetic APK fixture only - not an installable package');
const sha256 = createHash('sha256').update(apkBytes).digest('hex');
const commit = '17e22bc2d9e1851fd634f44b8aadc04479556983';
const version = '0.3.11';
const packageName = 'at.heinz.deviceai.mentorpaired';
const branch = 'astra/render-mentor-apk-builder-20261019';
const artifact = `DeviceAI-Continuum-v${version}-${sha256}.apk`;
const config = { origin, sha256, commit, version, packageName, branch, artifact };
const sums = `${sha256}  ${artifact}\n`;
const info = `commit=${commit}\nbranch=${branch}\npackage=${packageName}\nartifact=${artifact}\napk_sha256=${sha256}\n`;
const makeFetch = (overrides = {}) => async (url, options) => {
  assert.equal(options.redirect, 'manual');
  assert.equal(url.origin, origin);
  const content = {
    [`SHA256SUMS-${sha256}.txt`]: sums,
    [`BUILD-INFO-${sha256}.txt`]: info,
    [artifact]: apkBytes,
    ...overrides,
  }[url.pathname.slice(1)];
  return content === undefined ? new Response('missing', { status: 404 }) :
    content instanceof Response ? content : new Response(content, { status: 200 });
};

const run = (opts = config, replacements = {}) => auditPublishedApk(opts, { fetchImpl: makeFetch(replacements) });

test('known published artifact matches independent hash, commit, branch and package', async () => {
  const result = await run();
  assert.equal(result.status, 'DELIVERY_SHA256_VERIFIED');
  assert.equal(result.apkSha256, sha256);
  assert.equal(result.apkBytes, apkBytes.length);
  assert.equal(result.signatureVerified, false);
  assert.equal(result.installedOnDevice, false);
  assert.equal(result.playStoreReady, false);
});

test('refuses unapproved origin and all unpinned or malformed release selectors', () => {
  for (const change of [
    {origin: 'http://device-ai-mentor-apk-preview.onrender.com'},
    {origin: 'https://evil.example.com'},
    {sha256: '0'.repeat(64)},
    {commit: 'abcd'},
    {version: '../latest'},
    {packageName: 'unsafe/.app'},
    {artifact: 'file.apk'},
    {branch: 'main..evil'},
  ]) {
    assert.throws(() => validateOptions({...config, ...change}));
  }
});

test('rejects stale or forged SHA256SUMS before fetching APK', async () => {
  await assert.rejects(run(config, {[`SHA256SUMS-${sha256}.txt`]: `0`.repeat(64) + `  ${artifact}\n`}), /SHA256SUMS/);
  await assert.rejects(run(config, {[`SHA256SUMS-${sha256}.txt`]: `${sha256}  bad.apk\n`}), /SHA256SUMS/);
});

test('rejects mismatched source commit, package, branch and duplicate metadata', async () => {
  for (const bad of [
    info.replace(commit, '0'.repeat(40)),
    info.replace(packageName, 'at.heinz.deviceai.preview'),
    info.replace(branch, 'main'),
    `${info}package=${packageName}\n`,
  ]) {
    await assert.rejects(run(config, {[`BUILD-INFO-${sha256}.txt`]: bad}));
  }
});

test('catches CDN stale APK content independently of checksum metadata', async () => {
  await assert.rejects(run(config, {[artifact]: Buffer.from('wrong apk')}), /APK bytes/);
});

test('refuses redirects, errors, and missing metadata', async () => {
  await assert.rejects(run(config, {[artifact]: new Response(null, { status: 302, headers: { location: 'https://elsewhere.test' } })}), /HTTP 302/);
  await assert.rejects(run(config, {[`BUILD-INFO-${sha256}.txt`]: undefined}), /HTTP 404/);
});

test('refuses metadata with an oversized declared byte length', async () => {
  const bad = new Response('data', {status: 200, headers: {'content-length': `${33 * 1024}`} });
  await assert.rejects(run(config, {[`BUILD-INFO-${sha256}.txt`]: bad}), /over budget/);
});

test('rejects truncated or zero-byte published APK', async () => {
  await assert.rejects(run(config, {[artifact]: new Response('x', {status: 200, headers: {'content-length':'55'}})}), /truncated/);
  await assert.rejects(run(config, {[artifact]: new Response(null, {status: 200})}), /response rejected/);
});
