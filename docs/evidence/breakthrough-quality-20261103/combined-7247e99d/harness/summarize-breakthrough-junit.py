#!/usr/bin/env python3
import collections,json,pathlib,sys,tarfile,xml.etree.ElementTree as E,shutil,subprocess
root=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);source=sys.argv[3]
xml=root/'app/build/test-results/testDebugUnitTest';files=sorted(xml.glob('TEST-*.xml'));assert files
summary={'source_sha':source,'total':dict(tests=0,skipped=0,failures=0,errors=0),'suite_count':len(files),'failures':[],'skips':[],'suites':[]};cases={}
def consume(r,target):
 for c in r.findall('testcase'):
  key=(c.attrib.get('classname',r.attrib['name']),c.attrib['name']);status='FAIL' if c.find('failure') is not None or c.find('error') is not None else 'SKIP' if c.find('skipped') is not None else 'PASS';assert key not in target,key;target[key]=status
for f in files:
 r=E.parse(f).getroot();consume(r,cases);row={'name':r.attrib['name'],**{k:int(r.attrib.get(k,0)) for k in summary['total']}};summary['suites'].append(row)
 for k in summary['total']:summary['total'][k]+=row[k]
 for c in r.findall('testcase'):
  for tag,key in [('failure','failures'),('error','failures'),('skipped','skips')]:
   e=c.find(tag)
   if e is not None:summary[key].append({'class':r.attrib['name'],'name':c.attrib['name'],'message':e.attrib.get('message')})
(out/'junit-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
old={};oldtar=root/'docs/evidence/omega-quality-20261103/combined-64ec06a7/junit-results.tar.gz'
with tarfile.open(oldtar) as t:
 for f in t.getmembers():
  if f.isfile() and f.name.endswith('.xml'):consume(E.fromstring(t.extractfile(f).read()),old)
retention={'baseline_sha':'64ec06a73f50cae9ee49035c840710d87777d50f','candidate_sha':source,'baseline_cases':len(old),'candidate_cases':len(cases),'removed':sorted(set(old)-set(cases)),'added':sorted(set(cases)-set(old)),'status_changes':[{'id':k,'before':v,'after':cases[k]} for k,v in old.items() if k in cases and cases[k]!=v]}
(out/'junit-case-retention.json').write_text(json.dumps(retention,indent=2)+'\n')
with tarfile.open(out/'junit-results.tar.gz','w:gz') as t:
 for f in files:t.add(f,arcname=f.name)
for f in files:
 if any(n in f.name for n in ['ContinuumGermanLongMemEvalHoldoutTest','ContinuumOfflineEvalTest','OwnerStatementTimeline','ResearchClaimAdmissionTest','ResearchPromptControlTokenTest','LearningExperienceReplayCacheTest','ContinuumAvatarNativeUiTest','ContinuumHologramMotionUiTest']):shutil.copy2(f,out/f.name)
shutil.copy2(root/'app/build/reports/lint-results-debug.xml',out/'lint-results-debug.xml')
m3=root/'app/build/continuum-eval/m3-report.tsv';shutil.copy2(m3,out/'m3-report.tsv')
r=subprocess.run(['node','scripts/continuum-offline-eval-compare.mjs','docs/evidence/omega-quality-20261103/combined-64ec06a7/m3-report.tsv',str(m3)],cwd=root,capture_output=True,text=True)
(out/'m3-comparison.json').write_text(r.stdout);(out/'m3-comparison.exit').write_text(str(r.returncode)+'\n'+r.stderr)
print(json.dumps({'junit':summary['total'],'suites':len(files),'removed':len(retention['removed']),'status_changes':retention['status_changes'],'m3_compare_exit':r.returncode},indent=2))
