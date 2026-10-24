#!/usr/bin/env node
// Exact source and owner authorization gate for the *existing* Continuum package.
// Proposals and code presence are never owner approval. This script never signs.
import { execFileSync } from 'node:child_process';
import { readFileSync, lstatSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const manifestPath = 'docs/release/continuum-0321-source-proposal-20261024.json';
const SOURCE = 'ec92490bbe7d75a2b9c16a47a94a1991f61837cc';
const MANIFEST_BLOB = '36433bba220cb5706e286d8145df4c7a61734c0b';
const PACKAGE = 'at.heinz.deviceai.mentorpaired';
const CERT = '2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc';
const fail = message => { process.stderr.write('OWNER_V22_BLOCKED: ' + message + '\n'); process.exit(69); };
const git = (...args) => execFileSync('git', args, {
  cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 20000
}).trim();
const included = path => path.startsWith('app/src/') || path === 'app/build.gradle.kts' ||
  path === 'app/proguard-rules.pro' || path.startsWith('backend/') ||
  path.startsWith('.github/continuum/') || path.startsWith('scripts/') ||
  path.startsWith('gradle/') ||
  ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties'].includes(path);
const allowedBuilderPaths = new Set([
  'scripts/render-mentor-preview.sh', 'scripts/render-mentor-owner-update-v22.sh',
  'scripts/continuum-v22-source-gate.mjs',
  'scripts/continuum-v22-owner-release.test.mjs'
]);
try {
  // The owner must specifically approve the frozen source and proposed inventory.
  if(process.env.CONTINUUM_OWNER_V22_APPROVED_SOURCE_SHA !== SOURCE ||
     process.env.CONTINUUM_OWNER_V22_APPROVED_MANIFEST_BLOB !== MANIFEST_BLOB)
    fail('Exact source AND full source inventory approval missing');
  const head = git('rev-parse', 'HEAD');
  if(!/^[a-f0-9]{40}$/.test(head) ||
      process.env.CONTINUUM_RELEASE_SHA !== head ||
      process.env.CONTINUUM_OWNER_V22_APPROVED_SHA !== head)
    fail('Exact owner-approved release builder commit missing or different');
  const manifest = join(root,manifestPath);
  if(git('hash-object','--',manifestPath) !== MANIFEST_BLOB)
    fail('Source proposal has changed since review');
  const j = JSON.parse(readFileSync(manifest,'utf8'));
  if(j.schemaVersion !== 1 || j.state !== 'SOURCE_PROPOSAL_NOT_OWNER_APPROVAL' ||
     j.sourceHead !== SOURCE || j.package !== PACKAGE ||
     j.versionCode !== 22 || j.versionName !== '0.3.21' ||
     j.certificateSha256 !== CERT || !Array.isArray(j.files) || j.files.length !== 927)
    fail('Unexpected release inventory or package identity');
  const seen = new Set();
  for(const item of j.files) {
    const p=item.path;
    if(typeof p !== 'string' || !/^[A-Za-z0-9_.\/-]+$/.test(p) || p.startsWith('/') ||
       p.split('/').includes('..') || seen.has(p) || !included(p) ||
       typeof item.blob !== 'string' || !/^[a-f0-9]{40}$/.test(item.blob))
      fail('Malformed or duplicate source entry');
    seen.add(p);
    if(p === 'scripts/render-mentor-preview.sh') continue; // pinned by final approved builder commit
    let at=root;
    const segments=p.split('/');
    for(let i=0;i<segments.length;i++){
      at=join(at,segments[i]);
      const st=lstatSync(at);
      if(st.isSymbolicLink() || (i<segments.length-1 && !st.isDirectory()) ||
         (i===segments.length-1 && !st.isFile())) fail('Source redirection: '+p);
    }
    if(git('hash-object','--',p)!==item.blob)fail('Altered approved source '+p);
  }
  const actual=git('ls-files','-z','--',
    'app/src','app/build.gradle.kts','app/proguard-rules.pro','backend',
    '.github/continuum','scripts','gradle','build.gradle.kts','settings.gradle.kts','gradle.properties')
      .split('\u0000').filter(Boolean).filter(included);
  for(const p of actual) if(!seen.has(p) && !allowedBuilderPaths.has(p))
      fail('New unattested source path '+p);
  if([...seen].some(p => !actual.includes(p)))fail('Missing originally approved source');
  const changes=git('-c','core.fileMode=false','diff','--name-only','HEAD','--');
  if(changes)fail('Tracked source content changed after approved builder');
  const untracked=git('ls-files','--others','--exclude-standard','--',
      'app/src','backend/src','backend/test','scripts','.github/continuum');
  if(untracked)fail('Untracked executable source present');
  console.log('OWNER_V22_SOURCE_VERIFIED package='+PACKAGE+' versionCode=22 files='+j.files.length+
              ' source='+SOURCE+' builder='+head);
} catch(e){ fail(e instanceof Error?e.message:'Unknown source verification failure'); }
