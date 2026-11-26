"""Accessor-level comparison: original vs grounded GLB (only death Pelvis translation may change)."""
import json, struct, sys, copy
from pathlib import Path
def load(p):
    d=Path(p).read_bytes(); n,_=struct.unpack_from('<II',d,12); j=json.loads(d[20:20+n]); b,_=struct.unpack_from('<II',d,20+n)
    return j, d[28+n:28+n+b]
out=[]; ok_all=True
for rel in sys.argv[3:]:
    a,abin=load(Path(sys.argv[1])/rel); b,bbin=load(Path(sys.argv[2])/rel)
    checks={}
    checks["binary_prefix_identical"]=bbin[:len(abin)]==abin
    checks["accessors_prefix_identical"]=b["accessors"][:len(a["accessors"])]==a["accessors"]
    checks["bufferviews_prefix_identical"]=b["bufferViews"][:len(a["bufferViews"])]==a["bufferViews"]
    for key in set(a)|set(b):
        if key in ("accessors","bufferViews","buffers","animations"): continue
        checks.setdefault("other_json_identical",True)
        checks["other_json_identical"] &= a.get(key)==b.get(key)
    changed=[]
    for ia,(x,y) in enumerate(zip(a["animations"],b["animations"])):
        if x["name"]!="death":
            if x!=y: changed.append(("non-death animation changed",x["name"]))
            continue
        yy=copy.deepcopy(y); xx=copy.deepcopy(x)
        extra=yy.pop("extras",{}); xx.pop("extras",None)
        added=yy["samplers"][len(xx["samplers"]):]; yy["samplers"]=yy["samplers"][:len(xx["samplers"])]
        if yy["samplers"]!=xx["samplers"]: changed.append(("death original samplers changed",))
        for cx,cy in zip(xx["channels"],yy["channels"]):
            if cx!=cy:
                node=b["nodes"][cy["target"]["node"]]["name"]; changed.append((node,cy["target"]["path"]))
        checks["death_new_samplers"]=len(added)
        checks["marker"]="imperathen_grounded_death" in extra
    checks["changed_channels"]=changed
    ok = all(v for k,v in checks.items() if k.endswith("identical")) and changed==[("Pelvis","translation")] and checks.get("marker")
    ok_all &= bool(ok)
    out.append({"glb":rel,"ok":bool(ok),**{k:(v if not isinstance(v,list) else [list(t) for t in v]) for k,v in checks.items()}})
json.dump({"all_ok":ok_all,"files":out},open(sys.argv[2]+"/../death-accessor-comparison.json" if False else "/dev/stdout","w"),indent=1)
