import { readFile, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PNG_SIGNATURE = Buffer.from('89504e470d0a1a0a', 'hex');
const MAX_REFERENCE_BYTES = 128 * 1024 * 1024;
const SHA256 = /^[0-9a-f]{64}$/;

export async function verifyReference(imagePath, contractPath) {
  const contract = JSON.parse(await readFile(contractPath, 'utf8'));
  if (contract?.schema !== 'continuum-reference-binding-v1' ||
      !SHA256.test(contract.sha256 ?? '') ||
      !Number.isSafeInteger(contract.width) || contract.width <= 0 ||
      !Number.isSafeInteger(contract.height) || contract.height <= 0) {
    throw new Error('INVALID_REFERENCE_CONTRACT');
  }
  const { size } = await stat(imagePath);
  if (size < 33 || size > MAX_REFERENCE_BYTES) throw new Error('INVALID_REFERENCE_SIZE');
  const png = await readFile(imagePath);
  if (!png.subarray(0, 8).equals(PNG_SIGNATURE) ||
      png.readUInt32BE(8) !== 13 || png.toString('ascii', 12, 16) !== 'IHDR') {
    throw new Error('INVALID_PNG_HEADER');
  }
  const width = png.readUInt32BE(16);
  const height = png.readUInt32BE(20);
  const sha256 = createHash('sha256').update(png).digest('hex');
  if (width !== contract.width || height !== contract.height) {
    throw new Error('REFERENCE_GEOMETRY_MISMATCH: expected ' + contract.width + 'x' + contract.height + ', actual ' + width + 'x' + height);
  }
  if (sha256 !== contract.sha256) throw new Error('REFERENCE_SHA256_MISMATCH');
  return { schema: contract.schema, status: 'BOUND', width, height, sha256 };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 4) {
    process.stderr.write('Usage: node reference-contract.mjs IMAGE.png CONTRACT.json\n');
    process.exitCode = 2;
  } else {
    try {
      const result = await verifyReference(process.argv[2], process.argv[3]);
      process.stdout.write(JSON.stringify(result) + '\n');
    } catch (error) {
      process.stderr.write(error.message + '\n');
      process.exitCode = 1;
    }
  }
}
