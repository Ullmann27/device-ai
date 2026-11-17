import re, sys, statistics, collections
runs = collections.defaultdict(lambda: collections.defaultdict(dict))  # family -> epoch -> seed -> (bpb, ckpt)
speed = collections.defaultdict(list); params = {}; base = {}; cdx = collections.defaultdict(dict)
for line in open(sys.argv[1], encoding='utf-8'):
    m = re.match(r'RUN (\S+) seed=(\d+) epoch=(\d+) .*valBPB=([\d.]+) params=(\d+) .*ckpt=(\w+)', line)
    if m:
        f, s, e, v, p, c = m.groups(); runs[f][int(e)][int(s)] = (float(v), c); params[f] = int(p); continue
    m = re.match(r'SPEED (\S+) seed=\d+ msPerToken=([\d.]+)', line)
    if m: speed[m.group(1)].append(float(m.group(2))); continue
    m = re.match(r'BASE (\S+) valBPB=([\d.]+)', line)
    if m: base[m.group(1)] = float(m.group(2)); continue
    m = re.match(r'CDX1 seed=(\d+) epoch=(\d+) valBPB=([\d.]+)', line)
    if m: cdx[int(m.group(2))][int(m.group(1))] = float(m.group(3))
def ms(xs): return (statistics.mean(xs), statistics.stdev(xs) if len(xs) > 1 else 0.0)
print('BASELINES', ' '.join(f'{k}={v:.4f}' for k, v in base.items()))
best = {e: ms(list(d.values())) for e, d in cdx.items()}
if best:
    e = min(best, key=lambda k: best[k][0]); print(f'CDX1 best epoch {e}: {best[e][0]:.4f} ± {best[e][1]:.4f} (seeds {len(cdx[e])})')
for f, epochs in runs.items():
    stats = {e: ms([v for v, _ in d.values()]) for e, d in epochs.items() if d}
    e = min(stats, key=lambda k: stats[k][0])
    seeds = sorted(epochs[e])
    print(f'{f}: params={params[f]} bestEpoch={e} valBPB={stats[e][0]:.4f} ± {stats[e][1]:.4f} seeds={seeds} msPerToken={statistics.mean(speed[f]) if speed[f] else float("nan"):.3f} '
          f'ckpts=' + ','.join(epochs[e][s][1][:12] for s in seeds))
