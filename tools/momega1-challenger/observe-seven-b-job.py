"""Independent Docker whole-job kernel counters and host controller RSS. No env reads."""
import subprocess,os,time,json,sys,pathlib
out,*command=sys.argv[1:]
env={k:v for k,v in os.environ.items() if k not in ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH']}
docker=['/usr/local/bin/docker','--host=unix:///var/run/docker.sock']
def call(args):
 r=subprocess.run(docker+args,env=env,text=True,capture_output=True,timeout=8)
 return r.stdout.strip() if r.returncode==0 else None
before=set((call(['ps','-q','--filter','name=continuum-model-']) or '').split())
p=subprocess.Popen(command);start=time.monotonic();samples=[];configs={};peakHost=0;peakHostTree=0
def host_tree_rss(pid):
 # This host's task/children view omits real descendants. Build PPid links from
 # proc stat instead; never read another process environment or command line.
 parents={}
 for entry in pathlib.Path('/proc').iterdir():
  if not entry.name.isdigit():continue
  try:
   line=(entry/'stat').read_text();parent=int(line[line.rfind(')')+2:].split()[1]);parents.setdefault(parent,[]).append(int(entry.name))
  except (OSError,ValueError,IndexError):pass
 queue=[pid];seen=set();rows=[]
 while queue:
  q=queue.pop()
  if q in seen:continue
  seen.add(q);queue.extend(parents.get(q,[]))
  try:
   status=pathlib.Path('/proc/%d/status'%q).read_text();rss=int(next(x.split()[1] for x in status.splitlines() if x.startswith('VmRSS:')));rows.append({'pid':q,'rssKiB':rss})
  except (OSError,StopIteration):pass
 return rows
while p.poll() is None:
 try:
  status=pathlib.Path('/proc/%d/status'%p.pid).read_text()
  peakHost=max(peakHost,int(next(x.split()[1] for x in status.splitlines() if x.startswith('VmRSS:'))))
 except (OSError,StopIteration):pass
 ids=set((call(['ps','-q','--filter','name=continuum-model-']) or '').split())-before
 for cid in ids:
  if cid not in configs:
   raw=call(['inspect','--format','{{json .HostConfig}}',cid]); configs[cid]=json.loads(raw) if raw else None
  js="const f=require('fs');const o={};for(const n of ['memory.max','memory.current','memory.peak','memory.swap.max','memory.events','cpu.max','cpu.stat','pids.max'])o[n]=f.readFileSync('/sys/fs/cgroup/'+n,'utf8').trim();o.socketExists=f.existsSync('/var/run/docker.sock');o.hostHomeExists=f.existsSync('/home/oai/share');o.cgroup=f.readFileSync('/proc/self/cgroup','utf8').trim();const rs=f.statSync('/workspace/momega1-local-model/build-cpu/bin/llama-completion');o.processRss=[];for(const p of f.readdirSync('/proc').filter(x=>/^\\d+$/.test(x))){try{const s=f.readFileSync('/proc/'+p+'/status','utf8');o.processRss.push({pid:p,rssKiB:Number(/VmRSS:\\s+(\\d+)/.exec(s)?.[1]??0)});}catch{}}o.summedContainerRssKiB=o.processRss.reduce((a,p)=>a+p.rssKiB,0);o.native=[];for(const pid of f.readdirSync('/proc').filter(x=>/^\\d+$/.test(x))){try{const st=f.statSync('/proc/'+pid+'/exe');if(st.dev!==rs.dev||st.ino!==rs.ino)continue;const status=f.readFileSync('/proc/'+pid+'/status','utf8');const sm=f.readFileSync('/proc/'+pid+'/smaps_rollup','utf8');o.native.push({pid,dev:st.dev,ino:st.ino,rssKiB:Number(/VmRSS:\\s+(\\d+)/.exec(status)?.[1]??0),hwmKiB:Number(/VmHWM:\\s+(\\d+)/.exec(status)?.[1]??0),pssKiB:Number(/Pss:\\s+(\\d+)/.exec(sm)?.[1]??0),threads:Number(/Threads:\\s+(\\d+)/.exec(status)?.[1]??0),stat:f.readFileSync('/proc/'+pid+'/stat','utf8')});}catch{}}console.log(JSON.stringify(o))"
  raw=call(['exec',cid,'/node/bin/node','-e',js])
  if raw:
   try:
    hostRows=host_tree_rss(p.pid);hostTotal=sum(x['rssKiB'] for x in hostRows);peakHostTree=max(peakHostTree,hostTotal);samples.append({'seconds':round(time.monotonic()-start,3),'container':cid,'hostControllerTree':hostRows,'summedHostControllerTreeRssKiB':hostTotal,**json.loads(raw)})
   except ValueError:pass
 time.sleep(1)
remaining=set((call(['ps','-q','--filter','name=continuum-model-']) or '').split())-before
pathlib.Path(out).write_text(json.dumps({'command':command,'exitCode':p.wait(),'elapsedSeconds':round(time.monotonic()-start,3),'hostControllerSampledRssKiB':peakHost,'hostControllerTreeSampledRssKiB':peakHostTree,'measurementOverhead':'periodic Node cgroup reader inside same capped container; included in memory counters','containers':configs,'samples':samples,'remainingContainers':sorted(remaining)},indent=2)+'\n')
sys.exit(p.returncode)
