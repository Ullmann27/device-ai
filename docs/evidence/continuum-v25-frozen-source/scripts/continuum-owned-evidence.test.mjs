import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {getProjectPlan} from './continuum-project-plan.mjs';
import {mockOwnedReports} from './fixtures/owned-build.mjs';
import {readOwnedBuildEvidence,parseOwnedJUnit,parseOwnedLint} from '../.github/continuum/owned-project-evidence.mjs';

test('actual Gradle JUnit shape requires each planned test and ignores CDATA log tags',()=>{
  const project=fs.mkdtempSync(path.join(os.tmpdir(),'owned-xml-'));
  try {
    const {xml}=mockOwnedReports(project);
    const noisy=xml.replace('<![CDATA[]]>','<![CDATA[<testcase name="forged"/>]]>');
    assert.equal(parseOwnedJUnit(noisy,getProjectPlan()).executed,3);
    for(const changed of [
      xml.replace('tests="3"','tests="4"'), xml.replace('skipped="0"','skipped="1"'),
      xml.replace('boundsNotes','foreignTest'), xml.replace('classname="at.heinz.continuum.m4notes.NotePolicyTest"','classname="foreign"'),
      xml.replace('<testcase','<system-out><testcase').replace('</testsuite>','</system-out></testsuite>'),
      xml.replace('</testsuite>',''), '<!DOCTYPE x>'+xml,
      xml.replace('<properties/>','<failure/>')
    ]) assert.throws(()=>parseOwnedJUnit(changed,getProjectPlan()));
  } finally {fs.rmSync(project,{recursive:true,force:true});}
});
test('lint errors, fake nested issues and malformed XML are never passing evidence',()=>{
  assert.equal(parseOwnedLint('<?xml version="1.0"?><issues format="6"/>').errors,0);
  assert.equal(parseOwnedLint('<issues><issue severity="Warning"><location file="x"/></issue></issues>').warnings,1);
  for(const xml of ['<issues><issue severity="Error"/></issues>','<issues><issue severity="Fatal"/></issues>',
    '<issues><wrapper><issue severity="Warning"/></wrapper></issues>','<issues>','<issues/><issues/>',
    '<issues><issue/></issues>','<!DOCTYPE x><issues/>']) assert.throws(()=>parseOwnedLint(xml));
});
test('fresh reports are bound to exact paths; stale and symlinked evidence fails closed',t=>{
  const project=fs.mkdtempSync(path.join(os.tmpdir(),'owned-evidence-'));
  t.after(()=>fs.rmSync(project,{recursive:true,force:true}));
  const notBefore=Date.now(),{unit}=mockOwnedReports(project),plan=getProjectPlan();
  assert.equal(readOwnedBuildEvidence(project,plan,notBefore).unit.executed,3);
  fs.utimesSync(path.join(project,unit),1,1);
  assert.throws(()=>readOwnedBuildEvidence(project,plan,notBefore),/STALE/);
  fs.unlinkSync(path.join(project,unit));
  fs.symlinkSync('/etc/passwd',path.join(project,unit));
  assert.throws(()=>readOwnedBuildEvidence(project,plan,0),/UNSAFE/);
});
