#!/usr/bin/env python3
"""Native child-tree rusage of an explicitly supplied reviewer command."""
import json,pathlib,resource,subprocess,sys,time
if len(sys.argv)<3:raise SystemExit('usage: output.json command [args...]')
start=time.monotonic();p=subprocess.run(sys.argv[2:]);r=resource.getrusage(resource.RUSAGE_CHILDREN)
result={'command':sys.argv[2:],'exit':p.returncode,'elapsedSeconds':round(time.monotonic()-start,3),'maxRssKiB':r.ru_maxrss,'userCpuSeconds':r.ru_utime,'systemCpuSeconds':r.ru_stime,'minorFaults':r.ru_minflt,'majorFaults':r.ru_majflt,'measurement':'RUSAGE_CHILDREN Linux maxrss includes waited descendants; distinct from parent-only sampler'}
pathlib.Path(sys.argv[1]).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));raise SystemExit(p.returncode)
