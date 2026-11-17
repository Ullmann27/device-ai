#!/usr/bin/env python3
"""Observe only processes whose executable inode is the reviewed native runtime.
No other process command line, environment, or private contents are read.
"""
import json,os,pathlib,subprocess,sys,time
if len(sys.argv)<5:raise SystemExit('usage: output.json reviewed-binary command [args...]')
output,binary=sys.argv[1:3];identity=pathlib.Path(binary).stat();wanted=(identity.st_dev,identity.st_ino);started=time.monotonic();child=subprocess.Popen(sys.argv[3:]);pids=set();samples=0;peak_rss=peak_hwm=peak_threads=0;misses=0
while child.poll() is None:
 for entry in pathlib.Path('/proc').iterdir():
  if not entry.name.isdigit():continue
  try:
   st=(entry/'exe').stat()
   if (st.st_dev,st.st_ino)!=wanted:continue
   pids.add(int(entry.name));sample={}
   for line in (entry/'status').read_text().splitlines():
    if line.startswith(('VmRSS:','VmHWM:','Threads:')):sample[line.split(':')[0]]=int(line.split()[1])
   samples+=1;peak_rss=max(peak_rss,sample.get('VmRSS',0));peak_hwm=max(peak_hwm,sample.get('VmHWM',0));peak_threads=max(peak_threads,sample.get('Threads',0))
  except (PermissionError,FileNotFoundError,ProcessLookupError):misses+=1
 time.sleep(.2)
code=child.wait();record={'command':sys.argv[3:],'runtimePath':binary,'runtimeDevice':identity.st_dev,'runtimeInode':identity.st_ino,'exit':code,'elapsedSeconds':round(time.monotonic()-started,3),'matchedRuntimeProcesses':len(pids),'samples':samples,'peakObservedRssKiB':peak_rss,'peakObservedVmHwmKiB':peak_hwm,'peakObservedThreads':peak_threads,'procAccessMisses':misses,'measurement':'Executable-inode-matched /proc status samples; sampled peak may miss transients; not a standalone benchmark'}
pathlib.Path(output).write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record));raise SystemExit(code)
