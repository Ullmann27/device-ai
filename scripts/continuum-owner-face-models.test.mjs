import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {matchesPin, modelUrl, prepareOwnerFaceModels, validateManifest} from './continuum-owner-face-models.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const production = JSON.parse(await fs.readFile(path.join(root, 'app/owner-face-models.lock.json'), 'utf8'));
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

async function fixture(t) {
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'continuum-face-model-test-'));
  t.after(() => fs.rm(temp, {recursive: true, force: true}));
  const manifest = structuredClone(production);
  const contents = new Map();
  for (const [index, model] of manifest.models.entries()) {
    const bytes = Buffer.alloc(2048 + index, 31 + index);
    model.byteLength = bytes.length;
    model.sha256 = sha(bytes);
    contents.set(modelUrl(manifest, model), bytes);
  }
  const aar = Buffer.alloc(3072, 63);
  manifest.opencv.byteLength = aar.length;
  manifest.opencv.sha256 = sha(aar);
  const opencvAar = path.join(temp, 'opencv.aar');
  await fs.writeFile(opencvAar, aar);
  const calls = [];
  const transfer = async (url, destination, expectedBytes) => {
    calls.push(url);
    assert.equal(contents.get(url).length, expectedBytes);
    await fs.writeFile(destination, contents.get(url));
  };
  return {manifest, out: path.join(temp, 'generated'), cache: path.join(temp, 'cache'),
    opencvAar, transfer, calls, contents, temp};
}

test('production pins use immutable official URLs and source contains no weights', async () => {
  validateManifest(production);
  for (const model of production.models) {
    assert.match(modelUrl(production, model), /^https:\/\/media\.githubusercontent\.com\/media\/opencv\/opencv_zoo\/[a-f0-9]{40}\/models\//);
    await assert.rejects(fs.stat(path.join(root, 'app/src/main/assets/faceid', model.filename)), {code: 'ENOENT'});
  }
  const licenses = [
    ['LICENSE-YUNET.txt', 'c83b8120c50ccbd4c4f96edf53141bdd566ebb8f8e9227e415326aa1b1aba958'],
    ['LICENSE-SFACE.txt', 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30'],
    ['LICENSE-OPENCV.txt', 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30'],
  ];
  for (const [file, digest] of licenses)
    assert.equal(sha(await fs.readFile(path.join(root, 'app/src/main/assets/faceid', file))), digest);
});

test('both verified models and provenance are staged; cache reuse is offline', async t => {
  const f = await fixture(t);
  const result = await prepareOwnerFaceModels(f);
  assert.equal(f.calls.length, 2);
  assert.equal(result.models.length, 2);
  for (const model of f.manifest.models)
    assert.equal(await matchesPin(path.join(f.out, 'faceid', model.filename), model), true);
  await prepareOwnerFaceModels({...f, transfer: () => assert.fail('Cache must avoid a download')});
  assert.deepEqual(JSON.parse(await fs.readFile(path.join(f.out, 'faceid/model-manifest.json'), 'utf8')), f.manifest);
  assert.equal((await fs.readdir(path.join(f.out, 'faceid'))).length, 3);
});

test('same-size model corruption blocks all output publication', async t => {
  const f = await fixture(t);
  f.transfer = async (_url, destination, count) => fs.writeFile(destination, Buffer.alloc(count, 0));
  await assert.rejects(prepareOwnerFaceModels(f), /MODEL_DOWNLOAD_PIN_MISMATCH/);
  assert.deepEqual(await fs.readdir(path.join(f.out, 'faceid')), []);
  assert.deepEqual(await fs.readdir(f.cache), []);
});

test('truncated model blocks packaging', async t => {
  const f = await fixture(t);
  f.transfer = async (_url, destination) => fs.writeFile(destination, 'partial');
  await assert.rejects(prepareOwnerFaceModels(f), /MODEL_DOWNLOAD_PIN_MISMATCH/);
  assert.deepEqual(await fs.readdir(path.join(f.out, 'faceid')), []);
});

test('a failed second model never stages the first model alone', async t => {
  const f = await fixture(t);
  const first = f.transfer;
  f.transfer = async (url, destination, count) => {
    if (url.includes('sface')) throw Error('Interrupted transfer');
    return first(url, destination, count);
  };
  await assert.rejects(prepareOwnerFaceModels(f), /Interrupted transfer/);
  assert.deepEqual(await fs.readdir(path.join(f.out, 'faceid')), []);
});

test('tampered OpenCV AAR blocks model requests', async t => {
  const f = await fixture(t);
  await fs.writeFile(f.opencvAar, Buffer.alloc(f.manifest.opencv.byteLength, 2));
  await assert.rejects(prepareOwnerFaceModels(f), /OPENCV_AAR_PIN_MISMATCH/);
  assert.equal(f.calls.length, 0);
});

test('corrupted cache is repaired only from verified bytes', async t => {
  const f = await fixture(t);
  await prepareOwnerFaceModels(f);
  const model = f.manifest.models[0];
  const cached = path.join(f.cache, `${model.sha256}-${model.filename}`);
  await fs.chmod(cached, 0o644);
  await fs.writeFile(cached, Buffer.alloc(model.byteLength, 0));
  f.calls.length = 0;
  await prepareOwnerFaceModels(f);
  assert.equal(f.calls.length, 1);
  assert.equal(await matchesPin(cached, model), true);
});

test('cache symlinks fail before writing their targets', async t => {
  const f = await fixture(t);
  await fs.mkdir(f.cache);
  const model = f.manifest.models[0];
  const target = path.join(f.temp, 'unrelated.txt');
  await fs.writeFile(target, 'preserve');
  await fs.symlink(target, path.join(f.cache, `${model.sha256}-${model.filename}`));
  await assert.rejects(prepareOwnerFaceModels(f), /UNSAFE_ARTIFACT_PATH/);
  assert.equal(await fs.readFile(target, 'utf8'), 'preserve');
  assert.equal(f.calls.length, 0);
});

test('unexpected generated assets are never silently packaged', async t => {
  const f = await fixture(t);
  await fs.mkdir(path.join(f.out, 'faceid'), {recursive: true});
  await fs.writeFile(path.join(f.out, 'faceid/stale.onnx'), 'old');
  await assert.rejects(prepareOwnerFaceModels(f), /UNEXPECTED_GENERATED_FACE_ASSET/);
  assert.equal(f.calls.length, 0);
});

test('cache and generated output cannot overlap', async t => {
  const f = await fixture(t);
  await assert.rejects(prepareOwnerFaceModels({...f, cache: f.out}), /OUTPUT_CACHE_OVERLAP/);
});

test('manifest rejects mutable refs, traversal, duplicate models and unbounded sizes', () => {
  for (const mutate of [
    m => { m.commit = 'main'; },
    m => { m.repository = 'other/repo'; },
    m => { m.models[0].filename = '../outside.onnx'; },
    m => { m.models[1] = {...m.models[0]}; },
    m => { m.models[0].byteLength = 1_000_000_000; },
    m => { m.models[0].sha256 = ''; },
    m => { m.opencv.coordinate = 'org.opencv:opencv:+'; },
  ]) {
    const value = structuredClone(production);
    mutate(value);
    assert.throws(() => validateManifest(value), /INVALID_/);
  }
});
