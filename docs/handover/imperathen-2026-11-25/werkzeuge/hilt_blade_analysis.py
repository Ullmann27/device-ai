import sys, numpy as np
from pathlib import Path
from scipy.spatial import cKDTree
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import connected_components
code=Path(sys.argv[1]); orig=Path(sys.argv[2]); out=Path(sys.argv[3]); sys.path.insert(0,str(code/'tools/characters'))
import retarget_accad_youth as rt
from youth_anatomy import texture_colors, skin_color
from youth_skin_polish import triangles_to_edges
do,bo=rt.decode(orig); blade=next(n for n in do["nodes"] if n.get("name")=="PracticeBlade")
pts=np.unique(np.concatenate([rt.read_access(do,bo,p["attributes"]["POSITION"]) for p in do["meshes"][blade["mesh"]]["primitives"]]),axis=0)
btri=np.concatenate([rt.read_access(do,bo,p["indices"]).reshape(-1,3) for p in do["meshes"][blade["mesh"]]["primitives"]])
# dense surface samples of the blade (vertices + face centres + edge midpoints)
bv=np.concatenate([rt.read_access(do,bo,p["attributes"]["POSITION"]) for p in do["meshes"][blade["mesh"]]["primitives"]])
samples=np.concatenate([bv, bv[btri].mean(1), (bv[btri[:,0]]+bv[btri[:,1]])/2,(bv[btri[:,1]]+bv[btri[:,2]])/2,(bv[btri[:,2]]+bv[btri[:,0]])/2])
tree=cKDTree(samples)
c=pts.mean(0); _,_,vh=np.linalg.svd(pts-c,full_matrices=False); axis=vh[0]
proj=(pts-c)@axis; print("blade length %.3f m, extent along axis %.3f..%.3f"%(np.ptp(proj),proj.min(),proj.max()))
d,b=rt.decode(out); nodes=d["nodes"]; joints=d["skins"][0]["joints"]; names=[nodes[i]["name"] for i in joints]
node=next(n for n in nodes if n.get("name")=="MeshyYouthBody"); pr=d["meshes"][node["mesh"]]["primitives"][0]; a=pr["attributes"]
v=rt.read_access(d,b,a["POSITION"]).astype(float); tri=rt.read_access(d,b,pr["indices"]).reshape(-1,3).astype(int)
used=np.unique(tri); e=triangles_to_edges(tri.reshape(-1)); n=len(v)
lab=connected_components(coo_matrix((np.ones(len(e)),(e[:,0],e[:,1])),shape=(n,n)),directed=False)[1]
rgb=texture_colors(d,b,pr,rt.read_access); flesh=skin_color(rgb)
prox=tree.query(v)[0]; q=v-c; rad=np.linalg.norm(q-(q@axis)[:,None]*axis,axis=1)
p0,p1,p2=v[tri[:,0]],v[tri[:,1]],v[tri[:,2]]; area=np.linalg.norm(np.cross(p1-p0,p2-p0),axis=1)/2
rows=[]
for isl in np.unique(lab[used[prox[used]<.02]]):
    m=np.flatnonzero(lab==isl); m=m[np.isin(m,used)]
    fi=np.isin(tri,m).all(1)
    rows.append((float(np.median(prox[m])),int(isl),len(m),int(fi.sum()),float(area[fi].sum()*1e4),float(flesh[m].mean()),[round(float(x),2) for x in rgb[m].mean(0)],float(np.median(rad[m])),float(np.median((v[m]-c)@axis))))
rows.sort()
print("islands with any vertex within 2cm of blade surface:",len(rows))
for r in rows[:40]: print("medprox=%.4f isl=%d verts=%d faces=%d area=%.2fcm2 flesh=%.2f rgb=%s rad=%.3f along=%.3f"%r)
vt=cKDTree(np.unique(bv,axis=0)); exact=vt.query(v)[0]<5e-4
stats=[]
for isl in np.unique(lab[used]):
    m=np.flatnonzero(lab==isl); m=m[np.isin(m,used)]
    frac=exact[m].mean()
    if frac>0: stats.append((frac,int(isl),len(m)))
stats=np.array(stats)
for th in (1.0,.9,.75,.5):
    sel=stats[stats[:,0]>=th]; isl=sel[:,1].astype(int)
    fi=np.isin(lab[tri],isl).all(1)
    print(f"islands with >= {th:.0%} verts exactly on blade: {len(sel)}, faces {int(fi.sum())}, area {area[fi].sum()*1e4:.1f} cm2, flesh-vertex share {flesh[np.isin(lab,isl)&np.isin(np.arange(n),used)].mean():.2f}")
np.save(sys.argv[4] if len(sys.argv)>4 else "/dev/null", stats)
