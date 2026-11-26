"""Compare skirt weight variants: gate tearing AND leg-through-skirt clipping (raw clips, 60 Hz)."""
import sys, math, json, numpy as np
from scipy.spatial import cKDTree
sys.path.insert(0, "/home/user/imperathen/tools/characters")
import refit_frontier_cloth as rf
from ground_death_clip import Glb, Rig
from pathlib import Path


def skin(prim, world, joints, weights):
    jm = np.stack([world[j] @ prim["ibm"][k] for k, j in enumerate(prim["joints"])])
    pos = np.c_[prim["position"], np.ones(len(prim["position"]))]
    out = np.zeros((len(pos), 3))
    for s in range(4):
        out += weights[:, s, None] * np.einsum("nij,nj->ni", jm[joints[:, s]], pos)[:, :3]
    return out


def normals(points, tris):
    t = tris.reshape(-1, 3)
    fn = np.cross(points[t[:, 1]] - points[t[:, 0]], points[t[:, 2]] - points[t[:, 0]])
    n = np.zeros_like(points)
    for k in range(3):
        np.add.at(n, t[:, k], fn)
    return n / np.maximum(1e-12, np.linalg.norm(n, axis=1))[:, None]


def run(path, variants):
    glb = Glb(path); rig = Rig(glb)
    world0 = rig.globals({"tracks": {}}, 0.)
    rest = rig.skin(world0)
    by_node = {}
    for p, prim in enumerate(rig.primitives):
        by_node.setdefault(prim["node"], []).append(p)
    tris = {}
    for node, ps in by_node.items():
        for p, t in zip(ps, rf._triangles(glb, node)):
            tris[p] = t
    skirt = next(p for p, pr in enumerate(rig.primitives) if pr["name"] == "HeroTunicSkirt")
    body = next(p for p, pr in enumerate(rig.primitives) if pr["name"] == "HeroBody")
    bp = rig.primitives[body]
    names = [rig.nodes[j]["name"] for j in bp["joints"]]
    legw = np.zeros(len(bp["position"]))
    for s in range(4):
        legw += np.where(np.isin([names[j] for j in bp["joint_index"][:, s]], ["Thigh_L", "Thigh_R", "Shin_L", "Shin_R"]), bp["weight"][:, s], 0)
    ylo, yhi = rest[skirt][:, 1].min(), rest[skirt][:, 1].max()
    legs = np.flatnonzero((legw > .5) & (rest[body][:, 1] > ylo + .01) & (rest[body][:, 1] < yhi - .05))
    # rest clearance per leg vertex (signed distance to nearest skirt vertex along its normal)
    def clearance(sk, bd, tri):
        tree = cKDTree(sk); dist, idx = tree.query(bd[legs])
        n = normals(sk, tri)
        return ((bd[legs] - sk[idx]) * n[idx]).sum(axis=1), dist
    rest_d, _ = clearance(rest[skirt], rest[body], tris[skirt])
    edges = rf._edges(tris[skirt])
    rl = np.linalg.norm(rest[skirt][edges[:, 0]] - rest[skirt][edges[:, 1]], axis=1)
    out = {}
    for name, (J, W) in variants.items():
        tear = 0; worst = 0.; poke = 0
        for anim in glb.document["animations"]:
            clip = rig.animation(anim["name"]); count = max(2, math.ceil(clip["duration"] * 60))
            for f in range(count + 1):
                world = rig.globals(clip, clip["duration"] * f / count)
                sk = skin(rig.primitives[skirt], world, J, W)
                bd = skin(bp, world, bp["joint_index"], bp["weight"])
                pl = np.linalg.norm(sk[edges[:, 0]] - sk[edges[:, 1]], axis=1)
                tear += int((pl > rl * 7 + .03).sum())
                d, dist = clearance(sk, bd, tris[skirt])
                excess = d - np.maximum(rest_d, 0)
                mask = dist < .12
                worst = max(worst, float(excess[mask].max()) if mask.any() else 0)
                poke += int(((excess > .01) & mask).sum())
        out[name] = {"tearing": tear, "leg_out_max_m": round(worst, 4), "leg_out_samples_gt_1cm": poke}
    return out


if __name__ == "__main__":
    path = Path(sys.argv[1])
    glb = Glb(path); rig = Rig(glb)
    world0 = rig.globals({"tracks": {}}, 0.); rest = rig.skin(world0)
    by_node = {}
    for p, prim in enumerate(rig.primitives):
        by_node.setdefault(prim["node"], []).append(p)
    tris = {}
    for node, ps in by_node.items():
        for p, t in zip(ps, rf._triangles(glb, node)):
            tris[p] = t
    skirt = next(p for p, pr in enumerate(rig.primitives) if pr["name"] == "HeroTunicSkirt")
    prim = rig.primitives[skirt]
    variants = {"original": (prim["joint_index"], prim["weight"])}
    for thigh in (.45, .7, .9):
        rf.SKIRT_MAX_THIGH = thigh
        variants[f"hero_rule_thigh{thigh}"] = rf.skirt_weights(rig, prim, rest[skirt], world0)
    rf.SKIRT_MAX_THIGH = .45
    for passes in (2, 4, 8):
        rf.TUNIC_PASSES = passes
        variants[f"seam_smooth_{passes}"] = rf.tunic_weights(prim, rest[skirt], tris[skirt])
    rf.TUNIC_PASSES = 2
    print(path.name, json.dumps(run(path, variants), indent=1))
