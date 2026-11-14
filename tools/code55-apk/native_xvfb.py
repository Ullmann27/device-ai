#!/usr/bin/env python3
"""Bound the owned display server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import select
import signal
import time

ARGUMENTS = ["-displayfd", "3", "-screen", "0", "1080x2400x24", "-nolisten", "tcp"]
START_SECONDS = 5
STOP_SECONDS = 2


def identity(pid, parent, *, allow_empty_command=False):
    if pid <= 1 or pid == os.getpid():
        raise ValueError("Invalid display PID")
    root = Path(f"/proc/{pid}")
    stat = (root / "stat").read_text().rsplit(") ", 1)[1].split()
    uids = re.findall(r"^Uid:\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$",
                      (root / "status").read_text(), re.M)
    command = (root / "cmdline").read_bytes()
    if len(stat) < 20 or int(stat[1]) != parent or uids != [tuple([str(os.getuid())] * 4)]:
        raise ValueError("Display parent or UID changed")
    process = {"pid": pid, "parent_pid": parent, "uid": os.getuid(),
               "start_ticks": int(stat[19]), "process_group": int(stat[2]), "session": int(stat[3])}
    if not command and allow_empty_command:
        return dict(process, executable=None, argv=None)
    if not command.endswith(b"\0") or len(command) > 16384:
        raise ValueError("Display command unavailable")
    return dict(process, executable=str((root / "exe").resolve(strict=True)),
                argv=command[:-1].decode("utf-8", "strict").split("\0"))


def display_number(reply):
    if not re.fullmatch(rb"(?:0|[1-9][0-9]{0,4})\n", reply) or int(reply) > 65535:
        raise ValueError("Invalid owned display response")
    return ":" + reply.decode("ascii").strip()


def exited(descriptor, seconds=0):
    return bool(select.select([descriptor], [], [], seconds)[0])


def ready(pid, executable, directory, report):
    binary = Path(executable).resolve(strict=True)
    if binary.name != "Xvfb":
        raise ValueError("Installed Xvfb required")
    expected_command = [str(binary), *ARGUMENTS]
    deadline = time.monotonic() + START_SECONDS
    descriptor = os.pidfd_open(pid)
    try:
        while time.monotonic() < deadline:
            if exited(descriptor):
                raise RuntimeError("Owned Xvfb exited during startup")
            current = identity(pid, os.getppid(), allow_empty_command="process" not in report)
            process = {key: value for key, value in current.items() if key not in ("executable", "argv")}
            if "startup_process" in report and report["startup_process"] != process:
                raise ValueError("Owned display startup identity changed")
            report["startup_process"] = process
            if "process" in report and report["process"] != current:
                raise ValueError("Owned display identity changed")
            if current["executable"] == str(binary) and current["argv"] == expected_command:
                report["process"] = current
                with (directory / "display.txt").open("rb") as stream:
                    reply = stream.read(17)
                if len(reply) > 16:
                    raise ValueError("Owned display response oversized")
                if b"\n" in reply:
                    report["display"] = display_number(reply)
                    with binary.open("rb") as stream:
                        report["executable_sha256"] = hashlib.file_digest(stream, "sha256").hexdigest()
                    if exited(descriptor) or identity(pid, os.getppid()) != current:
                        raise RuntimeError("Owned display ended before readiness")
                    if time.monotonic() >= deadline:
                        raise TimeoutError("Owned Xvfb startup deadline")
                    report["status"] = "READY"
                    return
            exited(descriptor, min(0.05, max(0, deadline - time.monotonic())))
        raise TimeoutError("Owned Xvfb startup deadline")
    finally:
        os.close(descriptor)


def stop_process(pid, expected, parent, report):
    try:
        descriptor = os.pidfd_open(pid)
    except ProcessLookupError:
        report["status"] = "ALREADY_EXITED"
        return
    try:
        if exited(descriptor):
            report["status"] = "ALREADY_EXITED"
            return
        current = identity(pid, parent)
        if current != expected:
            raise ValueError("Owned display identity changed before cleanup")
        report["process"] = current
        for number in (signal.SIGTERM, signal.SIGKILL):
            try:
                signal.pidfd_send_signal(descriptor, number)
                report.setdefault("signals", []).append(int(number))
            except ProcessLookupError:
                pass
            if exited(descriptor, STOP_SECONDS):
                report["status"] = "EXITED"
                return
        raise TimeoutError("Owned Xvfb cleanup deadline")
    finally:
        os.close(descriptor)


def stop(pid, executable, directory, report):
    try:
        descriptor = os.pidfd_open(pid)
    except ProcessLookupError:
        report["status"] = "ALREADY_EXITED"
        return
    try:
        if exited(descriptor):
            report["status"] = "ALREADY_EXITED"
            return
        start_path = directory / "ready.json"
        previous = json.loads(start_path.read_text()) if start_path.exists() else {}
        expected = previous.get("process") or identity(pid, os.getppid())
        startup = previous.get("startup_process")
        if startup is not None and startup != {
                key: value for key, value in expected.items() if key not in ("executable", "argv")}:
            raise ValueError("Owned display startup identity changed before cleanup")
        binary = str(Path(executable).resolve(strict=True))
        if expected["pid"] != pid or expected["executable"] != binary \
                or expected["argv"] != [binary, *ARGUMENTS]:
            raise ValueError("Cleanup target is not owned Xvfb")
    finally:
        os.close(descriptor)
    stop_process(pid, expected, os.getppid(), report)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("ready", "stop"))
    parser.add_argument("pid", type=int)
    parser.add_argument("executable")
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    report = {"scope": "OWN_NATIVE_XVFB", "pid": args.pid, "status": "FAILED",
              "started_unix_ns": time.time_ns(), "started_monotonic_ns": time.monotonic_ns(),
              "startup_limit_seconds": START_SECONDS, "signal_grace_seconds": STOP_SECONDS}
    try:
        if not hasattr(os, "pidfd_open") or not hasattr(signal, "pidfd_send_signal"):
            raise RuntimeError("PID-bound display lifecycle unavailable")
        (ready if args.phase == "ready" else stop)(args.pid, args.executable, args.directory, report)
    except Exception as error:
        report["error"] = {"type": type(error).__name__, "message": str(error)}
        raise
    finally:
        report["finished_unix_ns"] = time.time_ns()
        report["finished_monotonic_ns"] = time.monotonic_ns()
        (args.directory / (args.phase + ".json")).write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
