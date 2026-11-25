"""Experiment: make UV islands near the hands follow either body or hand uniformly.
Input: retargeted youth GLB (with animations). Output: modified GLB + metrics."""
import sys, json, math, numpy as np
from pathlib import Path
from collections import Counter
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import connected_components
from scipy.spatial.transform import Rotation as R
code=Path(sys.argv[1]); src=Path(sys.argv[2]); out=Path(sys.argv[3]); mode=sys.argv[4] if len(sys.argv)>4 else "color"
sys.path.insert(0,str(code/'tools/characters'))
import retarget_accad_youth as rt
from youth_anatomy import texture_colors, skin_color
from youth_skin_polish import triangles_to_edges, ARM_PREFIXES, redistribute
d,b=rt.decode(src); nodes=d["nodes"]; joints=d["skins"][0]["joints"]; names=[nodes[i]["name"] for i in joints]
ids={nodes[i]["name"]:i for i in joints}; parent={c:i for i,n in enumerate(nodes) for c in n.get("children",[])}
T={i:np.array(n.get("translation",[0,0,0]),float) for i,n in enumerate(nodes)}; Rr={i:R.from_quat(n.get("rotation",[0,0,0,1])).as_matrix() for i,n in enumerate(nodes)}
order=[]
def visit(i):
    if i in joints: order.append(i)
    for c in nodes[i].get("children",[]): visit(c)
visit(ids["Pelvis"])
def fk(rot,shift):
    g={};p={}
    for i in order:
        par=parent.get(i);pr=g.get(par,np.eye(3));pp=p.get(par,np.zeros(3))
        g[i]=pr@rot.get(i,Rr[i]);p[i]=pp+pr@T[i]
        if i==order[0]: p[i]=p[i]+shift
    return g,p
inv=rt.read_access(d,b,d["skins"][0]["inverseBindMatrices"]).reshape(-1,4,4).transpose(0,2,1)
mats=[]
for an in d["animations"]:
    rots={};tr=None
    for ch in an["channels"]:
        s=an["samplers"][ch["sampler"]]; o=rt.read_access(d,b,s["output"])
        if ch["target"]["path"]=="rotation": rots[ch["target"]["node"]]=o
        elif ch["target"]["node"]==ids["Pelvis"] and ch["target"]["path"]=="translation": tr=o
    n=len(next(iter(rots.values())))
    for f in sorted(set(range(0,n,6))|{n-1}):
        rot={k:R.from_quat(q[min(f,len(q)-1)]).as_matrix() for k,q in rots.items()}
        g,p=fk(rot,(tr[min(f,len(tr)-1)]-T[ids["Pelvis"]]) if tr is not None else np.zeros(3))
        mats.append(np.stack([np.column_stack([g[i],p[i]]) for i in joints])@inv)
node=next(n for n in nodes if n.get("name")=="MeshyYouthBody"); pr=d["meshes"][node["mesh"]]["primitives"][0]; a=pr["attributes"]
v=rt.read_access(d,b,a["POSITION"]).astype(float); j=rt.read_access(d,b,a["JOINTS_0"]).astype(np.int64); w=rt.read_access(d,b,a["WEIGHTS_0"]).astype(float)
tri=rt.read_access(d,b,pr["indices"]).reshape(-1,3).astype(np.int64); edges=triangles_to_edges(tri.reshape(-1))
n=len(v); A=coo_matrix((np.ones(len(edges)),(edges[:,0],edges[:,1])),shape=(n,n)); k,lab=connected_components(A,directed=False)
colors=texture_colors(d,b,pr,rt.read_access); flesh=skin_color(colors); cloth=(colors.mean(1)>.31)&((colors.max(1)-colors.min(1))<.28)
arm=[i for i,x in enumerate(names) if x.startswith(ARM_PREFIXES)]
body=[names.index(x) for x in ("Pelvis","Thigh_L","Thigh_R")]
aw=(w*np.isin(j,arm)).sum(1); bw=(w*np.isin(j,body)).sum(1)
def skin(wj,ww):
    P=[]; pts=np.c_[v,np.ones(n)]
    for M in mats: P.append((np.einsum("vkij,vj->vki",M[wj],pts)*ww[...,None]).sum(1))
    return P
