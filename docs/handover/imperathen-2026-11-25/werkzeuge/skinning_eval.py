"""Ad-hoc (Sitzung 25.11.2026, nicht reviewed). Aufruf:
  python3 -I skinning_eval.py <imperathen-root-mit-patch> <glb-dir> <lods z.B. 0,2> <clips z.B. march,idle> <out.json>
Evaluate produced youth GLBs: skinned sandal-slab tilt during ground contact,
floor penetration of all foot vertices, and ankle/foot edge stretch."""
import sys, os, json, math, numpy as np
from pathlib import Path
from scipy.spatial.transform import Rotation as R
code=Path(sys.argv[1]); glbdir=Path(sys.argv[2]); lods=[int(x) for x in sys.argv[3].split(",")]; clips=sys.argv[4].split(",")
sys.path.insert(0,str(code/'tools/characters'))
from retarget_accad_youth import decode, read_access, Target, UP
import retarget_accad_youth as rt
import youth_anatomy as ya
res={}
for lod in lods:
    d,b=decode(glbdir/f"hero_meshy_italia_{lod}.glb")
    # Build a Target-like FK object without calibration side effects.
    class T(Target):
        def __init__(s,d,b):
            s.d=d;s.b=b;s.nodes=d["nodes"];s.joints=d["skins"][0]["joints"];s.ids={s.nodes[i]["name"]:i for i in s.joints}
            s.parent={c:i for i,n in enumerate(s.nodes) for c in n.get("children",[])}
            s.t={i:np.array(n.get("translation",[0,0,0]),float) for i,n in enumerate(s.nodes)}
            s.r={i:R.from_quat(n.get("rotation",[0,0,0,1])).as_matrix() for i,n in enumerate(s.nodes)}
            s.order=[]
            def visit(i):
                if i in s.joints:s.order.append(i)
                for c in s.nodes[i].get("children",[]):visit(c)
            visit(s.ids["Pelvis"]); s.rest_r,s.rest_p=s.fk({}); s.hips=s.ids["Pelvis"]
            from retarget_accad_youth import frame
            s.pelvis_frame=frame(s.pos("Thigh_L")-s.pos("Thigh_R"),UP)
    t=T(d,b)
    slab={side:ya.sole_patch(t,read_access,side) for side in "LR"}
    node=next(n for n in t.nodes if n.get("name")=="MeshyYouthBody")
    pr=d["meshes"][node["mesh"]]["primitives"][0]; a=pr["attributes"]
    V=read_access(d,b,a["POSITION"]).astype(float); J=read_access(d,b,a["JOINTS_0"]).astype(int); W=read_access(d,b,a["WEIGHTS_0"]).astype(float)
    F=read_access(d,b,pr["indices"]).reshape(-1,3).astype(int)
    inv=read_access(d,b,d["skins"][0]["inverseBindMatrices"]).reshape(-1,4,4).transpose(0,2,1)
    footw={s:(W*np.isin(J,[t.joints.index(t.ids[n+"_"+s]) for n in ("Foot","Toe")])).sum(1) for s in "LR"}
    legw=(W*np.isin(J,[t.joints.index(t.ids[n+"_"+s]) for n in ("Foot","Toe","Shin") for s in "LR"])).sum(1)
    region=np.flatnonzero(legw>.3)
    E=np.unique(np.sort(np.concatenate([F[:,[0,1]],F[:,[1,2]],F[:,[2,0]]]),axis=1),axis=0)
    E=E[np.isin(E[:,0],region)&np.isin(E[:,1],region)]
    rest_len=np.linalg.norm(V[E[:,0]]-V[E[:,1]],axis=1); E=E[rest_len>1e-6]; rest_len=rest_len[rest_len>1e-6]
    sel={s:np.flatnonzero((footw[s]>.6)&(np.abs(V@slab[s]["normal"]+slab[s]["offset"])<=.004)) for s in "LR"}
    foot_all={s:np.flatnonzero(footw[s]>.6) for s in "LR"}
    idx=np.unique(np.concatenate([region]+list(foot_all.values())+list(sel.values())))
    pos_index={v:i for i,v in enumerate(idx)}
    homo=np.c_[V[idx],np.ones(len(idx))]
    local=np.einsum("nkij,nj->nki",inv[J[idx]],homo)
    res[lod]={}
    for clip in clips:
        an=next(x for x in d["animations"] if x["name"]==clip)
        rots={};trans=None
        for ch in an["channels"]:
            s=an["samplers"][ch["sampler"]]; out=read_access(d,b,s["output"])
            if ch["target"]["path"]=="rotation": rots[ch["target"]["node"]]=out
            elif ch["target"]["path"]=="translation" and ch["target"]["node"]==t.hips: trans=out
        n=len(trans); tilt=[];pen=[];stretch=[];over=[]
        for f in range(n):
            rot={k:R.from_quat(q[f]).as_matrix() for k,q in rots.items()}
            g,p=t.fk(rot,trans[f]-t.t[t.hips])
            M=np.stack([np.column_stack([g[i],p[i]]) for i in t.joints])
            P=(np.einsum("nkij,nkj->nki",M[J[idx]],local)*W[idx][...,None]).sum(1)
            for s in "LR":
                q=P[[pos_index[v] for v in sel[s]]]
                fp=P[[pos_index[v] for v in foot_all[s]]][:,1]; low=fp.min()
                if low<-.002 and os.environ.get("WHO"):
                    k=foot_all[s][int(fp.argmin())]; print("  PEN",clip,f,s,round(float(low),4),"bind_y",round(float(V[k,1]),4),"plane_dist",round(float(V[k]@slab[s]["normal"]+slab[s]["offset"]),4))
                if q[:,1].min()<0.012:  # this foot's slab is on/near the ground
                    c=q-q.mean(0); _,_,vh=np.linalg.svd(c,full_matrices=False); nn=vh[-1]
                    tilt.append(math.degrees(math.acos(min(1,abs(nn[1])))))
                pen.append(low)
            L=np.linalg.norm(P[[pos_index[v] for v in E[:,0]]]-P[[pos_index[v] for v in E[:,1]]],axis=1)/rest_len
            stretch.append(L.max()); over.append(int((L>1.5).sum()))
        res[lod][clip]={"frames":n,"contact_samples":len(tilt),"slab_tilt_deg_mean":round(float(np.mean(tilt)),1) if tilt else None,
            "slab_tilt_deg_p95":round(float(np.percentile(tilt,95)),1) if tilt else None,
            "min_foot_vertex_height_m":round(float(min(pen)),4),"max_leg_edge_stretch":round(float(max(stretch)),2),
            "max_edges_over_1_5x":int(max(over)),"leg_edges":int(len(E))}
        print("LOD",lod,clip,json.dumps(res[lod][clip]),flush=True)
json.dump(res,open(sys.argv[5],"w"),indent=1)
