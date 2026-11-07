import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const project = fileURLToPath(new URL('../', import.meta.url));
const known = new Map([
  ['yunet', ['face_detection_yunet', 'face_detection_yunet_2023mar.onnx', 'MIT']],
  ['sface', ['face_recognition_sface', 'face_recognition_sface_2021dec.onnx', 'Apache-2.0']],
]);
const digestPattern = /^[a-f0-9]{64}$/;
const within = (child, parent) => child === parent || child.startsWith(parent + path.sep);
const fail = code => { throw new Error(code); };

export function validateManifest(value) {
  if (!value || value.schema !== 1 || value.repository !== 'opencv/opencv_zoo' ||
      !/^[a-f0-9]{40}$/.test(value.commit || '') || !Array.isArray(value.models) ||
      value.models.length !== 2) fail('INVALID_MODEL_MANIFEST');
  const cv = value.opencv;
  if (cv?.coordinate !== 'org.opencv:opencv:4.13.0' || cv.license !== 'Apache-2.0' ||
      !digestPattern.test(cv.sha256 || '') || !Number.isSafeInteger(cv.byteLength) ||
      cv.byteLength < 1024 || cv.byteLength > 250_000_000) fail('INVALID_OPENCV_PIN');
  const ids = new Set();
  for (const model of value.models) {
    const expected = known.get(model?.id);
    if (!expected || ids.has(model.id) || model.directory !== expected[0] ||
        model.filename !== expected[1] || model.license !== expected[2] ||
        !digestPattern.test(model.sha256 || '') || !Number.isSafeInteger(model.byteLength) ||
        model.byteLength < 1024 || model.byteLength > 100_000_000) fail('INVALID_MODEL_PIN');
    ids.add(model.id);
  }
  return value;
}

export function modelUrl(manifest, model) {
  return `https://media.githubusercontent.com/media/opencv/opencv_zoo/${manifest.commit}/models/${model.directory}/${model.filename}`;
}

async function regularFile(file) {
  try {
    const stat = await fsp.lstat(file);
    if (!stat.isFile() || stat.isSymbolicLink()) fail('UNSAFE_ARTIFACT_PATH');
    return stat;
  } catch (error) {
    if (error.code === 'ENOENT') return null;
    throw error;
  }
}

export async function matchesPin(file, pin) {
  const stat = await regularFile(file);
  if (!stat || stat.size !== pin.byteLength) return false;
  const hash = crypto.createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex') === pin.sha256;
}

async function directory(dir) {
  await fsp.mkdir(dir, {recursive: true, mode: 0o700});
  const stat = await fsp.lstat(dir);
  if (!stat.isDirectory() || stat.isSymbolicLink()) fail('UNSAFE_ARTIFACT_DIRECTORY');
}

function download(url, destination, expectedBytes) {
  const result = spawnSync('curl', [
    '--fail', '--location', '--silent', '--show-error', '--proto', '=https',
    '--proto-redir', '=https', '--max-redirs', '3', '--connect-timeout', '20',
    '--max-time', '180', '--max-filesize', String(expectedBytes),
    '--output', destination, url,
  ], {stdio: ['ignore', 'ignore', 'pipe'], timeout: 190_000, maxBuffer: 8192});
  if (result.error || result.status !== 0) fail('MODEL_DOWNLOAD_FAILED');
}

async function cachedModel(manifest, model, cache, transfer) {
  const target = path.join(cache, `${model.sha256}-${model.filename}`);
  if (await matchesPin(target, model)) return target;
  const temp = await fsp.mkdtemp(path.join(cache, 'download-'));
  try {
    const candidate = path.join(temp, model.filename);
    await transfer(modelUrl(manifest, model), candidate, model.byteLength);
    if (!await matchesPin(candidate, model)) fail('MODEL_DOWNLOAD_PIN_MISMATCH');
    await regularFile(target);
    await fsp.chmod(candidate, 0o444);
    await fsp.rename(candidate, target);
    return target;
  } finally {
    await fsp.rm(temp, {recursive: true, force: true});
  }
}

