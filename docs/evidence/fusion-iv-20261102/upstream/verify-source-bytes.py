"""Recreate byte-verified audit snapshots from previously fetched text; stdlib only."""
from pathlib import Path
import hashlib
import json
import sys

base = Path(__file__).resolve().parent
manifest = json.loads((base / "source-provenance.json").read_text())
source_root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/workspace/upstream-audit")
checked = []

def git_blob(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()

for repo in manifest["repositories"]:
    for entry in repo["selectedSources"]:
        original = (source_root / repo["repository"] / entry["path"]).read_bytes()
        data = original
        normalization = "none"
        if git_blob(data) != entry["blob"] and original.endswith(b"\n"):
            # Acquisition added one LF to each text file. Accept removal only
            # when it restores the independently pinned Git blob exactly.
            data = original[:-1]
            normalization = "remove_one_acquisition_added_final_LF"
        if git_blob(data) != entry["blob"] or len(data) != entry["size"]:
            raise SystemExit("Pinned Git blob mismatch: " + repo["repository"] + "/" + entry["path"])
        dest = base / "sources" / repo["repository"] / entry["path"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        checked.append({
            "repository": repo["repository"],
            "commit": repo["commit"],
            "path": entry["path"],
            "gitBlobSha1": git_blob(data),
            "sha256": hashlib.sha256(data).hexdigest(),
            "bytes": len(data),
            "fetchedTextSha256": hashlib.sha256(original).hexdigest(),
            "normalization": normalization,
            "status": "PINNED_BLOB_MATCH"
        })

result = {
    "status": "PASS",
    "check": "Selected source snapshots match pinned GitHub recursive-tree Git blob SHA-1 and size",
    "filesChecked": len(checked),
    "upstreamRuntimeTests": "NOT_RUN",
    "androidBuildAndDevice": "NOT_RUN",
    "dependenciesInstalled": False,
    "sources": checked
}
(base / "source-byte-verification.json").write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps({"status": result["status"], "filesChecked": len(checked), "runtimeTests": "NOT_RUN", "android": "NOT_RUN"}))
