#!/usr/bin/env python3
"""NFC1 evaluation: reads the committed run logs of this folder and applies the decision rules that were registered
before the runs (docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md, sections 2-8a). VALIDATION only; TEST untouched.
usage: python3 evaluate.py [folder]   (prints the result tables used in section 10)
"""
import json
import re
import statistics as st
import sys
from pathlib import Path

D = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).parent)


def runs(name, epoch=6):
    """family -> {seed: valBPB} at [epoch] from RUN lines of a log."""
    out = {}
    for line in (D / name).read_text(encoding='utf-8').splitlines():
        m = re.match(r'RUN (\S+) seed=(\d) epoch=(\d+) .*?valBPB=([\d.]+)', line)
        if m and int(m.group(3)) == epoch:
            out.setdefault(m.group(1), {})[int(m.group(2))] = float(m.group(4))
    return out


def mean(d):
    return st.mean(d.values())


def separated(better, worse):
    """complete seed separation: every seed of [better] below every seed of [worse]"""
    return max(better.values()) < min(worse.values())


def rule(name, better, worse, min_gap):
    gap = mean(worse) - mean(better)
    ok = separated(better, worse) and gap >= min_gap
    return f'{name}: mean gap {gap:+.4f} BPB, complete separation {separated(better, worse)}, min gap {min_gap} -> ' + \
        ('SUPPORTED' if ok else 'NOT SUPPORTED')


def fmt(d):
    return f'{mean(d):.4f} ± {st.stdev(d.values()):.4f} ({", ".join(f"{d[s]:.4f}" for s in sorted(d))})'


print('== NF-2 language modelling, size S, DOC VALIDATION BPB at epoch 6 (3 seeds)')
lm = runs('nf2-lm-s.txt')
lm.update(runs('nf4-relu-l1-0.txt'))
lm_l1 = runs('nf4-relu-l1-1e-3.txt')
for fam in ('G-S', 'T-S', 'D-S', 'L-S', 'Dna-S', 'Dnb-S'):
    print(f'  {fam:6} {fmt(lm[fam])}')
print('  ' + rule('D better than G (pre-registered NF-2 a)', lm['D-S'], lm['G-S'], .02))
print('  ' + rule('G better than D (reverse, reported)', lm['G-S'], lm['D-S'], .02))
print('  ' + rule('D better than L (erase term)', lm['D-S'], lm['L-S'], .02))
print('  ' + rule('D better than Dna (decay gate)', lm['D-S'], lm['Dna-S'], .02))
print('  ' + rule('D better than Dnb (write gate)', lm['D-S'], lm['Dnb-S'], .02))

print('\n== NF-2 memory (MQAR), accuracy per number of pairs N (3 seeds)')
rec = {}
for line in (D / 'nf2-recall.txt').read_text(encoding='utf-8').splitlines():
    m = re.match(r'RECALL (\S+) seed=(\d) params=(\d+) stateFloats=(\d+) .*?acc4=([\d.]+) acc8=([\d.]+) acc16=([\d.]+) acc24=([\d.]+) acc32=([\d.]+)', line)
    if m:
        rec.setdefault(m.group(1), {'params': int(m.group(3)), 'state': int(m.group(4)), 'acc': {}})['acc'][int(m.group(2))] = \
            [float(m.group(i)) for i in range(5, 10)]
for fam, r in rec.items():
    a = r['acc']
    cols = ' '.join(f'N={n}: {st.mean(a[s][i] for s in a):.3f}' for i, n in enumerate((4, 8, 16, 24, 32)))
    print(f'  {fam:5} params {r["params"]:7} state {r["state"]:5} | {cols} | acc16 per seed {[a[s][2] for s in sorted(a)]}')
d16 = {s: rec['D-S']['acc'][s][2] for s in rec['D-S']['acc']}
g16 = {s: rec['G-S']['acc'][s][2] for s in rec['G-S']['acc']}
x16 = {s: rec['G-X']['acc'][s][2] for s in rec['G-X']['acc']}
print(f'  D-S >= G-S + 0.10 at N=16 in every seed (NF-2 b): {all(d16[s] >= g16[s] + .10 for s in d16)}  '
      f'(D-S {st.mean(d16.values()):.3f}, G-S {st.mean(g16.values()):.3f})')
