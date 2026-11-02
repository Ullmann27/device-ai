/**
 * Android-safe, write-once storage of independently checked ANSK shadow recipes.
 *
 * Android app-private filesystems can reject hardlink(2) with EACCES.
 * Use an exclusively created destination instead. COPYFILE_EXCL refuses to
 * overwrite existing data, and the destination is reread through O_NOFOLLOW
 * before a receipt is advertised. No recipe grants a production write.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {verifyAnskRecipeReceipt} from './ansk-lab-contract.mjs';

const MAX_RECEIPT_BYTES = 16_384;
const O_READ_NOFOLLOW = fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW;

/** Read a single receipt by content address; no arbitrary external JSON path. */
export function readImmutableAnskRecipe(store, recipeSha256) {
  if (typeof store !== 'string' || !path.isAbsolute(store) ||
      !/^[0-9a-f]{64}$/.test(recipeSha256 ?? ''))
    throw Error('ANSK_INVALID_RECIPE_ADDRESS');
  const dir = path.join(fs.realpathSync(store), 'shadow-recipes');
  const info = fs.lstatSync(dir);
  if (!info.isDirectory() || info.isSymbolicLink())
    throw Error('ANSK_UNSAFE_RECIPE_DIRECTORY');
  const fd = fs.openSync(path.join(dir, recipeSha256 + '.json'), O_READ_NOFOLLOW);
  try {
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || stat.size > MAX_RECEIPT_BYTES || (stat.mode & 0o077) !== 0)
      throw Error('ANSK_UNSAFE_RECIPE_FILE');
    const recipe = JSON.parse(fs.readFileSync(fd, 'utf8'));
    if (!verifyAnskRecipeReceipt(recipe).valid || recipe.recipeSha256 !== recipeSha256)
      throw Error('ANSK_INVALID_RECIPE_RECEIPT');
    return recipe;
  } finally {
    fs.closeSync(fd);
  }
}

/** Same write-once mechanism, kept separate from production experience stores. */
export function writeImmutableAnskTransfer(store, report) {
  if (!report || report.schema !== 'continuum-ansk-transfer' ||
      report.version !== 1 || report.authority !== 'SHADOW_ONLY' ||
      report.modelTrainingAuthorized !== false || report.productionWriteAuthorized !== false)
    throw Error('ANSK_INVALID_TRANSFER_REPORT');
  const bytes = JSON.stringify(report, null, 2) + '\n';
  if (Buffer.byteLength(bytes) > 128_000) throw Error('ANSK_TRANSFER_REPORT_TOO_LARGE');
  const digest = crypto.createHash('sha256').update(bytes).digest('hex');
  const dir = path.join(fs.realpathSync(store), 'shadow-transfers');
  fs.mkdirSync(dir, {recursive: true, mode: 0o700});
  if (!fs.lstatSync(dir).isDirectory() || fs.lstatSync(dir).isSymbolicLink())
    throw Error('ANSK_UNSAFE_TRANSFER_DIRECTORY');
  const destination = path.join(dir, digest + '.json');
  const temporary = path.join(dir, '.' + crypto.randomBytes(12).toString('hex') + '.tmp');
  try {
    const fd = fs.openSync(temporary, 'wx', 0o600);
    try { fs.writeFileSync(fd, bytes); fs.fsyncSync(fd); }
    finally { fs.closeSync(fd); }
    try { fs.copyFileSync(temporary, destination, fs.constants.COPYFILE_EXCL); }
    catch (error) { if (error.code !== 'EEXIST') throw error; }
    verifiedExistingReceipt(destination, bytes);
    return {reportSha256: digest, reportPath: destination};
  } finally {
    fs.rmSync(temporary, {force: true});
  }
}

function verifiedExistingReceipt(file, expected) {
  const fd = fs.openSync(file, O_READ_NOFOLLOW);
  try {
    const info = fs.fstatSync(fd);
    if (!info.isFile() || info.size !== Buffer.byteLength(expected) ||
        (info.mode & 0o077) !== 0)
      throw Error('ANSK_RECIPE_HASH_COLLISION_OR_TAMPERING');
    const actual = fs.readFileSync(fd, 'utf8');
    if (actual !== expected)
      throw Error('ANSK_RECIPE_HASH_COLLISION_OR_TAMPERING');
  } finally {
    fs.closeSync(fd);
  }
}

/**
 * No hardlinks, no overwrites, no trust promotion.
 * The copy itself is not guaranteed atomic on every filesystem. Before
 * returning success, verify the entire target; partial/corrupt copies are
 * never accepted and require explicit operator review.
 */
export function writeImmutableAnskRecipe(store, recipe) {
  if (typeof store !== 'string' || !path.isAbsolute(store))
    throw Error('ANSK_INVALID_RECIPE_STORE');
  const validation = verifyAnskRecipeReceipt(recipe);
  if (!validation.valid)
    throw Error('ANSK_INVALID_RECIPE_RECEIPT: ' + validation.reason);

  const json = JSON.stringify(recipe, null, 2) + '\n';
  if (Buffer.byteLength(json) > MAX_RECEIPT_BYTES)
    throw Error('ANSK_RECIPE_RECEIPT_TOO_LARGE');

  const storeDir = fs.realpathSync(store);
  if (!fs.statSync(storeDir).isDirectory())
    throw Error('ANSK_INVALID_RECIPE_STORE');
  const dir = path.join(storeDir, 'shadow-recipes');
  fs.mkdirSync(dir, {recursive: true, mode: 0o700});
  const folder = fs.lstatSync(dir);
  if (!folder.isDirectory() || folder.isSymbolicLink())
    throw Error('ANSK_UNSAFE_RECIPE_DIRECTORY');
  const target = path.join(dir, recipe.recipeSha256 + '.json');
  const temporary = path.join(dir, '.' + crypto.randomBytes(12).toString('hex') + '.tmp');

  try {
    const fd = fs.openSync(temporary, 'wx', 0o600);
    try {
      fs.writeFileSync(fd, json, 'utf8');
      fs.fsyncSync(fd);
    } finally {
      fs.closeSync(fd);
    }
    try {
      fs.copyFileSync(temporary, target, fs.constants.COPYFILE_EXCL);
    } catch (error) {
      if (error.code !== 'EEXIST') throw error;
    }
    verifiedExistingReceipt(target, json);
    return target;
  } finally {
    fs.rmSync(temporary, {force: true});
  }
}
