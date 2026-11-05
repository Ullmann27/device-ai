"""Research-only cross-study final-test reuse preflight; NOT a training permission.

This guard checks a pinned inventory of local output directories. It is not an
independent publication attestation, a filesystem lock, or a complete test gate.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re

STUDIES = frozenset({"v1", "v2", "v3"})
TEST_MARKERS = ("intent-summary.json", "final-test-started.json")


class ReuseBlocked(RuntimeError):
    pass


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _unique_object(pairs):
    result = {}
    for k, v in pairs:
        if k in result:
            raise ValueError("duplicate manifest field")
        result[k] = v
    return result


def _require_plain_ancestors(path: Path) -> None:
    """Reject a link in any named path component; not an atomic openat check."""
    if not path.is_absolute() or ".." in path.parts:
        raise ValueError("noncanonical or relative path")
    for component in (path, *path.parents):
        if component.is_symlink():
            raise ValueError("symbolic link in path: " + str(component))


def _has_local_test_artifact(logdir: Path) -> bool:
    """Fail closed on partial/renamed/hidden test-* outputs, not only two suffixes."""
    return any(entry.name.startswith("test-") or entry.name.startswith(".test-")
               for entry in logdir.iterdir())


def _has_local_test_marker(root: Path) -> bool:
    """A partially written result or start marker is also evidence of consumption."""
    return any(entry.name == marker or entry.name.startswith(marker + ".")
               for entry in root.iterdir() for marker in TEST_MARKERS)


def verify_unused(plan_path: Path, pinned_plan_sha256: str, selected: str) -> dict:
    """Fail closed on incomplete inventory or any recorded final-test artifact."""
    try:
        if not re.fullmatch(r"[0-9a-f]{64}", pinned_plan_sha256):
            raise ValueError("expected plan SHA-256 required")
        _require_plain_ancestors(plan_path)
        plan_raw = plan_path.read_bytes()
        if _sha(plan_raw) != pinned_plan_sha256:
            raise ValueError("plan bytes differ from pin")
        plan = json.loads(plan_raw, object_pairs_hook=_unique_object,
                          parse_constant=lambda v: (_ for _ in ()).throw(ValueError(v)))
        if type(plan) is not dict or set(plan) != {"version", "selectedStudy", "studyOutputs"}:
            raise ValueError("incomplete or unexpected plan")
        if type(plan["version"]) is not int or plan["version"] != 1 or selected not in STUDIES:
            raise ValueError("invalid version or selected study")
        if plan["selectedStudy"] != selected:
            raise ValueError("selected study mismatch")
        paths = plan["studyOutputs"]
        if type(paths) is not dict or set(paths) != STUDIES or not all(type(v) is str for v in paths.values()):
            raise ValueError("all three output roots are mandatory")
        roots = []
        for study in sorted(STUDIES):
            root = Path(paths[study]); logdir = root / "logs"
            _require_plain_ancestors(root)
            _require_plain_ancestors(logdir)
            if not root.is_dir() or not logdir.is_dir():
                raise ValueError("missing, relative, or symbolic-link output: " + study)
            roots.append(root.resolve(strict=True))
            if _has_local_test_artifact(logdir):
                raise ValueError("final test already started in " + study)
            if _has_local_test_marker(root):
                raise ValueError("final result/consumption marker present in " + study)
        if len(set(roots)) != len(STUDIES):
            raise ValueError("aliased study output roots")
        if _sha(plan_path.read_bytes()) != pinned_plan_sha256:
            raise ValueError("plan changed during review")
        return {"status": "LOCALLY_UNCONSUMED_AT_INSPECTION", "selectedStudy": selected,
                "planSha256": pinned_plan_sha256, "checkedRoots": 3}
    except (OSError, ValueError, TypeError, KeyError) as exc:
        raise ReuseBlocked("CROSS_STUDY_FINAL_REUSE_BLOCKED: " + str(exc)) from exc
