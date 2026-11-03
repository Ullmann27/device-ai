# Adapted from existing QA PR413 at 10f4339a760f8a531993598200b1c3299d3b2b97; reviewer-only new three-case A0.
#!/usr/bin/env python3
"""QA resource sampler, extending the independent reviewer's executable-inode method.
Reads no other process command lines/environment. Invoke only with exclusive model use.
Usage: NEW_REPORT_JSON REVIEWED_RUNTIME COMMAND ARG...
"""
import hashlib,json,os,pathlib,subprocess,sys,time

if len(sys.argv)<4:
    raise SystemExit('usage: NEW_REPORT_JSON REVIEWED_RUNTIME COMMAND ARG...')
output,binary=sys.argv[1:3]
target=pathlib.Path(output)
if target.exists():
    raise SystemExit('Refusing existing report')
identity=pathlib.Path(binary).stat()
wanted=(identity.st_dev,identity.st_ino)
ticks=os.sysconf('SC_CLK_TCK')

def matching():
    found=[]
    for entry in pathlib.Path('/proc').iterdir():
        if not entry.name.isdigit():
            continue
        try:
            st=(entry/'exe').stat()
            if (st.st_dev,st.st_ino)==wanted:
                found.append(entry)
        except (PermissionError,FileNotFoundError,ProcessLookupError):
            pass
    return found

existing=matching()
if existing:
    raise SystemExit('Reviewed runtime already active: exclusive sampling not available')
started=time.monotonic()
child=subprocess.Popen(sys.argv[3:])
samples=[]
processes={}
while child.poll() is None:
    for entry in matching():
        sample={'pid':int(entry.name),'elapsedSeconds':round(time.monotonic()-started,3)}
        try:
            values={}
            for line in (entry/'status').read_text().splitlines():
                if line.startswith(('VmRSS:','VmHWM:','Threads:')):
                    values[line.split(':')[0]]=int(line.split()[1])
            sample.update(rssKiB=values.get('VmRSS'),hwmKiB=values.get('VmHWM'),threads=values.get('Threads'))
        except (PermissionError,FileNotFoundError,ProcessLookupError) as error:
            sample['statusUnavailable']=type(error).__name__
        try:
            # comm may contain spaces/parentheses; fields after final ')' begin at3.
            fields=(entry/'stat').read_text().rsplit(')',1)[1].split()
            sample.update(startTicks=int(fields[19]),userCpuTicks=int(fields[11]),systemCpuTicks=int(fields[12]))
            sample['observedCpuSeconds']=(sample['userCpuTicks']+sample['systemCpuTicks'])/ticks
        except (PermissionError,FileNotFoundError,ProcessLookupError,ValueError,IndexError) as error:
            sample['cpuUnavailable']=type(error).__name__
        try:
            rows=(entry/'smaps_rollup').read_text().splitlines()
            sample['pssKiB']=next(int(row.split()[1]) for row in rows if row.startswith('Pss:'))
        except (PermissionError,FileNotFoundError,ProcessLookupError,ValueError,StopIteration) as error:
            sample['pssUnavailable']=type(error).__name__
        key=str(sample['pid'])+':'+str(sample.get('startTicks','UNKNOWN'))
        record=processes.setdefault(key,{'pid':sample['pid'],'startTicks':sample.get('startTicks'),'samples':0})
        record['samples']+=1
        for field in ['rssKiB','hwmKiB','pssKiB','threads','observedCpuSeconds']:
            if sample.get(field) is not None:
                record[field]=max(record.get(field,0),sample[field])
        samples.append(sample)
    time.sleep(.2)
code=child.wait()
record={'schema':'momega1-native-resource-observation-v1','command':sys.argv[3:],'runtimePath':binary,
        'runtimeSha256':hashlib.sha256(pathlib.Path(binary).read_bytes()).hexdigest(),
        'runtimeDevice':identity.st_dev,'runtimeInode':identity.st_ino,'exit':code,
        'elapsedSeconds':round(time.monotonic()-started,3),'clockTicksPerSecond':ticks,
        'processes':list(processes.values()),'samples':samples,'measurement':
        'Executable-inode-matched native processes, 200ms samples. Missing readings are UNAVAILABLE, never zero. '
        'Sampled peaks and cumulative CPU observations may miss process start/exit/transients. '
        'Requires exclusive use of this binary; cannot attribute an unrelated concurrent invocation. '
        'No wrapper RSS, device metric, GPU metric or latency advantage is inferred.'}
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'resourceReport':output,'exit':code,'nativeProcesses':len(processes),'samples':len(samples)}))
raise SystemExit(code)
