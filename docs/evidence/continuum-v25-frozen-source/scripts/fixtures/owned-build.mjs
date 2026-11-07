import fs from 'node:fs';
import path from 'node:path';
// Test-only, explicitly simulated Gradle evidence. Not an Android build.
export function mockOwnedReports(project,recipeId='m4-notes') {
  const counter=recipeId==='m4-counter';
  const cls=counter?'at.heinz.continuum.m4counter.CounterPolicyTest':'at.heinz.continuum.m4notes.NotePolicyTest';
  const cases=counter?['increments','boundsCounter','clampsInvalidStorage']:['trimsWhitespace','boundsNotes','keepsEmptyNotes'];
  const xml='<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="'+cls+'" tests="3" skipped="0" failures="0" errors="0">'+
    '<properties/>'+cases.map(name=>'<testcase name="'+name+'" classname="'+cls+'" time="0.001"/>').join('')+
    '<system-out><![CDATA[]]></system-out><system-err><![CDATA[]]></system-err></testsuite>\n';
  const unit='app/build/test-results/testDebugUnitTest/TEST-'+cls+'.xml';
  const lint='app/build/reports/lint-results-debug.xml';
  for(const [name,body] of [[unit,xml],[lint,'<?xml version="1.0" encoding="UTF-8"?><issues format="6" by="mock lint"/>\n']]) {
    fs.mkdirSync(path.dirname(path.join(project,name)),{recursive:true});
    fs.writeFileSync(path.join(project,name),body);
  }
  return {unit,lint,xml};
}