print(f'  D-S >= G-X + 0.10 at N=16 in every seed (research question): {all(d16[s] >= x16[s] + .10 for s in d16)}  '
      f'(G-X {st.mean(x16.values()):.3f}, {rec["G-X"]["params"]} parameters vs D-S {rec["D-S"]["params"]})')

print('\n== NF-1 multi-speed memory: prequential CODE stream after DOC training (bases G-S and D-S, 3 seeds each)')
arms = {}
for line in (D / 'nf1-stream.txt').read_text(encoding='utf-8').splitlines():
    m = re.match(r'STREAM (\S+) base=(\S+) seed=(\d) streamBPB=([\d.]+) codeValBPB=([\d.]+) docValBPB=([\d.]+) updates=(\d+)', line)
    if m:
        arms.setdefault(m.group(2), {}).setdefault(m.group(1), {})[int(m.group(3))] = \
            dict(stream=float(m.group(4)), code=float(m.group(5)), doc=float(m.group(6)), updates=int(m.group(7)),
                 intact='baseUnchanged=true' in line or 'docValAdapterOff' not in line)
for base, a in arms.items():
    print(f'  base {base}')
    seeds = sorted(a['BASE'])
    for arm, v in a.items():
        gain = st.mean(a['BASE'][s]['stream'] - v[s]['stream'] for s in seeds)
        forget = st.mean(v[s]['doc'] - a['BASE'][s]['doc'] for s in seeds)
        print(f'    {arm:8} stream {st.mean(v[s]["stream"] for s in seeds):.4f} codeVal {st.mean(v[s]["code"] for s in seeds):.4f} '
              f'docVal {st.mean(v[s]["doc"] for s in seeds):.4f} | gain {gain:+.4f} forgetting {forget:+.4f} '
              f'updates {st.mean(v[s]["updates"] for s in seeds):.0f} base intact {all(v[s]["intact"] for s in seeds)}')
    def g(arm): return st.mean(a['BASE'][s]['stream'] - a[arm][s]['stream'] for s in seeds)
    def f(arm): return st.mean(a[arm][s]['doc'] - a['BASE'][s]['doc'] for s in seeds)
    def u(arm): return st.mean(a[arm][s]['updates'] for s in seeds)
    h1a = g('ADAPT') >= .5 * g('FT') and f('ADAPT') <= .25 * f('FT')
    h1b = g('SURPRISE') >= .8 * g('ADAPT') and u('SURPRISE') <= .6 * u('ADAPT')
    h1c = g('CARRY') > 0
    h1d = g('TWO') > max(g('CARRY'), g('ADAPT'))
    print(f'    H1a ADAPT >= 50 % of FT gain ({g("ADAPT"):.3f} vs {g("FT"):.3f}) and <= 25 % of FT forgetting '
          f'({f("ADAPT"):.3f} vs {f("FT"):.3f}): {"SUPPORTED" if h1a else "REFUTED"}')
    print(f'    H1b SURPRISE >= 80 % of ADAPT gain ({g("SURPRISE"):.3f} vs {g("ADAPT"):.3f}) with <= 60 % of updates '
          f'({u("SURPRISE"):.0f} vs {u("ADAPT"):.0f}): {"SUPPORTED" if h1b else "REFUTED"}')
    print(f'    H1c CARRY lowers stream BPB vs BASE (gain {g("CARRY"):+.4f}): {"SUPPORTED" if h1c else "REFUTED"}')
    print(f'    H1d TWO better than CARRY and ADAPT alone ({g("TWO"):.4f} vs {g("CARRY"):.4f} / {g("ADAPT"):.4f}): '
          f'{"SUPPORTED" if h1d else "REFUTED"}')

print('\n== NF-3 adaptive compute: byte-latent entropy patches vs fixed patches vs BPE (DOC VALIDATION, epoch 6)')
blt = runs('nf3-blt.txt')
speed_blt = {}
for line in (D / 'nf3-blt.txt').read_text(encoding='utf-8').splitlines():
    m = re.match(r'SPEED (\S+) seed=(\d) msPerByte=([\d.]+)', line)
    if m:
        speed_blt.setdefault(m.group(1), []).append(float(m.group(3)))
