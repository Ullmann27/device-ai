"""Run a bounded QA command under both pre-existing C1 host locks."""
from __future__ import annotations

from contextlib import ExitStack
import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def main() -> int:
    if not sys.argv[1:]:
        raise SystemExit("Pass an explicit, already reviewed command.")
    locks = (
        Path("/workspace/scratch/b6ee64d418cf/toolchain/gradle-build.lock"),
        Path("/workspace/scratch/d663e8444310/qa/gradle.lock"),
    )
    output = Path(os.environ["CONTINUUM_R2_EVIDENCE_DIR"])
    output.mkdir(parents=True, exist_ok=True)
    receipt = {"lock_paths": [str(p) for p in locks], "wait_limit_seconds": 45,
               "command": sys.argv[1:], "pid": os.getpid()}
    start = time.monotonic()
    with ExitStack() as stack:
        for path in locks:
            if not path.is_file():
                receipt.update(status="BLOCKED", reason="EXPECTED_LOCK_FILE_MISSING",
                               missing_path=str(path))
                (output / "build-locks.json").write_text(json.dumps(receipt, indent=2) + "\n")
                print(json.dumps(receipt), flush=True)
                return 75
            handle = stack.enter_context(path.open("a"))
            while True:
                try:
                    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                    break
                except BlockingIOError:
                    if time.monotonic() - start >= 45:
                        receipt.update(status="BLOCKED", reason="SHARED_BUILD_LOCK_BUSY",
                                       busy_path=str(path), waited_seconds=time.monotonic() - start)
                        (output / "build-locks.json").write_text(json.dumps(receipt, indent=2) + "\n")
                        print(json.dumps(receipt), flush=True)
                        return 75
                    time.sleep(0.25)
        receipt.update(status="ACQUIRED", waited_seconds=time.monotonic() - start)
        (output / "build-locks.json").write_text(json.dumps(receipt, indent=2) + "\n")
        print(json.dumps(receipt), flush=True)
        result = subprocess.run(sys.argv[1:], check=False)
        receipt.update(status="RELEASED", command_exit_code=result.returncode,
                       total_seconds=time.monotonic() - start)
        (output / "build-locks.json").write_text(json.dumps(receipt, indent=2) + "\n")
        return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
