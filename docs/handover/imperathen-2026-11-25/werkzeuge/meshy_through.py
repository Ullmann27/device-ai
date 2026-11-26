"""Body points coming out through the cloak's outer surface, before vs after (all clips, 60 Hz)."""
import sys, json, numpy as np
from pathlib import Path

def nearest(ref, q, chunk=2048):
    idx = np.empty(len(q), np.int64); dist = np.empty(len(q))
    for i in range(0, len(q), chunk):
        d2 = ((q[i:i+chunk, None, :] - ref[None, :, :]) ** 2).sum(2)
        idx[i:i+chunk] = d2.argmin(1); dist[i:i+chunk] = np.sqrt(d2.min(1))
    return dist, idx
import meshy_cloak_fix as cf, smooth_frontier_skirt_seam as seam
from ground_death_clip import Glb, Rig

def skin_normals(prim, world, J, Wt, normals):
    mats = np.stack([world[j] @ prim["ibm"][k] for k, j in enumerate(prim["joints"])])[:, :3, :3]
    out = np.zeros_like(normals)
    for s in range(4):
        out += Wt[:, s, None] * np.einsum("nij,nj->ni", mats[J[:, s]], normals)
    return out / np.maximum(1e-9, np.linalg.norm(out, axis=1))[:, None]

def run(path):
    glb = Glb(Path(path)); rig = Rig(glb)
    prim, tris, rest, J, Wt, rep = cf.plan(glb, rig)
    doc = glb.document
    normals = glb.accessor(doc["meshes"][doc["nodes"][prim["node"]]["mesh"]]["primitives"][0]["attributes"]["NORMAL"])
    root = cf.islands(len(rest), tris)
    cl = np.isin(root, [i["island"] for i in rep["cloak_islands"]])
    radial = rest.copy(); radial[:, 1] = 0; radial /= np.maximum(1e-9, np.linalg.norm(radial, axis=1))[:, None]
    rest_world = rig.globals({"tracks": {}}, 0.)
    n0 = skin_normals(prim, rest_world, prim["joint_index"], prim["weight"], normals)
    outer = np.flatnonzero(cl & ((n0 * radial).sum(1) > .3) & (rest[:, 1] > .7))
    body = np.flatnonzero(~cl)
    d, k = nearest(rest[outer], rest[body])
    c = outer[k]; off0 = ((rest[body] - rest[c]) * n0[c]).sum(1)
    pts = body[(d < .06) & (off0 < 0)]
    res = {}
    for label, (JJ, WW) in (("before", (prim["joint_index"], prim["weight"])), ("after", (J, Wt))):
        per = {}
        for anim in doc["animations"]:
            clip = rig.animation(anim["name"]); n = max(2, int(np.ceil(clip["duration"] * 60)))
            worst, count = 0., 0
            for f in range(n + 1):
                world = rig.globals(clip, clip["duration"] * f / n)
                posed = seam._pose(prim, world, JJ, WW)
                nn = skin_normals(prim, world, JJ, WW, normals)
                _, kk = nearest(posed[outer], posed[pts]); cc = outer[kk]
                dv = posed[pts] - posed[cc]; off = (dv * nn[cc]).sum(1)
                lat = np.linalg.norm(dv - off[:, None] * nn[cc], axis=1)
                hit = (off > .002) & (lat < .02)
                count += int(hit.sum()); worst = max(worst, float(off[hit].max()) if hit.any() else 0.)
            per[anim["name"]] = (count, round(worst, 4))
        res[label] = per
    return len(outer), len(pts), res

if __name__ == '__main__':
  for p in sys.argv[1:]:
    o, n, res = run(p)
    print(Path(p).name, "outer cloak vertices", o, "body points tracked", n)
    for c in res["before"]:
        print("   %-7s before %-16s after %s" % (c, res["before"][c], res["after"][c]))
