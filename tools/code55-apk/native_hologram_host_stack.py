#!/usr/bin/env python3
"""Bound the original host stack."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid

SCOPE = "OWN_EMULATOR_PRE_RESIZE_STACK"
HOST_TIMEOUT = 10
KILL_GRACE = 2
ACK_RESERVE = 5
CLEANUP_RESERVE = 4


def process_fields(pid, stat):
    match = re.fullmatch(r"(\d+) \(.*\) (.+)", stat.strip())
    if not match or match[1] != str(pid):
        raise ValueError("Host process stat mismatch")
    fields = match[2].split()
    if len(fields) < 20 or not all(fields[index].isdigit() for index in (1, 2, 3, 19)):
        raise ValueError("Host process identity incomplete")
    return fields


def process_start(pid, stat):
    return process_fields(pid, stat)[19]


def launch_debugger(marker, stop, nonce, argv):
    pid = os.getpid()
    if os.getpgrp() != pid:
        os.setpgid(0, 0)
    parents = []
    parent = os.getppid()
    while parent > 1 and len(parents) < 16:
        fields = process_fields(parent, Path(f"/proc/{parent}/stat").read_text())
        parents.append({"pid": parent, "start_ticks": fields[19]})
        parent = int(fields[1])
    value = {"pid": pid, "pgid": os.getpgrp(), "sid": os.getsid(0), "uid": os.getuid(),
             "start_ticks": process_start(pid, Path("/proc/self/stat").read_text()),
             "parents": parents, "nonce": nonce, "argv": argv}
    temporary = Path(str(marker) + ".tmp")
    with temporary.open("x") as stream:
        os.fchmod(stream.fileno(), 0o644)
        stream.write(json.dumps(value) + "\n")
        stream.flush()
        os.fsync(stream.fileno())
    temporary.rename(marker)
    if Path(stop).exists():
        return 125
    os.execv(argv[0], argv)


def group_members(group):
    members = []
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        try:
            fields = process_fields(int(entry.name), (entry / "stat").read_text())
        except FileNotFoundError:
            continue
        if int(fields[2]) == group["pgid"]:
            if int(fields[3]) != group["sid"] or int(fields[19]) < int(group["start_ticks"]):
                raise ValueError("Debugger process group identity changed")
            members.append({"pid": int(entry.name), "start_ticks": fields[19], "state": fields[0]})
    return members


def verify_status(pid, uid, status):
    if re.findall(r"^Pid:\s+(\d+)\s*$", status, re.M) != [str(pid)]:
        raise ValueError("Host thread identity changed")
    if re.findall(r"^Uid:\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$", status, re.M) != [tuple([str(uid)] * 4)]:
        raise ValueError("Host emulator UID changed")
    if re.findall(r"^TracerPid:\s+(\d+)\s*$", status, re.M) != ["0"]:
        raise ValueError("Host emulator remains traced")
    states = re.findall(r"^State:\s+(\w)\b.*$", status, re.M)
    if len(states) != 1 or states[0] in ("T", "t", "Z", "X", "x"):
        raise ValueError("Host emulator is not resumed")


def read_process(pid, uid):
    root = Path(f"/proc/{pid}")
    status = (root / "status").read_text()
    verify_status(pid, uid, status)
    cmdline = (root / "cmdline").read_bytes()
    if not cmdline.endswith(b"\0") or len(cmdline) > 131072:
        raise ValueError("Host emulator command unavailable")
    return {"pid": pid, "uid": uid,
            "start_ticks": process_start(pid, (root / "stat").read_text()),
            "executable": str((root / "exe").resolve(strict=True)),
            "argv": cmdline[:-1].decode("utf-8").split("\0")}


def validate_identity(value, emulator, serial, source, uid):
    fields = {"schema", "scope", "serial", "source_sha", "pid", "uid", "start_ticks", "executable", "argv"}
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError("Unexpected host identity fields")
    if type(value["schema"]) is not int or value["schema"] != 1 or value["scope"] != SCOPE:
        raise ValueError("Unexpected host stack scope")
    if not re.fullmatch(r"emulator-[0-9]+", serial) or value["serial"] != serial:
        raise ValueError("Host emulator serial changed")
    if not re.fullmatch(r"[0-9a-f]{40}", source) or value["source_sha"] != source:
        raise ValueError("Host stack source changed")
    if type(value["pid"]) is not int or value["pid"] <= 1 or type(value["uid"]) is not int or value["uid"] != uid:
        raise ValueError("Own host emulator required")
    if not isinstance(value["start_ticks"], str) or not re.fullmatch(r"[0-9]+", value["start_ticks"]):
        raise ValueError("Host start identity missing")
    binary = Path(emulator).resolve(strict=True)
    expected = binary.parent / "qemu/linux-x86_64/qemu-system-x86_64-headless"
    if binary.name != "emulator" or expected.is_symlink() or not expected.resolve(strict=True).is_relative_to(binary.parent) \
            or str(expected.resolve(strict=True)) != value["executable"]:
        raise ValueError("Pinned host emulator executable required")
    argv = value["argv"]
    if not isinstance(argv, list) or not argv or not all(isinstance(s, str) and s and "\0" not in s for s in argv):
        raise ValueError("Host launch command invalid")
    if sum(len(s.encode("utf-8")) + 1 for s in argv) > 131072:
        raise ValueError("Host launch command oversized")
    return value


def bind_host(pid, emulator, serial, source, destination):
    value = dict(read_process(pid, os.getuid()), schema=1, scope=SCOPE, serial=serial, source_sha=source)
    validate_identity(value, emulator, serial, source, os.getuid())
    with Path(destination).open("x") as stream:
        stream.write(json.dumps(value, indent=2) + "\n")


class HostStack:
    def __init__(self, identity, emulator, serial, source, directory):
        self.identity = validate_identity(json.loads(Path(identity).read_text()), emulator, serial, source, os.getuid())
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.safe_to_resume = True
        self.process = None
        self.state = {"scope": SCOPE, "source_sha": source, "status": "NOT_REQUESTED", "identity": self.identity}
        self.verify("bound")

    def verify(self, label):
        actual = read_process(self.identity["pid"], self.identity["uid"])
        for key, value in actual.items():
            if self.identity[key] != value:
                raise ValueError("Host emulator identity changed: " + key)
        threads = []
        statuses = self.directory / (label + "-threads")
        statuses.mkdir()
        for task in sorted(Path(f"/proc/{self.identity['pid']}/task").iterdir()):
            try:
                status = (task / "status").read_text()
            except FileNotFoundError:
                continue
            verify_status(int(task.name), self.identity["uid"], status)
            (statuses / (task.name + "-status.txt")).write_text(status)
            threads.append(int(task.name))
        if self.identity["pid"] not in threads:
            raise ValueError("Host emulator main thread missing")
        process = Path(f"/proc/{self.identity['pid']}")
        (self.directory / (label + "-status.txt")).write_bytes((process / "status").read_bytes())
        (self.directory / (label + "-stat.txt")).write_bytes((process / "stat").read_bytes())
        self.state[label] = {"host_monotonic_nanos": time.monotonic_ns(), "untraced_threads": threads,
                             "cpu_affinity": sorted(os.sched_getaffinity(self.identity["pid"])),
                             "host_cpu_count": os.cpu_count(), "loadavg": Path("/proc/loadavg").read_text().strip(),
                             "host_memory_kib": {key: int(value) for key, value in re.findall(
                                 r"^(MemTotal|MemAvailable):\s+(\d+) kB$", Path("/proc/meminfo").read_text(), re.M)}}

    def finish_debugger(self):
        self.stop.write_text("CANCELLED\n")
        if self.process.poll() is None:
            try:
                self.process.terminate()
                self.process.wait(timeout=0.5)
            except (ProcessLookupError, PermissionError, subprocess.TimeoutExpired) as error:
                self.state["supervisor_stop"] = str(error)
        if self.marker.exists():
            group = json.loads(self.marker.read_text())
            supervisor = {"pid": self.process.pid, "start_ticks": self.supervisor_start}
            lineage = supervisor in group.get("parents", []) or (group.get("pid") == self.process.pid and
                group.get("start_ticks") == self.supervisor_start)
            if group.get("nonce") != self.nonce or group.get("argv") != self.driver_argv \
                    or type(group.get("pid")) is not int or group["pid"] <= 1 or group.get("pgid") != group["pid"] \
                    or group.get("uid") != 0 or type(group.get("sid")) is not int or group["sid"] <= 1 \
                    or not isinstance(group.get("start_ticks"), str) or not group["start_ticks"].isdigit() \
                    or not lineage:
                raise ValueError("Owned debugger group was not bound")
            if group["pgid"] == os.getpgid(self.identity["pid"]):
                raise ValueError("Debugger cannot share emulator group")
            self.state["debugger_group"] = group
            members = group_members(group)
            self.state["debugger_group_before_cleanup"] = members
            if members:
                kill = shutil.which("kill")
                if not kill:
                    raise RuntimeError("Scoped debugger cleanup unavailable")
                argv = ([] if os.geteuid() == 0 else ["sudo", "-n", "--"]) + [
                    self.driver_argv[0], "--signal=TERM", "--kill-after=1s", "1s", kill,
                    "--signal", "KILL", "--", "-" + str(group["pgid"])]
                self.state["cleanup_argv"] = argv
                result = subprocess.run(argv, capture_output=True, timeout=2.5, start_new_session=True)
                self.state["cleanup_exit"] = result.returncode
                (self.directory / "cleanup-stdout.txt").write_bytes(result.stdout)
                (self.directory / "cleanup-stderr.txt").write_bytes(result.stderr)
            deadline = time.monotonic() + 0.5
            remaining = group_members(group)
            while any(member["state"] not in ("Z", "X", "x") for member in remaining) and time.monotonic() < deadline:
                time.sleep(0.01)
                remaining = group_members(group)
            self.state["debugger_group_after_cleanup"] = remaining
            if any(member["state"] not in ("Z", "X", "x") for member in remaining):
                raise RuntimeError("Debugger process group remains active")
        elif self.process.poll() is None:
            raise RuntimeError("Debugger launch completion unproved")
        self.process.wait(timeout=0.5)
        self.verify("after")
        self.safe_to_resume = True

    def capture(self, deadline):
        self.state["started_host_monotonic_nanos"] = time.monotonic_ns()
        try:
            required = HOST_TIMEOUT + KILL_GRACE + 1 + CLEANUP_RESERVE + ACK_RESERVE
            if deadline - time.monotonic() < required:
                raise TimeoutError("Insufficient shared deadline for host capture")
            self.verify("before")
            debugger, timeout = shutil.which("gdb"), shutil.which("timeout")
            if not debugger or not timeout:
                raise RuntimeError("Host debugger tools unavailable")
            if deadline - time.monotonic() < required:
                raise TimeoutError("Host identity check exhausted capture reserve")
            self.driver_argv = [
                timeout, "--foreground", "--signal=TERM", f"--kill-after={KILL_GRACE}s", f"{HOST_TIMEOUT}s",
                debugger, "-nx", "-q", "-batch", "-iex", "set auto-load off",
                "-iex", "set debuginfod enabled off", "-iex", "set pagination off",
                "-iex", "set print frame-arguments none", "-iex", "set print entry-values no",
                "-ex", f"attach {self.identity['pid']}", "-ex", "info threads",
                "-ex", "thread apply all -c bt 64", "-ex", "detach", "-ex", "quit"]
            self.nonce = uuid.uuid4().hex
            self.marker = self.directory / ("debugger-group-" + self.nonce + ".json")
            self.stop = self.directory / ("debugger-stop-" + self.nonce)
            argv = ([] if os.geteuid() == 0 else ["sudo", "-n", "--"]) + [
                sys.executable, str(Path(__file__).resolve()), "--launch-debugger", str(self.marker),
                str(self.stop), self.nonce, json.dumps(self.driver_argv)]
            self.state.update(argv=argv, status="CAPTURING")
            with (self.directory / "gdb-stdout.txt").open("wb") as output, \
                 (self.directory / "gdb-stderr.txt").open("wb") as error:
                self.process = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=output, stderr=error,
                    env=dict(os.environ, LC_ALL="C"), start_new_session=True)
                self.safe_to_resume = False
                try:
                    self.supervisor_start = process_start(self.process.pid, Path(f"/proc/{self.process.pid}/stat").read_text())
                    self.state["exit_code"] = self.process.wait(timeout=HOST_TIMEOUT + KILL_GRACE + 1)
                finally:
                    self.finish_debugger()
            output = (self.directory / "gdb-stdout.txt").read_text(errors="replace")
            self.state["frame_count"] = len(re.findall(r"^\s*#\d+\s", output, re.M))
            if self.state["exit_code"] != 0 or not self.state["frame_count"]:
                raise RuntimeError("Host stack incomplete; inspect raw debugger output")
            self.state["status"] = "CAPTURED"
        except Exception as error:
            self.state.update(status="FAILED", error=str(error))
            raise
        finally:
            self.state.update(safe_to_resume=self.safe_to_resume, finished_host_monotonic_nanos=time.monotonic_ns())
            (self.directory / "collection.json").write_text(json.dumps(self.state, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("pid", type=int)
    parser.add_argument("emulator")
    parser.add_argument("serial")
    parser.add_argument("source")
    parser.add_argument("destination")
    args = parser.parse_args()
    bind_host(args.pid, args.emulator, args.serial, args.source, args.destination)


if __name__ == "__main__":
    if len(sys.argv) == 6 and sys.argv[1] == "--launch-debugger":
        sys.exit(launch_debugger(sys.argv[2], sys.argv[3], sys.argv[4], json.loads(sys.argv[5])))
    main()