def metrics(P):
    rest=np.linalg.norm(v[edges[:,0]]-v[edges[:,1]],axis=1); lim=np.maximum(rest*4.0,rest+.045)
    worst=np.zeros(len(edges))
    for X in P: worst=np.maximum(worst,np.maximum(np.linalg.norm(X[edges[:,0]]-X[edges[:,1]],axis=1)-lim,0))
    # cracks: welded copies (same bind position) separating
    key=np.round(v,5); _,inv_w,cnt=np.unique(key,axis=0,return_inverse=True,return_counts=True); inv_w=inv_w.reshape(-1)
    multi=np.flatnonzero(cnt[inv_w]>1); grp={}
    for i in multi: grp.setdefault(inv_w[i],[]).append(i)
    pairs=np.array([(g[0],x) for g in grp.values() for x in g[1:]]) if grp else np.zeros((0,2),int)
    crack=np.zeros(len(pairs))
    for X in P: crack=np.maximum(crack,np.linalg.norm(X[pairs[:,0]]-X[pairs[:,1]],axis=1))
    return {"overstretched_edges":int((worst>.005).sum()),"max_excess_m":round(float(worst.max()),4),
            "sum_excess_m":round(float(worst.sum()),2),"crack_pairs_over_5mm":int((crack>.005).sum()),
            "crack_pairs_over_20mm":int((crack>.02).sum()),"max_crack_m":round(float(crack.max(initial=0)),4)}
base=metrics(skin(j,w)); print("BEFORE",json.dumps(base),flush=True)
# distance of every bind vertex to the hand/finger bone chain (bind pose)
GEO_R=float(sys.argv[5]) if len(sys.argv)>5 else .035
_, restp = fk({}, np.zeros(3))
def seg_dist(P,a,b):
    ab=b-a; t=np.clip(((P-a)@ab)/max(ab@ab,1e-12),0,1); return np.linalg.norm(P-(a+t[:,None]*ab),axis=1)
hand_dist=np.full(n,np.inf)
for side in "LR":
    chain=[nm+"_"+side for nm in ("Forearm","Hand","FingerCurl","Thumb","Palm") if nm+"_"+side in ids]
    pts={c:restp[ids[c]] for c in chain}
    segs=[]
    if "Forearm_"+side in pts and "Hand_"+side in pts: segs.append((pts["Forearm_"+side],pts["Hand_"+side]))
    for tip in ("FingerCurl_"+side,"Thumb_"+side,"Palm_"+side):
        if tip in pts and "Hand_"+side in pts:
            # extend each finger segment beyond its joint by its own length (fingertips)
            a0=pts["Hand_"+side]; b0=pts[tip]; segs.append((a0,b0+(b0-a0)))
    for a0,b0 in segs: hand_dist=np.minimum(hand_dist,seg_dist(v,a0,b0))
print("hand chain joints present:",[c for c in ids if c.split("_")[0] in ("Forearm","Hand","FingerCurl","Thumb","Palm")])
# candidate islands: contain substantial arm AND body influence
nj=j.copy(); nw=w.copy(); decisions=Counter()
hip=v[np.argmax(bw)] if False else None
hip=restp[ids["Pelvis"]]; knee_y=min(restp[ids["Shin_L"]][1],restp[ids["Shin_R"]][1]); half=abs(restp[ids["Thigh_L"]][0]-restp[ids["Thigh_R"]][0])*.5
armset=set(arm); pel,thl,thr=(names.index(x) for x in ("Pelvis","Thigh_L","Thigh_R"))
to_body=np.zeros(n,bool); to_hand_mask=np.zeros(n,bool)
for isl in range(k):
    m=np.flatnonzero(lab==isl)
    if mode=="geo2":
        if not (aw[m]>.05).any(): continue
        c=v[m].mean(0)
        if not (knee_y<c[1]<hip[1]+.12 and abs(c[0]-hip[0])<half+.15): continue
    else:
        if not ((aw[m]>.05).any() and (bw[m]>.05).any()): continue
        if v[m,1].mean()>1.0: continue
    if float(np.median(hand_dist[m]))<GEO_R:
        to_hand_mask[m]=True; decisions["hand"]+=1
    else:
        to_body[m]=True; decisions["body"]+=1
