#!/usr/bin/env python3
"""Read-only R8/R9 raw CSV audit; does not rerun timings."""
import argparse, csv, hashlib, io, json, statistics, tarfile
from collections import defaultdict
from pathlib import Path
EXPECTED = {
 'R8': ('464c8c1e676d12640c5b737c6a34f06d3837307e274e8adb5035dbf4d22a02ac','StepperBench.kt','aa.csv','ab.csv','f05adccac2da23451b1edcc1ef9d9837229486ed3b4505445582a0f62169c5c8'),
 'R9': ('cefa10199763df4033abf0b221e35837a3eb161052a4d3e734d60c5645f0738c','R9Bench.kt','r9-aa.csv','r9-ab.csv','3694fcce8f0cb7fd8c20e694f463173ba951b139744393829eac79558cc18800')}
def parse(tar, filename):
 rows = list(csv.DictReader(io.StringIO(tar.extractfile(filename).read().decode())))
 assert len(rows) == 156, filename
 cases = defaultdict(dict)
 for r in rows:
  p, s, i, a = r['phase'],r['scenario'],int(r['pair']),r['arm']
  assert p in ('warmup','measured') and a in ('A','B') and int(r['cpu_ns'])>=0 and int(r['alloc_bytes'])>=0
  key = (p,i,a)
  assert key not in cases[s]
  cases[s][key]=r
 assert len(cases)==6
 for d in cases.values():
  assert len(d)==26 and all((phase,i,arm) in d for phase,rr in [('warmup',range(4)),('measured',range(4,13))] for i in rr for arm in 'AB')
 return cases
def stats(cases):
 out={}
 for s,d in sorted(cases.items()):
  a=[d['measured',i,'A'] for i in range(4,13)]; b=[d['measured',i,'B'] for i in range(4,13)]
  assert all(x['checksum']==y['checksum'] for x,y in zip(a,b))
  med=lambda rows,key: statistics.median(int(r[key]) for r in rows)
  ca,cb=med(a,'cpu_ns'),med(b,'cpu_ns'); xa,xb=med(a,'alloc_bytes'),med(b,'alloc_bytes')
  out[s]={'cpu_A':ca,'cpu_B':cb,'cpu_B_delta_pct':round(100*(cb/ca-1),2),'cpu_B_wins_of_9':sum(int(y['cpu_ns'])<int(x['cpu_ns']) for x,y in zip(a,b)), 'alloc_A':xa,'alloc_B':xb,'alloc_B_delta_pct':round(100*(xb/xa-1),2), 'checksum':a[0]['checksum']}
 return out
def audit(label, archive):
 sha,source,aa,ab,source_sha=EXPECTED[label]
 data=Path(archive).read_bytes()
 assert hashlib.sha256(data).hexdigest()==sha
 with tarfile.open(fileobj=io.BytesIO(data),mode='r:xz') as tar:
  assert sorted(tar.getnames())==sorted([source,aa,ab])
  assert hashlib.sha256(tar.extractfile(source).read()).hexdigest()==source_sha
  a,b=stats(parse(tar,aa)),stats(parse(tar,ab))
 assert a.keys()==b.keys() and all(a[s]['checksum']==b[s]['checksum'] for s in a)
 drift={s:(a[s]['alloc_A'],b[s]['alloc_A']) for s in a if a[s]['alloc_A']!=b[s]['alloc_A']}
 return {'status':'PASS','archive_sha256':sha,'source_sha256':source_sha,'rows':312,'measured':216,'warmup':96,'AA':a,'AB':b,'baseline_allocation_drift':drift}
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('r8_archive');p.add_argument('r9_archive');p.add_argument('--output');args=p.parse_args()
 result={'method':'offline raw-data recomputation; no timed rerun','R8':audit('R8',args.r8_archive),'R9':audit('R9',args.r9_archive)}
 out=json.dumps(result,indent=2,sort_keys=True)+'\n'
 if args.output: Path(args.output).write_text(out,encoding='utf-8');print('PASS: 624 original CSV rows, 432 measured, 192 warmup; saved',args.output)
 else: print(out)
