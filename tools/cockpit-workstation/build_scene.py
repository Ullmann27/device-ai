"""Build the current Blender assets."""
import argparse
import fcntl
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--blender", default="blender")
    parser.add_argument("--lock", type=Path,
        default=Path(os.environ.get("CONTINUUM_HEAVY_JOB_LOCK", str(ROOT.parent/"agent-gradle.lock"))))
    args = parser.parse_args()
    args.lock.parent.mkdir(parents=True, exist_ok=True)
    with args.lock.open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        subprocess.run([args.blender, "-b", "-t", "2", "--python",
            str(ROOT/"tools/cockpit-workstation/build_unified_assets.py")], check=True, cwd=ROOT)
    subprocess.run([sys.executable, str(ROOT/"tools/cockpit-workstation/verify_unified_assets.py"),
        "--write-manifest"], check=True, cwd=ROOT)


if __name__ == "__main__":
    main()
