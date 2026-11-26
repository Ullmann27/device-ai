"""Experiment: strip forearm/hand influence from cloak islands of a Meshy body, harmonic fill."""
import sys, collections, numpy as np
from pathlib import Path
sys.path.insert(0, "/home/user/imperathen/tools/characters")
import smooth_frontier_skirt_seam as seam
from ground_death_clip import Glb, Rig

BELOW_HAND_M = .15
HAND_CORE = .9
WELD = 1e-5
SEAM_SHARE = .5
import os
GROW = os.environ.get('GROW', '1') == '1'

def islands(n, tris):
    parent = np.arange(n)
    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]; a = parent[a]
        return a
    for t in tris:
        for a, b in ((t[0], t[1]), (t[1], t[2])):
            ra, rb = find(a), find(b)
            if ra != rb: parent[ra] = rb
    return np.array([find(v) for v in range(n)])

def weights_matrix(prim, n, j):
    W = np.zeros((n, j))
    for s in range(4): np.add.at(W, (np.arange(n), prim["joint_index"][:, s]), prim["weight"][:, s])
    return W

def plan(glb, rig, p=0, fill_iters=400):
    prim = rig.primitives[p]
    sib = [q for q, pr in enumerate(rig.primitives) if pr["node"] == prim["node"]]
    tris = seam._triangles(glb, prim["node"])[sib.index(p)].reshape(-1, 3)
    rest = seam._pose(prim, rig.globals({"tracks": {}}, 0.), prim["joint_index"], prim["weight"])
    names = [rig.nodes[j]["name"] for j in prim["joints"]]
    W = weights_matrix(prim, len(rest), len(names))
    fh = [k for k, n in enumerate(names) if n.split("_")[0] in ("Forearm", "Hand") and n[-2:] in ("_L", "_R")]
    fhw = W[:, fh].sum(1)
    root = islands(len(rest), tris)
    lowest_hand = {}
    report = {"cloak_islands": [], "changed_vertices": 0}
    cloak = np.zeros(len(rest), bool)
    for side in "LR":
        k = [names.index(x) for x in ("Forearm_" + side, "Hand_" + side) if x in names]
        core = W[:, k].sum(1) > HAND_CORE
        lowest_hand[side] = float(rest[core, 1].min())
    floor = min(lowest_hand.values()) - BELOW_HAND_M
    for i in sorted(set(root[fhw > 0].tolist())):
        m = np.flatnonzero(root == i)
        if rest[m, 1].min() < floor:
            cloak[m] = True
            report["cloak_islands"].append({"island": int(i), "vertices": int(len(m)), "forearm_hand_sum": round(float(fhw[m].sum()), 3)})
    _, weld = np.unique(np.round(rest / WELD).astype(np.int64), axis=0, return_inverse=True); weld = weld.reshape(-1)
    size = int(weld.max()) + 1
    multiplicity = np.bincount(weld, minlength=size)
    island_ids = sorted(set(root.tolist()))
    cloak_node = np.zeros(size, bool); cloak_node[weld[cloak]] = True
    if GROW:
        for i in sorted(set(root[~cloak & (fhw > 0)].tolist())):
            m = np.flatnonzero(root == i)
            nodes = np.unique(weld[m]); seam_nodes = nodes[multiplicity[nodes] > 1]
            if len(seam_nodes) and cloak_node[seam_nodes].mean() >= SEAM_SHARE:
                cloak[m] = True
                report["cloak_islands"].append({"island": int(i), "vertices": int(len(m)), "forearm_hand_sum": round(float(fhw[m].sum()), 3), "joined_by_seam": True})
    unknown_v = cloak & (fhw > 0)
    node_unknown = np.zeros(size, bool); node_unknown[weld[unknown_v]] = True
    report["seam_duplicates_outside_cloak"] = int((node_unknown[weld] & ~cloak).sum())
    unknown_v = node_unknown[weld]
    report["changed_vertices"] = int(unknown_v.sum())
    # Jacobi harmonic fill on welded nodes
    Wn = np.zeros((size, len(names))); cnt = np.zeros(size)
    np.add.at(Wn, weld, W); np.add.at(cnt, weld, 1); Wn /= cnt[:, None]
    pts = np.zeros((size, 3)); np.add.at(pts, weld, rest); pts /= cnt[:, None]
    unknown = np.zeros(size, bool); unknown[weld[unknown_v]] = True
    start = Wn.copy(); start[:, fh] = 0
    keep = start.sum(1) > 1e-6
    start[keep] /= start[keep].sum(1, keepdims=True)
    Wn[unknown] = start[unknown]
    e = seam._edges(weld[tris[cloak[tris].all(1)]].reshape(-1))
    a, b = e[:, 0], e[:, 1]
    infl = 1. / np.maximum(.003, np.linalg.norm(pts[a] - pts[b], axis=1))
    tot = np.zeros(size); np.add.at(tot, a, infl); np.add.at(tot, b, infl)
    act = unknown & (tot > 0)
    report["isolated_unknown"] = int((unknown & (tot == 0)).sum())
    for _ in range(fill_iters):
        nb = np.zeros_like(Wn)
        np.add.at(nb, a, Wn[b] * infl[:, None]); np.add.at(nb, b, Wn[a] * infl[:, None])
        Wn[act] = nb[act] / tot[act, None]
    newW = W.copy(); newW[unknown_v] = Wn[weld[unknown_v]]
    chosen = np.argsort(newW, axis=1)[:, -4:]
    wt = np.take_along_axis(newW, chosen, axis=1); wt /= wt.sum(1, keepdims=True)
    J = prim["joint_index"].copy(); Wt = prim["weight"].copy()
    J[unknown_v] = chosen[unknown_v]; Wt[unknown_v] = wt[unknown_v]
    report["floor_y"] = round(floor, 4); report["lowest_hand"] = lowest_hand
    return prim, tris, rest, J, Wt, report

def tears(glb, rig, prim, tris, rest, J, Wt, clips=None):
    edges = seam._edges(tris.reshape(-1))
    rl = np.linalg.norm(rest[edges[:, 0]] - rest[edges[:, 1]], axis=1)
    out = {}
    for anim in glb.document.get("animations", []):
        c = anim["name"]
        if clips and c not in clips: continue
        clip = rig.animation(c); n = max(2, int(np.ceil(clip["duration"] * 60)))
        cnt, worst = 0, 0.
        for f in range(n + 1):
            posed = seam._pose(prim, rig.globals(clip, clip["duration"] * f / n), J, Wt)
            L = np.linalg.norm(posed[edges[:, 0]] - posed[edges[:, 1]], axis=1)
            ex = L - (rl * 7 + .03); cnt += int((ex > 0).sum()); worst = max(worst, float(ex.max()))
        out[c] = (cnt, round(worst, 4))
    return out

if __name__ == "__main__":
    import json
    for path in sys.argv[1:]:
        glb = Glb(Path(path)); rig = Rig(glb)
        prim, tris, rest, J, Wt, rep = plan(glb, rig)
        print(Path(path).name, json.dumps(rep))
        before = tears(glb, rig, prim, tris, rest, prim["joint_index"], prim["weight"])
        after = tears(glb, rig, prim, tris, rest, J, Wt)
        for c in before:
            if before[c] != after[c] or before[c][0]: print("   %-8s before %s after %s" % (c, before[c], after[c]))
