import sys, json, numpy as np
from pathlib import Path
import meshy_cloak_fix as cf, meshy_through as th, smooth_frontier_skirt_seam as seam
from ground_death_clip import Glb, Rig

def torn_vertices(glb, rig, prim, tris, rest, J, Wt):
    edges = seam._edges(tris.reshape(-1)); rl = np.linalg.norm(rest[edges[:,0]]-rest[edges[:,1]],axis=1)
    torn, count = set(), {}
    for anim in glb.document["animations"]:
        clip = rig.animation(anim["name"]); n = max(2, int(np.ceil(clip["duration"]*60))); c = 0
        for f in range(n+1):
            posed = seam._pose(prim, rig.globals(clip, clip["duration"]*f/n), J, Wt)
            L = np.linalg.norm(posed[edges[:,0]]-posed[edges[:,1]],axis=1); bad = L > rl*7+.03
            c += int(bad.sum()); torn.update(edges[bad].reshape(-1).tolist())
        if c: count[anim["name"]] = c
    return sorted(torn), count

path = Path(sys.argv[1]); rings = [int(x) for x in sys.argv[2].split(",")]; passes = [int(x) for x in sys.argv[3].split(",")]
glb = Glb(path); rig = Rig(glb); prim = rig.primitives[0]
tris = seam._triangles(glb, prim["node"])[0]
rest = seam._pose(prim, rig.globals({"tracks": {}}, 0.), prim["joint_index"], prim["weight"])
torn, count = torn_vertices(glb, rig, prim, tris, rest, prim["joint_index"], prim["weight"])
print("before tearing", count, "torn vertices", len(torn))
for r in rings:
    for p in passes:
        seam.RINGS, seam.PASSES = r, p
        J, Wt, region = seam._smoothed(prim, rest, tris, torn)
        _, c2 = torn_vertices(glb, rig, prim, tris, rest, J, Wt)
        print(f"rings {r} passes {p} region {region} tearing {c2}", flush=True)
