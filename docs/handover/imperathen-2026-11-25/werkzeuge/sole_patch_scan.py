import sys, math, json, numpy as np
from pathlib import Path
from collections import defaultdict, deque
root=Path(sys.argv[1]); lods=[int(x) for x in sys.argv[2].split(",")]; ANG=float(sys.argv[3]) if len(sys.argv)>3 else 12
sys.path.insert(0,str(root/'tools/characters'))
from retarget_accad_youth import decode, read_access, Target, UP
import retarget_accad_youth as rt
out={}
for lod in lods:
    d,b=decode(root/f"prototype/assets/characters/hero_meshy_italia_{lod}.glb")
    keep=rt.calibrate_soles; rt.calibrate_soles=lambda t,ra:None; t=Target(d,b); rt.calibrate_soles=keep
    node=next(n for n in t.nodes if n.get("name")=="MeshyYouthBody")
    for pi,pr in enumerate(d["meshes"][node["mesh"]]["primitives"]):
        a=pr["attributes"]
        if not {"POSITION","JOINTS_0","WEIGHTS_0"}.issubset(a) or "indices" not in pr:continue
        v=read_access(d,b,a["POSITION"]).astype(float); j=read_access(d,b,a["JOINTS_0"]).astype(int); w=read_access(d,b,a["WEIGHTS_0"])
        f=read_access(d,b,pr["indices"]).reshape(-1,3).astype(int)
        for side in "LR":
            ids=[t.joints.index(t.ids[n+"_"+side]) for n in ("Foot","Toe")]
            fw=(w*np.isin(j,ids)).sum(1)
            # faces of the foot (mean weight) below the ankle joint height
            ff=np.flatnonzero((fw[f].mean(1)>.5)&(v[f][:,:,1].mean(1)<.14))
            if not len(ff):continue
            F=f[ff]; p0,p1,p2=v[F[:,0]],v[F[:,1]],v[F[:,2]]; cr=np.cross(p1-p0,p2-p0); ar=np.linalg.norm(cr,axis=1)/2; nn=cr/np.maximum(2*ar[:,None],1e-12)
            # adjacency via shared edges (use rounded positions to weld seams)
            key=np.round(v,5); vid={}; wid=np.array([vid.setdefault(tuple(k),len(vid)) for k in key])
            edge=defaultdict(list)
            for k,(x,y,z) in enumerate(wid[F]):
                for e in ((x,y),(y,z),(z,x)): edge[tuple(sorted(e))].append(k)
            nb=defaultdict(set)
            for ks in edge.values():
                for p in ks:
                    for q in ks:
                        if p!=q: nb[p].add(q)
            seen=np.zeros(len(F),bool); patches=[]
            cosang=math.cos(math.radians(ANG))
            for s in np.argsort(-ar):
                if seen[s]:continue
                comp=[s];seen[s]=True;dq=deque([s]);ref=nn[s]
                while dq:
                    c=dq.popleft()
                    for q in nb[c]:
                        if not seen[q] and nn[q]@ref>cosang: seen[q]=True;comp.append(q);dq.append(q)
                A=ar[comp].sum(); m=(nn[comp]*ar[comp,None]).sum(0); m/=np.linalg.norm(m)
                patches.append((A,m,len(comp),comp))
            patches.sort(key=lambda x:-x[0])
            print(f"LOD{lod} prim{pi} {side}: foot faces={len(F)} total area={ar.sum()*1e4:.1f}cm2")
            for A,m,nc,comp in patches[:4]:
                c=((p0+p1+p2)/3)[comp].mean(0)
                print(f"   patch area={A*1e4:5.1f}cm2 tris={nc:3d} outward n={np.round(m,3)} centroid={np.round(c,3)}")
            out[f"{lod}_{side}"]={"faces":[int(ff[i]) for i in patches[0][3]],"normal":patches[0][1].tolist(),"prim":pi}
json.dump(out,open(sys.argv[4] if len(sys.argv)>4 else "/dev/null","w"))