async function stageFile(source, destination, pin) {
  await regularFile(destination);
  const temp = destination + '.tmp-' + crypto.randomUUID();
  try {
    await fsp.copyFile(source, temp, fs.constants.COPYFILE_EXCL);
    if (!await matchesPin(temp, pin)) fail('STAGED_MODEL_PIN_MISMATCH');
    await fsp.rename(temp, destination);
  } finally {
    await fsp.rm(temp, {force: true});
  }
}

export async function prepareOwnerFaceModels({manifest, out, cache, opencvAar, transfer = download}) {
  validateManifest(manifest);
  if (!await matchesPin(opencvAar, manifest.opencv)) fail('OPENCV_AAR_PIN_MISMATCH');
  const output = path.resolve(out);
  const cacheDir = path.resolve(cache);
  if (within(output, cacheDir) || within(cacheDir, output)) fail('OUTPUT_CACHE_OVERLAP');
  await directory(output);
  await directory(cacheDir);
  const assets = path.join(output, 'faceid');
  await directory(assets);
  const expectedAssets = new Set([...manifest.models.map(model => model.filename), 'model-manifest.json']);
  for (const name of await fsp.readdir(assets)) {
    if (!expectedAssets.has(name)) fail('UNEXPECTED_GENERATED_FACE_ASSET');
    await regularFile(path.join(assets, name));
  }
  const ready = [];
  for (const model of manifest.models) ready.push(await cachedModel(manifest, model, cacheDir, transfer));
  for (let i = 0; i < manifest.models.length; i++) {
    const model = manifest.models[i];
    await stageFile(ready[i], path.join(assets, model.filename), model);
  }
  const provenance = path.join(assets, 'model-manifest.json');
  await regularFile(provenance);
  const tempManifest = provenance + '.tmp-' + crypto.randomUUID();
  try {
    await fsp.writeFile(tempManifest, JSON.stringify(manifest, null, 2) + '\n', {flag: 'wx', mode: 0o444});
    await fsp.rename(tempManifest, provenance);
  } finally {
    await fsp.rm(tempManifest, {force: true});
  }
  return {models: manifest.models.map(({id, filename, sha256, byteLength}) =>
    ({id, asset: 'faceid/' + filename, sha256, byteLength})), opencvSha256: manifest.opencv.sha256};
}

async function main(args) {
  const keys = new Set(['--manifest', '--out', '--cache', '--opencv-aar']);
  if (args.length !== 8) fail('INVALID_MODEL_PREPARE_ARGUMENTS');
  const values = {};
  for (let i = 0; i < args.length; i += 2) {
    if (!keys.has(args[i]) || values[args[i]] || !args[i + 1]) fail('INVALID_MODEL_PREPARE_ARGUMENTS');
    values[args[i]] = path.resolve(args[i + 1]);
  }
  const root = path.resolve(project);
  for (const key of ['--out', '--cache']) {
    if (within(values[key], root) && !within(values[key], path.join(root, 'app', 'build')) &&
        !within(values[key], path.join(root, '.continuum-render-build')) &&
        !within(values[key], path.join(root, '.gradle'))) fail('MODEL_OUTPUT_IN_SOURCE');
  }
  const manifest = validateManifest(JSON.parse(await fsp.readFile(values['--manifest'], 'utf8')));
  const result = await prepareOwnerFaceModels({manifest, out: values['--out'], cache: values['--cache'],
    opencvAar: values['--opencv-aar']});
  process.stdout.write('CONTINUUM_OWNER_FACE_MODELS_VERIFIED ' + JSON.stringify(result) + '\n');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(error => {
    process.stderr.write('CONTINUUM_OWNER_FACE_MODELS_BLOCKED ' + error.message + '\n');
    process.exitCode = 1;
  });
}
