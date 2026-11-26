"""Scan MeshyBody (and other primitives) for gate-criterion tearing on raw GLB clips."""
import sys, json, collections, numpy as np
from pathlib import Path
sys.path.insert(0, "/home/user/imperathen/tools/characters")
import smooth_frontier_skirt_seam as seam
from ground_death_clip import Glb, Rig

def scan(path):
    glb = Glb(path); rig = Rig(glb); doc = glb.document
    out = {}
    clips = [a["name"] for a in doc.get("animations", [])]
    rest_world = rig.globals({"tracks": {}}, 0.)
    nodes_seen = {}
    for p, prim in enumerate(rig.primitives):
        sib = [q for q, pr in enumerate(rig.primitives) if pr["node"] == prim["node"]]
        tris = seam._triangles(glb, prim["node"])[sib.index(p)]
        edges = seam._edges(tris)
        rest = seam._pose(prim, rest_world, prim["joint_index"], prim["weight"])
        rl = np.linalg.norm(rest[edges[:, 0]] - rest[edges[:, 1]], axis=1)
        names = [rig.nodes[j]["name"] for j in prim["joints"]]
        per = {}
        torn = collections.Counter()
        for c in clips:
            clip = rig.animation(c)
            n = max(2, int(np.ceil(clip["duration"] * 60)))
            cnt = 0
            for f in range(n + 1):
                w = rig.globals(clip, clip["duration"] * f / n)
                posed = seam._pose(prim, w, prim["joint_index"], prim["weight"])
                L = np.linalg.norm(posed[edges[:, 0]] - posed[edges[:, 1]], axis=1)
                bad = L > rl * 7 + .03
                cnt += int(bad.sum())
                for e in np.flatnonzero(bad): torn[tuple(edges[e])] += 1
            if cnt: per[c] = cnt
        W = np.zeros((len(rest), len(prim["joints"])))
        for s in range(4): np.add.at(W, (np.arange(len(rest)), prim["joint_index"][:, s]), prim["weight"][:, s])
        dom = W.argmax(1)
        pairs = collections.Counter((names[dom[a]], names[dom[b]]) for a, b in torn)
        out[f"{prim['name']}#{p}"] = {"verts": len(rest), "per_clip": per, "torn_edges": len(torn), "dominant_pairs": pairs.most_common(8)}
    return out

for path in sys.argv[1:]:
    print(Path(path).name, json.dumps(scan(Path(path)), default=str))
