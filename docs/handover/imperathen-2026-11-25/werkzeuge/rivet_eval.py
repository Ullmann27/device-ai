"""Find kit decoration islands bound to a different bone than the plate they sit on; measure lift-off."""
import sys, json, numpy as np, collections
from scipy.spatial import cKDTree
sys.path.insert(0, "/home/user/imperathen/tools/characters")
import smooth_frontier_skirt_seam as seam
from ground_death_clip import Glb, Rig
from pathlib import Path

SMALL = 40
DECORATION_M = .03
PLATE_M = .08
NEAR_M = .025
RATIO = 1.5


def islands(rest, tris):
    _, weld = np.unique(np.round(rest / 1e-5).astype(np.int64), axis=0, return_inverse=True)
    weld = weld.reshape(-1)
    parent = np.arange(weld.max() + 1)

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for t in weld[tris.reshape(-1, 3)]:
        for a, b in ((t[0], t[1]), (t[1], t[2])):
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[ra] = rb
    groups = collections.defaultdict(list)
    for v, w in enumerate(weld):
        groups[find(w)].append(v)
    return [np.array(v) for v in groups.values()]


def analyse(path):
    glb = Glb(path); rig = Rig(glb)
    k = next(p for p, pr in enumerate(rig.primitives) if pr["name"] == "FrontierRomanKit")
    prim = rig.primitives[k]
    sib = [p for p, pr in enumerate(rig.primitives) if pr["node"] == prim["node"]]
    tris = seam._triangles(glb, prim["node"])[sib.index(k)]
    rest = seam._pose(prim, rig.globals({"tracks": {}}, 0.), prim["joint_index"], prim["weight"])
    parts = islands(rest, tris)
    diag = lambda part: float(np.linalg.norm(rest[part].max(axis=0) - rest[part].min(axis=0)))
    bigs = [p for p in parts if diag(p) >= PLATE_M]
    if not bigs:
        return glb, rig, prim, k, sib, rest, np.array([], int), None, []
    big = np.concatenate(bigs)
    W = np.zeros((len(rest), len(prim["joints"])))
    for s in range(4):
        np.add.at(W, (np.arange(len(rest)), prim["joint_index"][:, s]), prim["weight"][:, s])
    dominant = W.argmax(axis=1)
    tree = cKDTree(rest[big])
    chosen = []
    for part in parts:
        if diag(part) > DECORATION_M:
            continue
        own = set(dominant[part].tolist())
        if len(own) != 1 or W[part].max(axis=1).min() < .999:
            continue
        own = own.pop()
        centre = rest[part].mean(axis=0)
        dist, idx = tree.query(centre)
        plate = big[idx]
        if dominant[plate] == own or dist > NEAR_M:
            continue
        same = big[dominant[big] == own]
        d_own = float(np.linalg.norm(rest[same] - centre, axis=1).min()) if len(same) else np.inf
        if d_own < RATIO * dist:
            continue
        chosen.append((part, plate, float(dist), d_own))
    return glb, rig, prim, k, sib, rest, big, W, chosen


def lift(rig, prim, rest, big, parts_weights, frames):
    """max over frames of (centre-to-nearest-plate-vertex distance - rest distance) per island."""
    result = []
    base_tree = cKDTree(rest[big])
    for part, joints, weights in parts_weights:
        d0, _ = base_tree.query(rest[part].mean(axis=0))
        worst = 0.
        for world in frames:
            plates = seam._pose(prim, world, prim["joint_index"], prim["weight"])[big]
            pts = seam._pose(prim, world, joints, weights)[part]
            d, _ = cKDTree(plates).query(pts.mean(axis=0))
            worst = max(worst, float(d - d0))
        result.append(worst)
    return result


if __name__ == "__main__":
    path = Path(sys.argv[1])
    glb, rig, prim, k, sib, rest, big, W, chosen = analyse(path)
    frames = seam._frames(rig)[::2]
    names = [rig.nodes[j]["name"] for j in prim["joints"]]
    before, after = [], []
    for part, plate, dist, d_own in chosen:
        before.append((part, prim["joint_index"], prim["weight"]))
        J = prim["joint_index"].copy(); Wt = prim["weight"].copy()
        J[part] = prim["joint_index"][plate]; Wt[part] = prim["weight"][plate]
        after.append((part, J, Wt))
    lb = lift(rig, prim, rest, big, before, frames); la = lift(rig, prim, rest, big, after, frames)
    for (part, plate, dist, d_own), b, a in zip(chosen, lb, la):
        own = names[int(W[part[0]].argmax())]; new = names[int(W[plate].argmax())]
        print(f"{path.name} island {len(part)}v at {np.round(rest[part].mean(0),3)} {own}->{new} plate {dist:.4f} own {d_own:.4f} lift {b:.4f}->{a:.4f}")
    print(path.name, "candidates", len(chosen))
