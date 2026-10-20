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