# Neighbour consistency: an island destined for the body whose welded seams
# connect mainly to hand-moving surface would float beside the hip; keep it
# with the hand instead (iterate until stable).
if mode=="geo2":
    keyw=np.round(v,5); _,wid=np.unique(keyw,axis=0,return_inverse=True); wid=wid.reshape(-1)
    from collections import defaultdict
    by_w=defaultdict(set)
    for i in range(n): by_w[wid[i]].add(lab[i])
    seam=defaultdict(Counter)
    for isls in by_w.values():
        if len(isls)>1:
            for x in isls:
                for y in isls:
                    if x!=y: seam[x][y]+=1
    def hand_moving(isl):
        m=lab==isl
        if to_hand_mask[m].any(): return True
        if to_body[m].any(): return False
        return aw[m].mean()>.5
    for it in range(5):
        changed=0
        for isl in set(lab[to_body]):
            nb=seam.get(isl,{})
            tot=sum(nb.values())
            if tot==0: continue
            handy=sum(c for y,c in nb.items() if hand_moving(y))
            if handy/tot>.5:
                m=lab==isl; to_body[m]=False; to_hand_mask[m]=True; decisions["body"]-=1; decisions["hand"]+=1; decisions["flipped"]+=1; changed+=1
        if not changed: break
# body islands: move every arm influence to pelvis/thighs exactly like the project's garment repair
nj,nw=redistribute(j,w,v,to_body,armset,pel,left:=thl,right:=thr,hip) if False else redistribute(j,w,v,to_body,armset,pel,thl,thr,hip)
# hand islands: drop non-arm influences, renormalise
for i in np.flatnonzero(to_hand_mask):
    keep=[(int(nj[i,q]),nw[i,q]) for q in range(4) if nw[i,q]>0 and int(nj[i,q]) in armset]
    if not keep: continue
    tot=sum(x for _,x in keep); nj[i]=0; nw[i]=0
    for q,(jj,x) in enumerate(keep): nj[i,q]=jj; nw[i,q]=x/tot
after=metrics(skin(nj,nw)); print("AFTER",mode,dict(decisions),json.dumps(after),flush=True)
# write GLB with appended weight accessors
class Tg: pass
t=Tg(); t.d=d; t.b=b
def append(array,kind,component_type=5126):
    arr=np.asarray(array,dtype=np.dtype(rt.DTYPE[component_type]).newbyteorder("<")).reshape(-1,rt.TYPE_N[kind]); raw=arr.tobytes()
    t.b+=b"\0"*(-len(t.b)%4); start=len(t.b); t.b+=raw
    d["bufferViews"].append({"buffer":0,"byteOffset":start,"byteLength":len(raw)})
    d["accessors"].append({"bufferView":len(d["bufferViews"])-1,"componentType":component_type,"count":len(arr),"type":kind}); return len(d["accessors"])-1
ja=append(nj,"VEC4",5123); wa=append(nw,"VEC4")
for p in d["meshes"][node["mesh"]]["primitives"]:
    if p["attributes"].get("POSITION")==a["POSITION"]: p["attributes"]["JOINTS_0"]=ja; p["attributes"]["WEIGHTS_0"]=wa
out.write_bytes(rt.encode(d,t.b)); print("WROTE",out)
