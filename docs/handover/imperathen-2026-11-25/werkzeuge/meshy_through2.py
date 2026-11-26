import sys, numpy as np
from pathlib import Path
import meshy_cloak_fix as cf, meshy_through as th, smooth_frontier_skirt_seam as seam
from ground_death_clip import Glb, Rig

def setup(glb, rig):
    prim, tris, rest, _, _, rep = cf.plan(glb, rig)
    doc = glb.document
    normals = glb.accessor(doc["meshes"][doc["nodes"][prim["node"]]["mesh"]]["primitives"][0]["attributes"]["NORMAL"])
    root = cf.islands(len(rest), tris)
    cl = np.isin(root, [i["island"] for i in rep["cloak_islands"]])
    radial = rest.copy(); radial[:, 1] = 0; radial /= np.maximum(1e-9, np.linalg.norm(radial, axis=1))[:, None]
    n0 = th.skin_normals(prim, rig.globals({"tracks": {}}, 0.), prim["joint_index"], prim["weight"], normals)
    outer = np.flatnonzero(cl & ((n0 * radial).sum(1) > .3) & (rest[:, 1] > .7))
    body = np.flatnonzero(~cl)
    d, k = th.nearest(rest[outer], rest[body]); c = outer[k]
    off0 = ((rest[body] - rest[c]) * n0[c]).sum(1)
    return prim, tris, rest, normals, outer, body[(d < .06) & (off0 < 0)]

def measure(glb, rig, prim, normals, outer, pts, J, Wt):
    per = {}
    for anim in glb.document["animations"]:
        clip = rig.animation(anim["name"]); n = max(2, int(np.ceil(clip["duration"] * 60)))
        worst, count = 0., 0
        for f in range(n + 1):
            world = rig.globals(clip, clip["duration"] * f / n)
            posed = seam._pose(prim, world, J, Wt); nn = th.skin_normals(prim, world, J, Wt, normals)
            _, kk = th.nearest(posed[outer], posed[pts]); cc = outer[kk]
            dv = posed[pts] - posed[cc]; off = (dv * nn[cc]).sum(1)
            lat = np.linalg.norm(dv - off[:, None] * nn[cc], axis=1); hit = (off > .002) & (lat < .02)
            count += int(hit.sum()); worst = max(worst, float(off[hit].max()) if hit.any() else 0.)
        per[anim["name"]] = (count, round(worst, 4))
    return per

if __name__ == "__main__":
    import meshy_smooth_exp  # noqa (guarded below)
