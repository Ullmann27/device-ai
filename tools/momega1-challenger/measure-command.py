#!/usr/bin/env python3
"""Native child-tree rusage of an explicitly supplied reviewer command."""
import json,pathlib,resource,subprocess,sys,time
if len(sys.argv)<3:raise SystemExit('usage: output.json command [args...]')
start=time.monotonic();p=subprocess.run(sys.argv[2:]);r=resource.getrusage(resource.RUSAGE_CHILDREN)
result={'command':sys.argv[2:],'exit':p.returncode,'elapsedSeconds':round(time.monotonic()-start,3),'maxRssKiB':r.ru_maxrss,'userCpuSeconds':r.ru_utime,'systemCpuSeconds':r.ru_stime,'minorFaults':r.ru_minflt,'majorFaults':r.ru_majflt,'measurement':'WRAPPER_ONLY_NOT_MODEL_RSS: bwrap nested native process not included in this environment'}
pathlib.Path(sys.argv[1]).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result));raise SystemExit(p.returncode)
