import { readFile, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PNG_SIGNATURE = Buffer.from('89504e470d0a1a0a', 'hex');
const MAX_REFERENCE_BYTES = 128 * 1024 * 1024;
const SHA256 = /^[0-9a-f]{64}$/;

const CRC_TABLE = Uint32Array.from({length: 256}, (_, value) => {
  let result = value;
  for (let bit = 0; bit < 8; bit++) {
    result = result & 1 ? 0xedb88320 ^ (result >>> 1) : result >>> 1;
  }
  return result >>> 0;
});

function chunkCrc(png, first, last) {
  let crc = 0xffffffff;
  for (let offset = first; offset < last; offset++) {
    crc = CRC_TABLE[(crc ^ png[offset]) & 255] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function assertPngStructure(png) {
  let offset = PNG_SIGNATURE.length;
  let chunkIndex = 0;
  let sawIdat = false;
  let idatClosed = false;
  while (offset < png.length) {
    if (png.length - offset < 12) throw new Error('INVALID_PNG_STRUCTURE');
    const size = png.readUInt32BE(offset);
    if (size > png.length - offset - 12) throw new Error('INVALID_PNG_STRUCTURE');
    const type = png.toString('ascii', offset + 4, offset + 8);
    if (!/^[A-Za-z]{4}$/.test(type)) throw new Error('INVALID_PNG_STRUCTURE');
    const end = offset + 8 + size;
    if (png.readUInt32BE(end) !== chunkCrc(png, offset + 4, end)) {
      throw new Error('INVALID_PNG_STRUCTURE');
    }
    if (chunkIndex === 0) {
      const depth = png[24];
      const color = png[25];
      const legalDepths = {0:[1,2,4,8,16],2:[8,16],3:[1,2,4,8],4:[8,16],6:[8,16]};
      if (type !== 'IHDR' || size !== 13 ||
          !legalDepths[color]?.includes(depth) || png[26] !== 0 ||
          png[27] !== 0 || ![0,1].includes(png[28])) {
        throw new Error('INVALID_PNG_STRUCTURE');
      }
    } else if (type === 'IHDR') {
      throw new Error('INVALID_PNG_STRUCTURE');
    }
    if (type === 'IDAT') {
      if (idatClosed) throw new Error('INVALID_PNG_STRUCTURE');
      sawIdat = true;
    } else if (sawIdat) {
      idatClosed = true;
    }
    if (type === 'IEND') {
      if (size !== 0 || !sawIdat || end + 4 !== png.length) {
        throw new Error('INVALID_PNG_STRUCTURE');
      }
      return;
    }
    offset = end + 4;
    chunkIndex++;
  }
  throw new Error('INVALID_PNG_STRUCTURE');
}

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
  assertPngStructure(png);
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
