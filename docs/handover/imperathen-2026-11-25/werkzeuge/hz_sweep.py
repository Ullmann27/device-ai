import sys, json, collections
sys.path.insert(0, "/home/user/work/imp-clean/tools/characters")
import ground_death_clip as g
from pathlib import Path
orig=Path(sys.argv[1]); files=sorted(str(p.relative_to(orig)) for p in orig.rglob("*.glb"))
for hz in (60,120,180,240):
    g.KEY_HZ=hz; worst=collections.defaultdict(float); 
    for rel in files:
        r=g.ground(orig/rel, False)
        fam="frontier" if "frontier_" in rel else "meshy" if "meshy/" in rel else "soldiers" if "soldiers/" in rel else "village"
        worst[fam]=max(worst[fam], r["after"]["max_sole_slip_m"]); worst["pen"]=max(worst["pen"], r["after"]["max_sole_penetration_m"], r["after"]["max_hull_penetration_m"])
    print(hz, {k:round(v,4) for k,v in worst.items()}, flush=True)
