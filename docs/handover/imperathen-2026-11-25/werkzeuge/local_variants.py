import sys, math, json, numpy as np
from scipy.spatial import cKDTree
sys.path.insert(0, "/home/user/imperathen/tools/characters")
sys.path.insert(0, "/tmp/claude-0/-home-user-device-ai/2f57f86d-fea5-54ec-a5ed-a114ab86238f/scratchpad/cloth")
import refit_frontier_cloth as rf
from cloth_variants import skin, normals
from ground_death_clip import Glb, Rig
from pathlib import Path

def local_smooth(prim, rest, tris, tearing_vertices, rings, passes, blend=.35):
    nj=len(prim["joints"]); n=len(rest)
    values=np.zeros((n,nj))
    for s in range(4): np.add.at(values,(np.arange(n),prim["joint_index"][:,s]),prim["weight"][:,s])
    _,welded=np.unique(np.round(rest/1e-5).astype(np.int64),axis=0,return_inverse=True); welded=welded.reshape(-1)
    m=welded.max()+1; shared=np.zeros((m,nj)); cnt=np.zeros(m)
    np.add.at(shared,welded,values); np.add.at(cnt,welded,1); shared/=cnt[:,None]
    pts=np.zeros((m,3)); np.add.at(pts,welded,rest); pts/=cnt[:,None]
    e=rf._edges(welded[tris]); a,b=e[:,0],e[:,1]
    mask=np.zeros(m,bool); mask[welded[list(tearing_vertices)]]=True
    for _ in range(rings):
        grow=mask.copy(); grow[a[mask[b]]]=True; grow[b[mask[a]]]=True; mask=grow
    inf=1./np.maximum(.003,np.linalg.norm(pts[a]-pts[b],axis=1))
    tot=np.zeros(m); np.add.at(tot,a,inf); np.add.at(tot,b,inf)
    for _ in range(passes):
        nb=np.zeros_like(shared); np.add.at(nb,a,shared[b]*inf[:,None]); np.add.at(nb,b,shared[a]*inf[:,None])
        act=mask&(tot>0)
        shared[act]=(1-blend)*shared[act]+blend*nb[act]/tot[act,None]
    vals=shared[welded]; ch=np.argsort(vals,axis=1)[:,-4:]; w=np.take_along_axis(vals,ch,axis=1); w/=w.sum(axis=1,keepdims=True)
    return ch.astype(np.int64), w, int(mask.sum()), m

def main(path, part):
    glb=Glb(path); rig=Rig(glb)
    w0=rig.globals({"tracks":{}},0.); rest=rig.skin(w0)
    by={}; [by.setdefault(pr["node"],[]).append(p) for p,pr in enumerate(rig.primitives)]
    tris={}
    for nn,ps in by.items():
        for p,t in zip(ps, rf._triangles(glb,nn)): tris[p]=t
    P={pr["name"]:p for p,pr in enumerate(rig.primitives)}
    cl=P[part]; body=P["HeroBody"]; cp=rig.primitives[cl]; bp=rig.primitives[body]
    edges=rf._edges(tris[cl]); rl=np.linalg.norm(rest[cl][edges[:,0]]-rest[cl][edges[:,1]],axis=1)
    frames=[]
    for a_ in glb.document["animations"]:
        clip=rig.animation(a_["name"]); cnt=max(2,math.ceil(clip["duration"]*60))
        for f in range(cnt+1): frames.append(rig.globals(clip,clip["duration"]*f/cnt))
    # tearing vertices of the original weights
    tv=set()
    for w in frames:
        t=skin(cp,w,cp["joint_index"],cp["weight"]); pl=np.linalg.norm(t[edges[:,0]]-t[edges[:,1]],axis=1)
        bad=edges[pl>rl*7+.03]; tv.update(bad.reshape(-1).tolist())
    tree=cKDTree(rest[cl]); d0,_=tree.query(rest[body]); bsel=np.flatnonzero(d0<.05)
    nrm=normals(rest[cl],tris[cl]); _,i0=tree.query(rest[body][bsel]); rb=((rest[body][bsel]-rest[cl][i0])*nrm[i0]).sum(axis=1)
    variants={"original":(cp["joint_index"],cp["weight"],0,0)}
    for rings in (1,2,3):
        for passes in (2,4):
            variants[f"local_r{rings}_p{passes}"]=local_smooth(cp,rest[cl],tris[cl],tv,rings,passes)
    res={"tearing_vertices":len(tv)}
    bodies=[skin(bp,w,bp["joint_index"],bp["weight"]) for w in frames]
    for name,(J,W,masked,total) in variants.items():
        tear=0; bw=0.; bc=0
        for w,b in zip(frames,bodies):
            t=skin(cp,w,J,W); pl=np.linalg.norm(t[edges[:,0]]-t[edges[:,1]],axis=1); tear+=int((pl>rl*7+.03).sum())
            tr=cKDTree(t); dist,idx=tr.query(b[bsel]); n=normals(t,tris[cl]); d=((b[bsel]-t[idx])*n[idx]).sum(axis=1)
            ex=d-np.maximum(rb,0); m=dist<.08
            if m.any(): bw=max(bw,float(ex[m].max())); bc+=int(((ex>.01)&m).sum())
        res[name]={"tearing":tear,"body_out_max_m":round(bw,4),"body_out_gt_1cm":bc,"smoothed_vertices":f"{masked}/{total}"}
    print(path.name, part, json.dumps(res))

main(Path(sys.argv[1]), sys.argv[2])