gm = runs('nf3-bpe-gm.txt')
for fam, v in list(blt.items()) + [('G-M (BPE)', gm['G-M']), ('G-S (BPE)', lm['G-S'])]:
    print(f'  {fam:14} {fmt(v)}' + (f'  ms/byte {st.mean(speed_blt[fam]):.4f} (under parallel load)' if fam in speed_blt else ''))
print('  ' + rule('entropy patches better than fixed patches', blt['BLT-entropy'], blt['BLT-fixed'], 0.0))
print('  ' + rule('entropy patches >= 0.03 better than BPE G-S', blt['BLT-entropy'], lm['G-S'], .03))
print('  ' + rule('entropy patches >= 0.03 better than BPE G-M', blt['BLT-entropy'], gm['G-M'], .03))

print('\n== NF-4 event-driven activity: fair single-thread timing (one process, 5 interleaved rounds)')
fair = {}
for line in (D / 'nf4-speed-fair.txt').read_text(encoding='utf-8').splitlines():
    m = re.match(r'SPEED-FAIR (\S+)/s(\d)/l1=([\d.]+) medianMsPerToken=([\d.]+)(?:.*ffnDensity=([\d.]+))?', line)
    if m:
        key = m.group(1) + ('' if m.group(1) != 'Gr-S' else f' l1={m.group(3)}')
        fair.setdefault(key, []).append((float(m.group(4)), float(m.group(5)) if m.group(5) else None))
for k, v in fair.items():
    dens = [x[1] for x in v if x[1] is not None]
    print(f'  {k:16} median ms/token {st.mean(x[0] for x in v):.4f}' + (f'  active FFN share {st.mean(dens):.3f}' if dens else ''))
g_ms = st.mean(x[0] for x in fair['G-S'])
for key, bpb in (('Gr-S l1=0.0', lm['Gr-S']), ('Gr-S l1=0.001', lm_l1['Gr-S'])):
    ms = st.mean(x[0] for x in fair[key])
    ok = mean(bpb) <= mean(lm['G-S']) + .02 and ms <= .85 * g_ms
    print(f'  {key}: BPB {mean(bpb):.4f} (G-S {mean(lm["G-S"]):.4f}, limit +0.02) ms/token {ms:.4f} '
          f'(limit {0.85 * g_ms:.4f} = 0.85 x G-S) -> {"SUCCESS" if ok else "NOT MET"}')
r0 = st.mean(x[0] for x in fair['Gr-S l1=0.0']); r1 = st.mean(x[0] for x in fair['Gr-S l1=0.001'])
print(f'  decomposition: GELU -> ReLU (same sparsity mechanism off) {1 - r0 / g_ms:+.1%} time; '
      f'sparsity alone (ReLU, active share 0.44 -> 0.13) {1 - r1 / r0:+.1%} time')

print('\n== NF-5 / NF-6a learning reasoner (14 regular tasks incl. 6 new, 3 adversarial; real coding jobs)')
rows = json.loads((D / 'nf5-results.json').read_text(encoding='utf-8'))
new = {'context-cost', 'json-digest', 'feature-bucket', 'word-evidence', 'memory-relation', 'memory-subject'}
res = {}
for c in ('random', 'lexical', 'docs', 'gated', 'ungated'):
    reg = [r for r in rows if r['condition'] == c and r['gold']]
    adv = [r for r in rows if r['condition'] == c and not r['gold']]
    res[c] = dict(at1=sum(r['correctAt1'] for r in reg), at3=sum(r['correctAt3'] for r in reg),
                  new=sum(r['correctAt1'] for r in reg if r['task'] in new),
                  unintended=sum(r['unintended'] for r in rows if r['condition'] == c),
                  adv=sum(bool(r['adversarialOk']) for r in adv), jobs=sum(r['jobs'] for r in rows if r['condition'] == c))
    print(f'  {c:8} correct@1 {res[c]["at1"]}/14 (new {res[c]["new"]}/6) correct@<=3 {res[c]["at3"]}/14 '
          f'unintended {res[c]["unintended"]} adversarial ok {res[c]["adv"]}/3 jobs {res[c]["jobs"]}')
