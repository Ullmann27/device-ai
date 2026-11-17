import { createHash } from 'node:crypto';
import { readFileSync, lstatSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname, sep } from 'node:path';

/** Read-only verification of pinned upstream research snapshots. Never executes vendored code. */
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const manifestFile = resolve(repoRoot, 'docs/research/quantumflow/UPSTREAM-PROVENANCE.json');
const manifest = JSON.parse(readFileSync(manifestFile, 'utf8'));
if (manifest.schema !== 'CONTINUUM_QUANTUMFLOW_UPSTREAM_V1' ||
    !Array.isArray(manifest.sources) || manifest.sources.length === 0)
  throw Error('Unexpected or empty Quantumflow manifest');
const prefix = resolve(repoRoot, 'docs/evidence/quantumflow-20261102/upstream') + sep;
const seen = new Set();
let verified = 0;
for (const source of manifest.sources) {
  if (!/^[a-f0-9]{40}$/.test(source.commit) || !Array.isArray(source.source_files))
    throw Error('Upstream commit/source_files missing');
  if (!/^(MIT|Apache-2\.0)$/.test(source.license))
    throw Error('Unexpected upstream license; review required: ' + source.repo);
  for (const item of source.source_files) {
    const rel = item.snapshot_path;
    if (typeof rel !== 'string' ||
        !rel.startsWith('docs/evidence/quantumflow-20261102/upstream/'))
      throw Error('Snapshot must remain under evidence directory');
    const path = resolve(repoRoot, rel);
    if (!path.startsWith(prefix) || seen.has(path))
      throw Error('Snapshot path traversal or duplicate');
    seen.add(path);
    if (!/^[a-f0-9]{40}$/.test(item.git_blob_sha1))
      throw Error('Missing pinned original Git blob hash');
    const stat = lstatSync(path);
    if (!stat.isFile() || stat.isSymbolicLink() ||
        stat.size <= 0 || stat.size > 250000)
      throw Error('Invalid snapshot file');
    const bytes = readFileSync(path);
    const digest = createHash('sha1').update('blob ' + bytes.length + '\0')
      .update(bytes).digest('hex');
    if (digest !== item.git_blob_sha1)
      throw Error('Upstream modified or corrupted: ' + rel);
    verified++;
  }
}
if (!verified) throw Error('No upstream snapshots verified');
console.log('QUANTUMFLOW_UPSTREAM_SOURCE_VERIFIED ' + verified +
            ' files across ' + manifest.sources.length + ' repositories');
console.log('AUDIT_ONLY; NO_ANDROID_RUNTIME_OR_FOLD7_QA');
