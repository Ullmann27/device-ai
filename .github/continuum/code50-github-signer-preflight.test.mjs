import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

// Funktionale Prüfung der Preflight-Gates von scripts/continuum-code50-github-signer.sh
// mit einem Fake-`gh` (keine Netzwerkzugriffe, keine Geheimnisse, kein Signiervorgang).
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const script = path.join(root, 'scripts/continuum-code50-github-signer.sh');
const SHA = 'a'.repeat(40);
const RUN = '123456789';
const BRANCH = 'copilot/code51-master-integrator-20261108';
const hasTools = ['bash', 'jq', 'base64', 'sha256sum', 'curl', 'stat'].every(t => spawnSync('sh', ['-c', `command -v ${t}`]).status === 0);

const good = () => ({
  env: {
    deployment_branch_policy: {custom_branch_policies: true, protected_branches: false},
    protection_rules: [{type: 'required_reviewers', reviewers: [{type: 'User', reviewer: {login: 'Ullmann27'}}]}, {type: 'branch_policy'}]
  },
  policies: {branch_policies: [{name: BRANCH, type: 'branch'}]},
  run: {head_sha: SHA, head_branch: BRANCH, event: 'workflow_dispatch', status: 'completed', conclusion: 'success', path: '.github/workflows/continuum-code50-github-qa.yml'},
  jobs: {jobs: [{name: 'node-backend', conclusion: 'success'}, {name: 'android-host', conclusion: 'success'}]},
  releaseExists: false,
  latestTag: 'continuum-local-code50-473f79727e4b52509cc7d1af71e59b80ab9b6be5'
});

function runPreflight(mutate, {mode = 'preflight', overrides = {}} = {}) {
  const fx = good();
  mutate?.(fx);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'code50-signer-'));
  try {
    fs.writeFileSync(path.join(dir, 'fx.json'), JSON.stringify(fx));
    const fake = `#!/usr/bin/env node
const fs=require('fs');const fx=JSON.parse(fs.readFileSync(${JSON.stringify(path.join(dir, 'fx.json'))},'utf8'));
const a=process.argv.slice(2).join(' ');
const out=v=>{process.stdout.write(JSON.stringify(v));process.exit(0)};
if(a.includes('release view')) process.exit(fx.releaseExists?0:1);
if(a.includes('releases/latest')){ if(!fx.latestTag) process.exit(1); process.stdout.write(fx.latestTag+'\\n'); process.exit(0);}
if(a.endsWith('/deployment-branch-policies')) out(fx.policies);
if(/\\/environments\\/continuum-original-signing$/.test(a)) out(fx.env);
if(/\\/actions\\/runs\\/\\d+\\/jobs/.test(a)) out(fx.jobs);
if(/\\/actions\\/runs\\/\\d+$/.test(a)) out(fx.run);
process.exit(7);
`;
    fs.mkdirSync(path.join(dir, 'bin'));
    fs.writeFileSync(path.join(dir, 'bin/gh'), fake, {mode: 0o755});
    const env = {
      PATH: `${path.join(dir, 'bin')}:${process.env.PATH}`, HOME: dir, TMPDIR: dir,
      GITHUB_REPOSITORY: 'Ullmann27/device-ai', GITHUB_REF: `refs/heads/${BRANCH}`, GITHUB_EVENT_NAME: 'workflow_dispatch',
      GITHUB_SHA: SHA, SOURCE_SHA: SHA, QA_RUN_ID: RUN, OWNER_APPROVED_SHA: SHA, ...overrides
    };
    const r = spawnSync('bash', [script, mode], {env, encoding: 'utf8'});
    return {status: r.status, out: `${r.stdout}${r.stderr}`};
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
}

const failsWith = (res, code) => { assert.equal(res.status, 69, res.out); assert.match(res.out, new RegExp(`CODE51_SIGN_${code}\\b`), res.out); };

test('Preflight besteht nur mit allen Gates', {skip: !hasTools}, () => {
  const res = runPreflight();
  assert.equal(res.status, 0, res.out);
  assert.match(res.out, /CHECKPOINT=ENVIRONMENT_OWNER_REVIEWER_AND_EXACT_BRANCH_POLICY_VERIFIED/);
  assert.match(res.out, /CHECKPOINT=EXACT_SOURCE_QA_RUN_GREEN/);
  assert.match(res.out, /STATUS=PREFLIGHT_OK/);
  assert.match(res.out, /INFO=lifecycle_jdk21_job=PASS_REQUIRED_IN_ANDROID_HOST/);
});

