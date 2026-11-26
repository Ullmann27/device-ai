import sys, numpy as np, collections
sys.path.insert(0,"/home/user/imperathen/tools/characters")
import rebind_frontier_kit_rivets as rk
from smooth_frontier_skirt_seam import _pose, _triangles
from ground_death_clip import Glb, Rig
from pathlib import Path

def point_tri(p, a, b, c):
    # Ericson closest point on triangle, vectorised over triangles for one point
    ab=b-a; ac=c-a; ap=p-a
    d1=(ab*ap).sum(1); d2=(ac*ap).sum(1)
    bp=p-b; d3=(ab*bp).sum(1); d4=(ac*bp).sum(1)
    cp=p-c; d5=(ab*cp).sum(1); d6=(ac*cp).sum(1)
    vc=d1*d4-d3*d2; vb=d5*d2-d1*d6; va=d3*d6-d5*d4
    denom=va+vb+vc; denom=np.where(np.abs(denom)<1e-18,1e-18,denom)
    v=vb/denom; w=vc/denom
    q=a+ab*v[:,None]+ac*w[:,None]
    # region handling (approximate: clamp barycentrics)
    u=1-v-w
    bad=(u<0)|(v<0)|(w<0)
    if bad.any():
        # fall back to closest of edges via sampling
        for i in np.flatnonzero(bad):
            best=None
            for x,y in ((a[i],b[i]),(b[i],c[i]),(c[i],a[i])):
                e=y-x; t=np.clip(np.dot(p-x,e)/max(np.dot(e,e),1e-18),0,1); r=x+t*e
                dd=np.linalg.norm(p-r)
                if best is None or dd<best[0]: best=(dd,r)
            q[i]=best[1]
    return np.linalg.norm(q-p,axis=1)

path=Path(sys.argv[1]); glb=Glb(path); rig=Rig(glb)
k=next(p for p,pr in enumerate(rig.primitives) if pr["name"]=="FrontierRomanKit"); prim=rig.primitives[k]
sib=[p for p,pr in enumerate(rig.primitives) if pr["node"]==prim["node"]]
tris=_triangles(glb,prim["node"])[sib.index(k)].reshape(-1,3)
rest=_pose(prim,rig.globals({"tracks":{}},0.),prim["joint_index"],prim["weight"])
parts=rk._islands(rest,tris)
diag=[float(np.linalg.norm(rest[p].max(0)-rest[p].min(0))) for p in parts]
plates=[p for p,d in zip(parts,diag) if d>=rk.PLATE_M]
plate_set=np.zeros(len(rest),bool); plate_set[np.concatenate(plates)]=True
ptris=tris[plate_set[tris].all(1)]
A,B,C=rest[ptris[:,0]],rest[ptris[:,1]],rest[ptris[:,2]]; cen=(A+B+C)/3
W=np.zeros((len(rest),len(prim["joints"])))
for s in range(4): np.add.at(W,(np.arange(len(rest)),prim["joint_index"][:,s]),prim["weight"][:,s])
names=[rig.nodes[j]["name"] for j in prim["joints"]]
rows=[]
for p,d in zip(parts,diag):
    if d>rk.DECORATION_M: continue
    c=rest[p].mean(0); near=np.flatnonzero(np.linalg.norm(cen-c,axis=1)<.15)
    if not len(near): rows.append((None,len(p),np.round(c,3),names[int(W[p[0]].argmax())])); continue
    gap=min(float(point_tri(rest[v],A[near],B[near],C[near]).min()) for v in p)
    rows.append((round(gap,4),len(p),np.round(c,3),names[int(W[p[0]].argmax())]))
rows.sort(key=lambda r:-(r[0] or 9))
print(path.name,"decorations",len(rows)); [print(r) for r in rows[:30]]
