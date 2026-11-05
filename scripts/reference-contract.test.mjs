import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { verifyReference } from './reference-contract.mjs';

const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGNkePqfgYGBiYGBgYGBAQAV2wHorouCxQAAAABJRU5ErkJggg==', 'base64');
const sha256 = createHash('sha256').update(PNG).digest('hex');

async function fixture(t, patch = {}, bytes = PNG) {
  const dir = await mkdtemp(join(tmpdir(), 'continuum-reference-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  const image = join(dir, '59509.png');
  const manifest = join(dir, 'binding.json');
  await writeFile(image, bytes);
  await writeFile(manifest, JSON.stringify({schema:'continuum-reference-binding-v1', width:2, height:2, sha256, ...patch}));
  return {image, manifest};
}

test('exact approved bytes and dimensions are bound', async t => {
  const {image, manifest} = await fixture(t);
  assert.deepEqual(await verifyReference(image, manifest), {
    schema:'continuum-reference-binding-v1', status:'BOUND', width:2, height:2, sha256
  });
});

test('same filename and dimensions cannot bypass pin', async t => {
  const corrupted = Buffer.from(PNG); corrupted[42] ^= 1;
  const f = await fixture(t, {}, corrupted);
  await assert.rejects(verifyReference(f.image, f.manifest), /REFERENCE_SHA256_MISMATCH/);
});

test('same filename and hash cannot bypass wrong reference dimensions', async t => {
  const f = await fixture(t, {width:1536, height:1024});
  await assert.rejects(verifyReference(f.image, f.manifest), /REFERENCE_GEOMETRY_MISMATCH: expected 1536x1024, actual 2x2/);
});

test('invalid contract schema and hash are rejected', async t => {
  for (const patch of [{schema:'v0'},{sha256:'cafe'},{sha256:sha256.toUpperCase()}]) {
    const f = await fixture(t, patch);
    await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_REFERENCE_CONTRACT/);
  }
});

test('invalid contract dimensions are rejected', async t => {
  for (const value of [0, -1, 1.5, '2', null]) {
    const f = await fixture(t, {width:value});
    await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_REFERENCE_CONTRACT/);
  }
});

test('invalid PNG header is rejected', async t => {
  const wrong = Buffer.from(PNG); wrong[0] = 0;
  const f = await fixture(t, {}, wrong);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_HEADER/);
});

test('misnamed JPEG cannot masquerade as approved PNG', async t => {
  const fake = Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), Buffer.alloc(50)]);
  const f = await fixture(t, {}, fake);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_HEADER/);
});

test('truncated PNG is rejected', async t => {
  const f = await fixture(t, {}, PNG.subarray(0, 20));
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_REFERENCE_SIZE/);
});

test('CLI uses nonzero exit on mismatches, succeeds when bound', async t => {
  const f = await fixture(t);
  const cmd = new URL('./reference-contract.mjs', import.meta.url).pathname;
  const yes = spawnSync(process.execPath, [cmd, f.image, f.manifest], {encoding:'utf8'});
  assert.equal(yes.status, 0); assert.equal(JSON.parse(yes.stdout).status, 'BOUND');
  const bad = await fixture(t, {width:1536});
  const no = spawnSync(process.execPath, [cmd, bad.image, bad.manifest], {encoding:'utf8'});
  assert.equal(no.status, 1); assert.match(no.stderr, /REFERENCE_GEOMETRY_MISMATCH/);
});

test('validly pinned PNG with damaged IEND CRC is rejected', async t => {
  const bytes = Buffer.from(PNG); bytes[bytes.length - 1] ^= 1;
  const f = await fixture(t, {sha256:createHash('sha256').update(bytes).digest('hex')}, bytes);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_STRUCTURE/);
});

test('validly pinned PNG without final IEND is rejected', async t => {
  const bytes = PNG.subarray(0, PNG.length - 12);
  const f = await fixture(t, {sha256:createHash('sha256').update(bytes).digest('hex')}, bytes);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_STRUCTURE/);
});

test('validly pinned PNG with appended data is rejected', async t => {
  const bytes = Buffer.concat([PNG, Buffer.from('EXTRA')]);
  const f = await fixture(t, {sha256:createHash('sha256').update(bytes).digest('hex')}, bytes);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_STRUCTURE/);
});

test('validly pinned PNG with corrupted IDAT CRC is rejected', async t => {
  const bytes = Buffer.from(PNG); bytes[61] ^= 1;
  const f = await fixture(t, {sha256:createHash('sha256').update(bytes).digest('hex')}, bytes);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_STRUCTURE/);
});

test('validly pinned PNG without IDAT is rejected', async t => {
  const bytes = Buffer.concat([PNG.subarray(0, 33), PNG.subarray(PNG.length-12)]);
  const f = await fixture(t, {sha256:createHash('sha256').update(bytes).digest('hex')}, bytes);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_STRUCTURE/);
});

test('validly pinned PNG with oversized chunk length is rejected', async t => {
  const bytes = Buffer.from(PNG); bytes.writeUInt32BE(2147483647, 33);
  const f = await fixture(t, {sha256:createHash('sha256').update(bytes).digest('hex')}, bytes);
  await assert.rejects(verifyReference(f.image, f.manifest), /INVALID_PNG_STRUCTURE/);
});
