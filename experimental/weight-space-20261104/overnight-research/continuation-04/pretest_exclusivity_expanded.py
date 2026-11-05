"""Research-only cross-study final-test reuse preflight; NOT a training permission.

This stricter research-only candidate checks a pinned inventory of local output
directories. It is not independent publication attestation, an atomic filesystem
lock, a reliable prevention of concurrent writes, or complete test authorization.
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


def _scan_final_artifacts(root: Path) -> None:
    """Conservative bounded walk; reject hidden, nested, and linked evidence.

    A filesystem is mutable between every operation; this is not an atomic
    reservation, nor a trustworthy proof that the test was not used elsewhere.
    """
    stack = [(root, 0)]
    checked = 0
    while stack:
        directory, depth = stack.pop()
        if depth > 12:
            raise ValueError("inventory traversal depth exceeded")
        for entry in directory.iterdir():
            checked += 1
            if checked > 50000:
                raise ValueError("inventory traversal budget exceeded")
            if entry.is_symlink():
                raise ValueError("symbolic link in output inventory")
            name = entry.name
            if (name.startswith("test-") or name.startswith(".test-") or
                    any(name == marker or name.startswith(marker + ".")
                        or name == "." + marker or name.startswith("." + marker + ".")
                        for marker in TEST_MARKERS)):
                raise ValueError("possible final-test artifact: " + name)
            if entry.is_dir():
                stack.append((entry, depth + 1))


def _scan_roots(roots: list[Path]) -> None:
    for root in roots:
        _require_plain_ancestors(root)
        _require_plain_ancestors(root / "logs")
        if not root.is_dir() or not (root / "logs").is_dir():
            raise ValueError("study output root/logs missing")
        _scan_final_artifacts(root)


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
        if len(set(roots)) != len(STUDIES):
            raise ValueError("aliased study output roots")
        if any(a in b.parents or b in a.parents for i, a in enumerate(roots)
               for b in roots[i + 1:]):
            raise ValueError("overlapping study output roots")
        _scan_roots(roots)
        if _sha(plan_path.read_bytes()) != pinned_plan_sha256:
            raise ValueError("plan changed during review")
        # A second sweep detects some interleavings that the first one misses.
        # This remains subject to TOCTOU; do not treat result as authorization.
        _scan_roots(roots)
        return {"status": "LOCALLY_UNCONSUMED_AT_INSPECTION", "selectedStudy": selected,
                "planSha256": pinned_plan_sha256, "checkedRoots": 3}
    except (OSError, ValueError, TypeError, KeyError) as exc:
        raise ReuseBlocked("CROSS_STUDY_FINAL_REUSE_BLOCKED: " + str(exc)) from exc
