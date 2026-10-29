#!/usr/bin/env python3
"""ARGON4 F2 - how much of a mixture-of-experts model must stream from flash per token, for a given RAM cache?

SIMULATION, NOT A MEASUREMENT. Routing traces are synthetic (Zipf popularity + topic drift + token persistence);
real router logs (e.g. OLMoE/Mixtral/DeepSeek on the PC worker) must replace them before any planning number is used.

Policies per layer, with a cache of C experts:
  LRU        least recently used
  LFU        least frequently used (with decay)
  PREFETCH   LRU + Markov prefetch: an online transition table (expert now -> expert next token) predicts the next
             token's experts; the top m missing ones are fetched ahead. Wrong prefetches still cost bandwidth and may
             evict useful experts, so we count total fetches and stall fetches separately.
  BELADY     offline optimum (evicts the expert needed furthest in the future): an UPPER BOUND for any cache policy
The gap LRU -> BELADY is the most any predictor can win; if it is small, building a predictor is wasted work.

Usage: python3 -I scripts/argon4/expert_cache_sim.py [out.json]
"""
import json
import math
import random
import sys
from collections import OrderedDict, defaultdict


def trace(tokens=3000, experts=256, k=8, topics=12, zipf=1.1, persistence=0.35, drift=0.02, seed=1):
    """One layer's routing trace: list of k-sets. Deterministic for a seed."""
    rnd = random.Random(seed)
    base = [1.0 / (i + 1) ** zipf for i in range(experts)]
    order = list(range(experts)); rnd.shuffle(order)
    pop = [0.0] * experts
    for rank, e in enumerate(order):
        pop[e] = base[rank]
    topic_pref = [[rnd.random() ** 4 for _ in range(experts)] for _ in range(topics)]
    topic = rnd.randrange(topics)
    out, prev = [], []
    for _ in range(tokens):
        if rnd.random() < drift:
            topic = rnd.randrange(topics)
        chosen = set()
        for e in prev:
            if rnd.random() < persistence and len(chosen) < k:
                chosen.add(e)
        weights = [pop[e] * (0.2 + topic_pref[topic][e]) for e in range(experts)]
        total = sum(weights)
        while len(chosen) < k:
            r, acc = rnd.random() * total, 0.0
            for e, w in enumerate(weights):
                acc += w
                if acc >= r:
                    chosen.add(e)
                    break
        prev = sorted(chosen)
        out.append(prev)
    return out


def simulate(tr, capacity, policy, prefetch_m=4):
    assert capacity >= 1
    hits = misses = prefetched = stalls = 0
    cache = OrderedDict()
    freq = defaultdict(float)
    trans = defaultdict(lambda: defaultdict(float))
    future = defaultdict(list)
    if policy == "BELADY":
        for t, s in enumerate(tr):
            for e in s:
                future[e].append(t)
        pointer = defaultdict(int)

    def next_use(e, t):
        lst = future[e]
        i = pointer[e]
        while i < len(lst) and lst[i] <= t:
            i += 1
        pointer[e] = i
        return lst[i] if i < len(lst) else math.inf

    def evict(t, protect):
        if policy == "LFU":
            victim = min((e for e in cache if e not in protect), key=lambda e: freq[e])
        elif policy == "BELADY":
            victim = max((e for e in cache if e not in protect), key=lambda e: next_use(e, t))
        else:
            victim = next(e for e in cache if e not in protect)
        del cache[victim]

    for t, s in enumerate(tr):
        for e in freq:
            freq[e] *= 0.995
        for e in s:
            freq[e] += 1.0
            if e in cache:
                hits += 1
                cache.move_to_end(e)
            else:
                misses += 1
                stalls += 1
                if len(cache) >= capacity:
                    evict(t, set(s))
                cache[e] = True
        if policy == "PREFETCH":
            if t > 0:
                for a in tr[t - 1]:
                    for b in s:
                        trans[a][b] += 1.0
            if t + 1 < len(tr):
                score = defaultdict(float)
                for a in s:
                    for b, c in trans[a].items():
                        score[b] += c
                guesses = [e for e, _ in sorted(score.items(), key=lambda kv: -kv[1]) if e not in cache][:prefetch_m]
                for e in guesses:
                    if len(cache) >= capacity:
                        if all(x in s for x in cache):
                            break
                        evict(t, set(s))
                    cache[e] = True
                    prefetched += 1
    n = len(tr) * len(tr[0])
    return {"policy": policy, "capacity": capacity, "hit_rate": round(hits / n, 4), "fetches_per_token": round((misses + prefetched) / len(tr), 3),
            "stall_fetches_per_token": round(stalls / len(tr), 3)}


def sweep():
    rows = []
    for persistence in (0.0, 0.35, 0.7):
        tr = trace(persistence=persistence)
        for share in (0.01, 0.05, 0.1, 0.25, 0.5):
            cap = max(8, int(256 * share))
            for policy in ("LRU", "LFU", "PREFETCH", "BELADY"):
                r = simulate(tr, cap, policy)
                r.update(persistence=persistence, cache_share=share)
                rows.append(r)
    return {"schema": "argon4-f2-expert-cache-sim-v1", "synthetic": True,
            "trace": {"tokens": 3000, "experts": 256, "k": 8, "topics": 12, "zipf": 1.1, "drift": 0.02}, "rows": rows}


def main():
    result = sweep()
    if len(sys.argv) > 1:
        with open(sys.argv[1], "w", encoding="utf-8") as f:
            f.write(json.dumps(result, indent=1) + "\n")
    for r in result["rows"]:
        print(f'persist {r["persistence"]:.2f} | cache {r["cache_share"]:>4} ({r["capacity"]:3d} exp) | {r["policy"]:8} | '
              f'hit {r["hit_rate"]:.3f} | fetch/tok {r["fetches_per_token"]:6.2f} | stall/tok {r["stall_fetches_per_token"]:6.2f}')


if __name__ == "__main__":
    main()
