import subprocess, shutil, sys, os, re, json
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)),'../../..'))
W='.github/continuum/step-watchdog.mjs'; L='.github/continuum/repair-loop.mjs'; P='scripts/continuum-owned-process.mjs'
TW='.github/continuum/step-watchdog.test.mjs'; TL='.github/continuum/repair-loop.test.mjs'
TB='.github/continuum/backlog-repair-worker.test.mjs'; TP='scripts/continuum-owned-process.test.mjs'
M=[
 ('M1 abort detection off',W,"else if(aborted())stop('TASK_ABORTED');","else if(false&&aborted())stop('TASK_ABORTED');",[TW],
  ['an owner abort ends a running step','a task aborted before the step starts','an abort watch ends a running check profile']),
 ('M2 run ignores the watch',L,"const command=abortWatch?resolveCommand(cmd,childEnv,cwd):null;","const command=null;",[TW,TB],
  ['an abort watch ends a running check profile','an owner cancel during a running check step']),
 ('M3 no checkpoint after a step',L,"    atStep('checked:'+name);\n","",[TL,TW,TB],
  ['a checkpoint runs before and after every check step','an abort watch ends a running check profile','an owner cancel during a running check step']),
 ('M4 no fail-closed throw',L,"    if(r.stoppedByOwner)throw Error('CHECK_STEP_STOPPED_BY_OWNER');\n","",[TW],
  ['an abort watch ends a running check profile']),
 ('M5 exit code not passed through',W,"else if(Number.isInteger(result.code))process.exit(result.code);","else if(Number.isInteger(result.code))process.exit(0);",[TW],
  ['passes streams, exit code and ending signal']),
 ('M6 signal not passed through',W,"else if(result.exitSignal)endBy(result.exitSignal);","else if(false)endBy(result.exitSignal);",[TW],
  ['passes streams, exit code and ending signal']),
 ('M7 parent-gone off',W,"if(process.ppid!==parent)stop('PARENT_GONE');","if(false)stop('PARENT_GONE');",[TW],
  ['stops when the waiting parent is gone']),
 ('M8 output not inherited',P,"stdio:['ignore',...(inheritOutput?['inherit','inherit']:['pipe','pipe'])]","stdio:['ignore','pipe','pipe']",[TW],
  ['passes streams, exit code and ending signal']),
 ('M9 SIGTERM not forwarded',W,"for(const name of ['SIGTERM','SIGINT','SIGHUP'])process.on(name,()=>stop(name));","",[TW],
  ['a spawnSync timeout still reads as a timeout']),
 ('M10 exit signal not captured',P,"resultCode=code;resultSignal=exitSignal;closed=true;","resultCode=code;closed=true;",[TP,TW],
  ['the signal that ended the direct child is reported','passes streams, exit code and ending signal']),
]
only=sys.argv[1:] 
results=[]
for name,f,old,new,tests,expect in M:
  if only and name.split()[0] not in only: continue
  src=open(f).read(); assert src.count(old)==1,(name,src.count(old))
  try:
    open(f,'w').write(src.replace(old,new))
    env={k:v for k,v in os.environ.items() if k not in ('NODE_TEST_CONTEXT','CONTINUUM_REPAIR_DEPTH')}
    r=subprocess.run(['node','--test','--test-reporter=tap','--test-concurrency=3',*tests],capture_output=True,text=True,env=env,timeout=900)
    failed=[l.split(' - ',1)[1] for l in r.stdout.splitlines() if re.match(r'^not ok \d+ - ',l)]
  finally:
    open(f,'w').write(src)
  missing=[e for e in expect if not any(e in x for x in failed)]
  results.append({'mutation':name,'failedTests':failed,'expectedButPassing':missing,'killed':not missing and len(failed)>0})
  print(json.dumps(results[-1],ensure_ascii=False),flush=True)
print('ALL_KILLED' if all(r['killed'] for r in results) else 'SURVIVORS')
