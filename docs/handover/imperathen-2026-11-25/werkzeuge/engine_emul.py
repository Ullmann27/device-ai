"""Emulate Godot 4 glTF import baking (animation/fps) and measure the death clip like the gate."""
import sys, math, numpy as np
sys.path.insert(0, "/home/user/work/imp-clean/tools/characters")
import ground_death_clip as g
from pathlib import Path

def bake_times(end, fps=30):
    times=[]; t=0.0; inc=1.0/fps; last=False
    while True:
        times.append(t)
        if last: break
        t+=inc
        if t>=end: last=True; t=end
    return np.array(times)

def baked_clip(rig, clip, fps=30):
    out={"tracks":{}, "duration":clip["duration"], "animation":clip["animation"]}
    T=bake_times(clip["duration"], fps)
    for key,track in clip["tracks"].items():
        vals=np.array([rig.sample(track,t,key[1]) for t in T])
        out["tracks"][key]=(T, vals, "LINEAR" if track[2]!="STEP" else "LINEAR")
    return out

for rel in sys.argv[2:]:
    rig=g.Rig(g.Glb(Path(sys.argv[1])/rel)); clip=rig.animation("death")
    direct=g.measure(rig, clip); baked=g.measure(rig, baked_clip(rig, clip))
    print(rel, "direct pen %.4f slip %.4f | baked30 pen %.4f slip %.4f" % (direct["max_sole_penetration_m"], direct["max_sole_slip_m"], baked["max_sole_penetration_m"], baked["max_sole_slip_m"]))