docs_better = res['docs']['at1'] >= res['lexical']['at1'] + 2 and res['docs']['unintended'] == 0
nf6a = res['gated']['at1'] >= res['ungated']['at1'] and (res['gated']['unintended'] < res['ungated']['unintended'] or
                                                       res['gated']['at1'] > res['ungated']['at1'])
print(f'  docs better than lexical (>= 2 more @1, 0 unintended): {"SUPPORTED" if docs_better else "NOT SUPPORTED"}')
print(f'  NF-6a gated >= ungated @1 and (fewer unintended or more correct): {"SUPPORTED" if nf6a else "NOT SUPPORTED"}')

print('\n== NF-5b execution-guided pre-check (section 8d; same 17 tasks, lexical vs probe, real coding jobs)')
rows = json.loads((D / 'nf5b-results.json').read_text(encoding='utf-8'))
res = {}
for c in ('lexical', 'probe'):
    reg = [r for r in rows if r['condition'] == c and r['gold']]
    adv = [r for r in rows if r['condition'] == c and not r['gold']]
    res[c] = dict(at1=sum(r['correctAt1'] for r in reg), at3=sum(r['correctAt3'] for r in reg),
                  new=sum(r['correctAt1'] for r in reg if r['task'] in new),
                  unintended=[r['task'] for r in rows if r['condition'] == c and r['unintended']],
                  adv=sum(bool(r['adversarialOk']) for r in adv), jobs=sum(r['jobs'] for r in rows if r['condition'] == c),
                  seconds=sum(r['ms'] for r in rows if r['condition'] == c) / 1000)
    print(f'  {c:8} correct@1 {res[c]["at1"]}/14 (new {res[c]["new"]}/6) correct@<=3 {res[c]["at3"]}/14 '
          f'unintended {len(res[c]["unintended"])} {res[c]["unintended"]} adversarial ok {res[c]["adv"]}/3 '
          f'jobs {res[c]["jobs"]} wall {res[c]["seconds"]:.1f} s')
gain = res['probe']['at1'] - res['lexical']['at1']
checks = [('probe@1 >= lexical@1 + 3', gain >= 3), ('no unintended confirmation', not res['probe']['unintended']),
          ('3/3 correct abstentions', res['probe']['adv'] == 3)]
for name, ok in checks: print(f'  {name}: {"met" if ok else "VIOLATED"}')
print(f'  NF-5b: {"SUPPORTED" if all(ok for _, ok in checks) else "REFUTED"} (gain @1 {gain:+d} of 14)')

print('\n== NF-6b-lite count-anchored mixture (section 8c; byte minGRU + KN-7, 2-fold cross-fitting on DOC VALIDATION)')
mix = {}
for line in (D / 'nf6b-hybrid.txt').read_text(encoding='utf-8').splitlines():
    if line.startswith('HYBRID G-'):
        f = dict(re.findall(r'(\w+)=([-\d.]+)', line)); mix.setdefault(line.split()[1], []).append(f)
for fam, rs in mix.items():
    kn = [float(r['knBPB']) for r in rs]; fixed = [float(r['fixedBPB']) for r in rs]
    anchored = [float(r['anchoredBPB']) for r in rs]; neural = [float(r['neuralBPB']) for r in rs]
    h1 = all(k - x >= 0.05 for k, x in zip(kn, fixed)); h2 = all(x - a >= 0.02 for x, a in zip(fixed, anchored))
    print(f'  {fam}-b seeds {len(rs)} neural {st.mean(neural):.4f} KN-7 {kn[0]:.4f} fixed {" ".join(f"{x:.4f}" for x in fixed)} '
          f'anchored {" ".join(f"{a:.4f}" for a in anchored)}')
    print(f'    gain fixed vs KN-7 {" ".join(f"{k - x:+.4f}" for k, x in zip(kn, fixed))} -> H6b-1 {"SUPPORTED" if h1 else "REFUTED"}; '
          f'gain anchored vs fixed {" ".join(f"{x - a:+.4f}" for x, a in zip(fixed, anchored))} -> H6b-2 {"SUPPORTED" if h2 else "REFUTED"}')
