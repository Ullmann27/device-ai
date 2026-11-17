#!/usr/bin/env node
/**
 * Owner-operated manifest generator. It ONLY reads an APK already signed by the
 * original Fold7 key; it never signs, uploads, or touches owner credentials.
 *
 * Usage:
 * node scripts/continuum-owner-update-manifest.mjs \
 *   <signed-apk> <approved-https-url> <source-git-sha> <output-json>
 */
import { createHash } from 'node:crypto';
import { createReadStream, statSync, writeFileSync, realpathSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve, join } from 'node:path';

const PACKAGE = 'at.heinz.deviceai.continuumlocal';
const CERT = '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';
const HOST = 'continuum-owner-v23-release-preflight.onrender.com';
const HEX64 = /^[a-f0-9]{64}$/;
const HEX40 = /^[a-f0-9]{40}$/;
const rejected = (reason) => { throw new Error('OWNER_RELEASE_BLOCKED: ' + reason); };

function certificate(apk, signer) {
  const verification = execFileSync(signer, ['verify','--verbose','--print-certs',apk],
    { encoding:'utf8', maxBuffer:1024*1024, timeout:30000 });
  if (!/Verified using v2 scheme \(APK Signature Scheme v2\): true/.test(verification))
    rejected('APK v2 signature verification did not pass');
  const matches = [...verification.matchAll(/Signer #(\d+) certificate SHA-256 digest: ([a-f0-9]+)/gi)];
  if (matches.length !== 1 || matches[0][1] !== '1' ||
      matches[0][2].toLowerCase() !== CERT)
    rejected('signed APK has a different/multiple signer');
}

function packageIdentity(apk, aapt2) {
  const badging = execFileSync(aapt2, ['dump','badging',apk],
    { encoding:'utf8', maxBuffer:1024*1024, timeout:30000 });
  const match = badging.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m);
  if (!match || match[1] !== PACKAGE) rejected('signed APK belongs to another application');
  const code = Number(match[2]);
  if (!Number.isSafeInteger(code) || code < 29 || code > 9999)
    rejected('owner APK versionCode must be 29..9999');
  const name = '0.3.' + (code-1);
  if (match[3] !== name) rejected('versionName must correspond to owner release');
  return { code, name };
}

async function generate(args) {
  if (args.length !== 4) rejected('Usage: <signed-apk> <approved-https-url> <source-git-sha> <output-json>');
  const [rawApk, rawUrl, sourceCommit, outputJson] = args;
  if (!HEX40.test(sourceCommit)) rejected('source commit is not a canonical SHA-1 identifier');
  const apk = realpathSync(resolve(rawApk));
  const size = statSync(apk);
  if (!size.isFile() || size.size < 1_000_000 || size.size > 2_000_000_000)
    rejected('APK file size is outside approved bounds');
  const sha = createHash('sha256');
  for await (const chunk of createReadStream(apk, { highWaterMark: 1024*1024 })) sha.update(chunk);
  const digest = sha.digest('hex');
  if (!HEX64.test(digest)) rejected('invalid APK digest');
  const url = new URL(rawUrl);
  if (url.protocol !== 'https:' || url.hostname !== HOST || url.port !== '' ||
      url.username !== '' || url.password !== '' || url.search !== '' || url.hash !== '' ||
      url.pathname !== '/continuum-local/Continuum-Lokal-' + digest + '.apk')
    rejected('URL is not the pinned immutable owner APK endpoint');
  const androidHome = process.env.ANDROID_HOME;
  const prefix = process.env.PREFIX;
  if (!androidHome || !prefix) rejected('Fold7 Android SDK and Termux toolchain not configured');
  const signer = join(androidHome,'build-tools','35.0.0','apksigner');
  const aapt2 = join(prefix,'bin','aapt2');
  certificate(apk, signer);
  const { code, name } = packageIdentity(apk, aapt2);
  const manifest = {
    schema: 1,
    channel: 'OWNER_RELEASE',
    packageName: PACKAGE,
    versionCode: code,
    versionName: name,
    apkUrl: url.toString(),
    apkSha256: digest,
    byteLength: size.size,
    sourceCommit,
    signerSha256: CERT,
    approved: true,
    notes: 'Originalsigniertes Continuum-Lokal-Update; bestehende App-Daten bleiben unverändert.'
  };
  writeFileSync(outputJson, JSON.stringify(manifest) + '\n', { encoding:'utf8', flag:'wx', mode:0o600 });
  process.stdout.write('OWNER_MANIFEST_CREATED_NOT_PUBLISHED versionCode=' + code +
    ' apk_sha256=' + digest + ' source_sha=' + sourceCommit + '\n');
}

generate(process.argv.slice(2)).catch((error) => {
  process.stderr.write(String(error.message || error).slice(0,300) + '\n');
  process.exitCode = 69;
});