test('Path-Suffix @ref des QA-Laufs wird akzeptiert, fremder Workflow nicht', {skip: !hasTools}, () => {
  assert.equal(runPreflight(f => { f.run.path += `@refs/heads/${BRANCH}`; }).status, 0);
  failsWith(runPreflight(f => { f.run.path = '.github/workflows/other.yml'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
});

test('Environment ohne Pflicht-Reviewer oder mit falscher Branch-Regel bricht VOR Geheimnissen ab', {skip: !hasTools}, () => {
  failsWith(runPreflight(f => { f.env.protection_rules = [{type: 'branch_policy'}]; }), 'ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING');
  failsWith(runPreflight(f => { f.env.protection_rules[0].reviewers = [{type: 'User', reviewer: {login: 'someone-else'}}]; }), 'ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING');
  failsWith(runPreflight(f => { f.env.deployment_branch_policy = null; }), 'ENVIRONMENT_BRANCH_POLICY_NOT_CUSTOM');
  failsWith(runPreflight(f => { f.policies.branch_policies = [{name: 'codex/continuum-code48-github-owner-20261107', type: 'branch'}]; }), 'ENVIRONMENT_BRANCH_POLICY_NOT_EXACT');
  failsWith(runPreflight(f => { f.policies.branch_policies.push({name: '*', type: 'branch'}); }), 'ENVIRONMENT_BRANCH_POLICY_NOT_EXACT');
});

test('QA-Lauf muss exakt, manuell, abgeschlossen und grün sein', {skip: !hasTools}, () => {
  failsWith(runPreflight(f => { f.run.head_sha = 'b'.repeat(40); }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.run.event = 'push'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.run.conclusion = 'failure'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.run.head_branch = 'main'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.jobs.jobs[0].conclusion = 'failure'; }), 'NODE_BACKEND_NOT_GREEN');
  failsWith(runPreflight(f => { f.jobs.jobs = f.jobs.jobs.filter(j => j.name !== 'android-host'); }), 'ANDROID_HOST_NOT_GREEN');
});

test('Unreadable or different upgrade baseline cannot silently permit publication', {skip: !hasTools}, () => {
  failsWith(runPreflight(f => { f.latestTag = null; }), 'LATEST_RELEASE_UNREADABLE');
  failsWith(runPreflight(f => { f.latestTag = 'unrelated'; }), 'LATEST_RELEASE_IDENTITY_INVALID');
  failsWith(runPreflight(f => { f.latestTag = 'continuum-local-code50-' + SHA; }), 'CODE50_BASELINE_CHANGED');
});

test('Freigabe-SHA, Commit-Bindung, Branch, Event und Release-Zustand sind erzwungen', {skip: !hasTools}, () => {
  failsWith(runPreflight(null, {overrides: {OWNER_APPROVED_SHA: 'b'.repeat(40)}}), 'APPROVED_SHA_DIFFERS_FROM_SOURCE');
  failsWith(runPreflight(null, {overrides: {GITHUB_SHA: 'b'.repeat(40)}}), 'SOURCE_DIFFERS_FROM_RUN_COMMIT');
  failsWith(runPreflight(null, {overrides: {SOURCE_SHA: 'xyz', OWNER_APPROVED_SHA: 'xyz'}}), 'SOURCE_SHA_FORMAT');
  failsWith(runPreflight(null, {overrides: {GITHUB_REF: 'refs/heads/main'}}), 'WRONG_BRANCH');
  failsWith(runPreflight(null, {overrides: {GITHUB_EVENT_NAME: 'push'}}), 'WRONG_EVENT');
  failsWith(runPreflight(null, {overrides: {QA_RUN_ID: '12; rm -rf /'}}), 'QA_RUN_FORMAT');
  failsWith(runPreflight(f => { f.releaseExists = true; }), 'RELEASE_ALREADY_EXISTS');
  failsWith(runPreflight(f => { f.latestTag = 'continuum-local-code51-' + SHA; }), 'NOT_HIGHER_THAN_LATEST_RELEASE');
  failsWith(runPreflight(null, {mode: 'unbekannt'}), 'BAD_MODE');
});

test('Sign-Modus ohne Android-Werkzeuge und ohne Schlüssel bricht ab, ohne etwas zu veröffentlichen', {skip: !hasTools}, () => {
  failsWith(runPreflight(null, {mode: 'sign', overrides: {ANDROID_HOME: ''}}), 'ANDROID_HOME_MISSING');
});
