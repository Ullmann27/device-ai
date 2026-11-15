import { readFile, access } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const originalHash = 'c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a';
const model = await readFile(resolve(root, 'assets/continuum-avatar.glb'));
if (model.length !== 12038416 || createHash('sha256').update(model).digest('hex') !== originalHash) {
  throw new Error('Der Kopf stimmt nicht mit dem freigegebenen GitHub-Original überein. Bitte die Originaldatei aus dem vollständigen Projektpaket wiederherstellen.');
}
for (const file of [
  'assets/continuum-avatar-original.png',
  'assets/continuum-orb-original.png',
  'assets/continuum-avatar-NOTICE.json',
  'assets/LICENSE.ASSETS.md',
  'assets/fonts/manrope-0.ttf',
  'assets/fonts/manrope-1.ttf',
  'assets/fonts/manrope-2.ttf',
  'assets/fonts/manrope-3.ttf',
  'assets/fonts/space-grotesk-4.ttf',
  'assets/fonts/space-grotesk-5.ttf',
  'assets/fonts/space-grotesk-6.ttf',
]) await access(resolve(root, file));
process.stdout.write('Originalmodell und lokale Darstellungsdateien geprüft.\n');
