"""Independently exercise the real QA entry point with dummy signing material.

No network, build, publisher or signer is executed. A local curl canary stops any
case that unexpectedly reaches setup. Each case uses its own temporary Git repo.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile


SIGNING_KEYS = (
    "CONTINUUM_LOCAL_SIGNING_FILE",
    "CONTINUUM_LOCAL_SIGNING_PASSWORD",
    "CONTINUUM_LOCAL_SIGNING_P12_BASE64",
    "CONTINUUM_MENTOR_SIGNING_FILE",
    "CONTINUUM_MENTOR_SIGNING_PASSWORD",
    "CONTINUUM_MENTOR_SIGNING_P12_BASE64",
)
SCRIPT = Path("scripts/render-continuum-owner-continuous-qa.sh")
BRANCH = "integration/continuum-all-systems-apk-qa-20261102"


def run_case(source: bytes, mode: str, key: str) -> dict:
    with tempfile.TemporaryDirectory(prefix="continuum-signing-independent-") as tmp:
        root = Path(tmp)
        repo, bin_dir = root / "repo", root / "bin"
        (repo / SCRIPT.parent).mkdir(parents=True)
        bin_dir.mkdir()
        (repo / SCRIPT).write_bytes(source)
        canary = root / "network-attempted"
        curl = bin_dir / "curl"
        curl.write_text('#!/bin/sh\n: > "$CONTINUUM_SIGNING_PROBE_CANARY"\nexit 97\n')
        curl.chmod(0o700)
        # Only explicit non-secret fixture variables are inherited by the script.
        env = {
            "PATH": str(bin_dir) + os.pathsep + os.defpath,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_GLOBAL": "/dev/null",
            "CONTINUUM_OWNER_RELEASE_QA": mode,
            "CONTINUUM_RELEASE_VERSION_CODE": "42",
            "RENDER_SERVICE_NAME": (
                "continuum-owner-v23-release-preflight" if mode == "1"
                else "continuum-unified-research-apk-20261102"
            ),
            "CONTINUUM_RELEASE_HOST": "continuum-owner-v23-release-preflight.onrender.com",
            "CONTINUUM_SIGNING_PROBE_CANARY": str(canary),
            key: "DUMMY-NOT-A-KEY",
        }

        def git(*args: str) -> str:
            command = ["git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false",
                       "-c", "user.name=Independent QA probe", "-c", "user.email=probe@example.invalid", *args]
            return subprocess.run(command, cwd=repo, env=env, check=True, capture_output=True,
                                  text=True, timeout=10).stdout.strip()

        git("init", "-q", "-b", BRANCH)
        git("add", str(SCRIPT))
        git("commit", "-qm", "Independent temporary QA fixture")
        env["CONTINUUM_RELEASE_APPROVED_SHA"] = git("rev-parse", "HEAD")
        result = subprocess.run(["bash", str(SCRIPT)], cwd=repo, env=env, capture_output=True,
                                text=True, timeout=10)
        row = {
            "mode": mode,
            "variable": key,
            "exitCode": result.returncode,
            "signingRefused": "Signing material forbidden in QA" in result.stderr,
            "downloadAttempted": canary.exists(),
            "workspaceCreated": (repo / ".continuum-render-build").exists(),
            "logsCreated": (repo / "continuum-all-systems-logs").exists(),
            "dummyValuePrinted": "DUMMY-NOT-A-KEY" in result.stdout + result.stderr,
        }
        row["pass"] = (
            row["exitCode"] == 69 and row["signingRefused"]
            and not any(row[x] for x in ("downloadAttempted", "workspaceCreated", "logsCreated", "dummyValuePrinted"))
        )
        return row


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_repo", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = (args.source_repo / SCRIPT).read_bytes()
    cases = [run_case(source, mode, key) for mode in ("0", "1") for key in SIGNING_KEYS]
    report = {
        "schemaVersion": 1,
        "sourceSha256": hashlib.sha256(source).hexdigest(),
        "caseCount": len(cases),
        "pass": sum(row["pass"] for row in cases),
        "fail": sum(not row["pass"] for row in cases),
        "networkBuildSigningExecuted": False,
        "cases": cases,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: report[key] for key in ("sourceSha256", "caseCount", "pass", "fail")}))
    return 1 if report["fail"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
