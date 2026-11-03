#!/usr/bin/env python3
"""Exact-source Android host evidence runner; no device/install/signing operations."""
import argparse, hashlib, json, os, pathlib, subprocess, time, xml.etree.ElementTree as ET, zipfile
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--output',required=True);p.add_argument('--review-root',required=True);a=p.parse_args()
root=pathlib.Path(a.source).resolve();out=pathlib.Path(a.output).resolve();review=pathlib.Path(a.review_root).resolve()
out.mkdir(parents=True,exist_ok=False)
def cmd(args): return subprocess.check_output(args,cwd=root,text=True).strip()
def digest(file): return hashlib.sha256(file.read_bytes()).hexdigest()
def manifest():
 files=cmd(['git','ls-files','app','build.gradle.kts','settings.gradle.kts','gradle.properties','gradle','AGENTS.md']).splitlines()
 inputs={f:digest(root/f) for f in files if (root/f).is_file()}
 external={str(f):digest(f) for folder in ['tools/genesis-ui-review','tools/synapse-ui-review'] for f in sorted((review/folder).rglob('*')) if f.is_file() and f.suffix in ['.kt','.gradle']}
 jvm=pathlib.Path('/workspace/continuum-build-env/test-jvm21.init.gradle');external[str(jvm)]=digest(jvm)
 return {'source':cmd(['git','rev-parse','HEAD']),'status':cmd(['git','status','--porcelain','--untracked-files=all']),'inputs':inputs,'reviewInputs':external}
before=manifest();assert not before['status'];(out/'before.json').write_text(json.dumps(before,indent=2)+'\n')
# Caller paths are passed through env; the shell program is fixed.
env=os.environ.copy();env['INDEPENDENT_REVIEW_INIT']=str(review/'tools/genesis-ui-review/connection.init.gradle')
script='''source /workspace/continuum-build-env/env.sh
exec gradle --no-daemon --max-workers=2 --console=plain \\
 -I /workspace/continuum-build-env/test-jvm21.init.gradle \\
 -I "$INDEPENDENT_REVIEW_INIT" \\
 -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m" \\
 -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \\
 -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \\
 -Pkotlin.compiler.execution.strategy=in-process \\
 :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin
'''
(out/'command.sh').write_text(script)
started=time.monotonic()
with (out/'gradle.log').open('w') as log:r=subprocess.run(['bash','-c',script],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
(out/'gradle.exit').write_text(str(r.returncode)+'\n')
after=manifest();(out/'after.json').write_text(json.dumps(after,indent=2)+'\n')
suites=[];skips=[];failed=[]
xmls=sorted((root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
for file in xmls:
 s=ET.parse(file).getroot();row={'name':s.attrib['name'],**{k:int(s.attrib.get(k,0)) for k in ['tests','failures','errors','skipped']}}
 suites.append(row)
 for test in s.findall('testcase'):
  if test.find('skipped') is not None:skips.append({'class':test.attrib.get('classname'),'name':test.attrib['name']})
  if test.find('failure') is not None or test.find('error') is not None:failed.append({'class':test.attrib.get('classname'),'name':test.attrib['name']})
summary={k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']}
summary.update({'source':before['source'],'gradleExit':r.returncode,'elapsedSeconds':time.monotonic()-started,'suites':len(suites),'sourceAndReviewInputsUnchanged':before==after,'environment':'HOST','deviceVerified':False,'apkBuilt':False,'lintScope':'repository NewApi-only configuration'})
summary['passed']=summary['tests']-summary['failures']-summary['errors']-summary['skipped']
reviewSuites=[s for s in suites if '.IndependentBot' in s['name'] or s['name'].endswith('.IndependentActivitySpeechTest')]
summary['reviewSubsetIncluded']={k:sum(s[k] for s in reviewSuites) for k in ['tests','failures','errors','skipped']}
for name,obj in [('summary.json',summary),('suite-counts.json',suites),('skipped-cases.json',skips),('failed-cases.json',failed)]: (out/name).write_text(json.dumps(obj,indent=2)+'\n')
with zipfile.ZipFile(out/'junit-raw.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in xmls:z.write(f,f.name)
for f in (root/'app/build/reports').glob('lint-results-debug.*'):
 (out/f.name).write_bytes(f.read_bytes())
(out/'SHA256SUMS').write_text(''.join(f'{digest(f)}  {f.name}\n' for f in sorted(out.iterdir()) if f.is_file() and f.name!='SHA256SUMS'))
print(json.dumps(summary,indent=2),flush=True)
assert before==after,'Source or review inputs changed'
raise SystemExit(r.returncode)
