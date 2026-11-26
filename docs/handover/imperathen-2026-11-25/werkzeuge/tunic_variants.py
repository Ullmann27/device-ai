import sys, math, json, numpy as np
from scipy.spatial import cKDTree
sys.path.insert(0, "/home/user/imperathen/tools/characters")
sys.path.insert(0, "/tmp/claude-0/-home-user-device-ai/2f57f86d-fea5-54ec-a5ed-a114ab86238f/scratchpad/cloth")
import refit_frontier_cloth as rf
from cloth_variants import skin, normals
from ground_death_clip import Glb, Rig
from pathlib import Path
path=Path(sys.argv[1]); glb=Glb(path); rig=Rig(glb)
w0=rig.globals({"tracks":{}},0.); rest=rig.skin(w0)
by={}; [by.setdefault(pr["node"],[]).append(p) for p,pr in enumerate(rig.primitives)]
tris={}
for n,ps in by.items():
    for p,t in zip(ps, rf._triangles(glb,n)): tris[p]=t
P={pr["name"]:p for p,pr in enumerate(rig.primitives)}
tun=P["HeroTunic"]; body=P["HeroBody"]; kit=P.get("FrontierRomanKit")
tp=rig.primitives[tun]
variants={"original":(tp["joint_index"],tp["weight"])}
for k in (2,4):
    rf.TUNIC_PASSES=k; variants[f"smooth{k}"]=rf.tunic_weights(tp,rest[tun],tris[tun])
rf.TUNIC_PASSES=2
edges=rf._edges(tris[tun]); rl=np.linalg.norm(rest[tun][edges[:,0]]-rest[tun][edges[:,1]],axis=1)
bp=rig.primitives[body]
def through(outer_pts, outer_tri, inner_pts, sel, rest_d=None):
    tree=cKDTree(outer_pts); dist,idx=tree.query(inner_pts[sel]); n=normals(outer_pts,outer_tri)
    return ((inner_pts[sel]-outer_pts[idx])*n[idx]).sum(axis=1), dist
# body vertices near tunic at rest
tree=cKDTree(rest[tun]); d0,_=tree.query(rest[body]); bsel=np.flatnonzero(d0<.05)
rb,_=through(rest[tun],tris[tun],rest[body],bsel)
if kit is not None:
    tk=cKDTree(rest[kit]); dk,_=tk.query(rest[tun]); tsel=np.flatnonzero(dk<.05)
    rk,_=through(rest[kit],tris[kit],rest[tun],tsel)
res={}
for name,(J,W) in variants.items():
    tear=0; bw=0.; bc=0; kw=0.; kc=0
    for a in glb.document["animations"]:
        clip=rig.animation(a["name"]); cnt=max(2,math.ceil(clip["duration"]*60))
        for f in range(cnt+1):
            w=rig.globals(clip,clip["duration"]*f/cnt)
            t=skin(tp,w,J,W); b=skin(bp,w,bp["joint_index"],bp["weight"])
            pl=np.linalg.norm(t[edges[:,0]]-t[edges[:,1]],axis=1); tear+=int((pl>rl*7+.03).sum())
            d,dist=through(t,tris[tun],b,bsel); ex=d-np.maximum(rb,0); m=dist<.08
            if m.any(): bw=max(bw,float(ex[m].max())); bc+=int(((ex>.01)&m).sum())
            if kit is not None:
                kp=skin(rig.primitives[kit],w,rig.primitives[kit]["joint_index"],rig.primitives[kit]["weight"])
                d,dist=through(kp,tris[kit],t,tsel); ex=d-np.maximum(rk,0); m=dist<.08
                if m.any(): kw=max(kw,float(ex[m].max())); kc+=int(((ex>.01)&m).sum())
    res[name]={"tearing":tear,"body_out_max_m":round(bw,4),"body_out_gt_1cm":bc,"tunic_out_of_kit_max_m":round(kw,4),"tunic_out_of_kit_gt_1cm":kc}
print(path.name, json.dumps(res))
