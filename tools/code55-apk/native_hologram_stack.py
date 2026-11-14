#!/usr/bin/env python3
"""Capture before initial resize."""
import argparse
import json
import os
from pathlib import Path
import re
import signal
import shlex
import subprocess
import sys
import time

PACKAGE = "at.heinz.deviceai.mentorpaired"
CACHE = "cache/code59-hologram-diagnostic"


def validate_request(value):
    expected = {"schema", "package", "pid", "uid", "nonce", "requested_monotonic_nanos"}
    if not isinstance(value, dict) or set(value) != expected:
        raise ValueError("Unexpected stack request fields")
    if type(value["schema"]) is not int or value["schema"] != 1 or value["package"] != PACKAGE:
        raise ValueError("Unexpected stack request scope")
    for key in ("pid", "uid", "requested_monotonic_nanos"):
        if type(value[key]) is not int or value[key] <= 0:
            raise ValueError("Invalid stack request identity")
    if not 10000 <= value["uid"] <= 19999:
        raise ValueError("Disposable application UID required")
    if not isinstance(value["nonce"], str) or not re.fullmatch(r"[0-9a-f]{32}", value["nonce"]):
        raise ValueError("Invalid stack request nonce")
    return value


def request_response(returncode, output):
    if returncode == 44:
        return None
    if returncode:
        raise RuntimeError(f"Stack readiness check failed ({returncode})")
    if len(output) > 4096:
        raise ValueError("Oversized stack request")
    return validate_request(json.loads(output))


def handoff_deadline(instrumentation_deadline, last_absent_poll_start, now):
    if last_absent_poll_start is None:
        raise ValueError("Request freshness has no absence witness")
    deadline = min(instrumentation_deadline, last_absent_poll_start + 30, now + 27)
    if deadline <= now:
        raise TimeoutError("Stack request is stale")
    return deadline


def verify_process(request, pidof, cmdline, status, stat):
    pid = str(request["pid"])
    if pidof.strip() != pid or cmdline.rstrip(b"\0") != PACKAGE.encode():
        raise ValueError("QA app process changed")
    uids = re.findall(r"^Uid:\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$", status, re.M)
    if uids != [tuple([str(request["uid"])] * 4)]:
        raise ValueError("QA app UID changed")
    match = re.fullmatch(r"(\d+) \(.*\) (.+)", stat.strip())
    if not match or match[1] != pid:
        raise ValueError("QA process stat mismatch")
    fields = match[2].split()
    if len(fields) < 20 or not fields[19].isdigit():
        raise ValueError("QA process start time missing")
    return fields[19]


class Session:
    def __init__(self, adb, serial, evidence, source):
        if not re.fullmatch(r"emulator-[0-9]+", serial) or not re.fullmatch(r"[0-9a-f]{40}", source):
            raise ValueError("Disposable emulator and source required")
        self.adb = [adb, "-s", serial]
        self.directory = Path(evidence) / "guest-stack"
        self.directory.mkdir(parents=True, exist_ok=True)
        self.state = {"source_sha": source, "serial": serial, "status": "NOT_REQUESTED",
                      "started_unix_ns": time.time_ns()}
        self.native_pending = False
        self.exit_marker = None
        self.exit_probes = 0
        self.capture_deadline = None
        self.instrumentation_deadline = None

    def command(self, label, *args, timeout=5, payload=None):
        if self.capture_deadline is not None:
            remaining = self.capture_deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Shared stack capture deadline exceeded")
            timeout = min(timeout, remaining)
        started = time.monotonic_ns()
        try:
            result = subprocess.run(self.adb + list(args), input=payload, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired as error:
            if label:
                (self.directory / (label + "-stdout.txt")).write_bytes(error.stdout or b"")
                (self.directory / (label + "-stderr.txt")).write_bytes(error.stderr or b"")
                (self.directory / (label + "-command.json")).write_text(json.dumps({
                    "argv": list(args), "status": "HOST_TIMEOUT", "started_host_monotonic_nanos": started,
                    "finished_host_monotonic_nanos": time.monotonic_ns()
                }, indent=2) + "\n")
            raise
        if label:
            (self.directory / (label + "-stdout.txt")).write_bytes(result.stdout)
            (self.directory / (label + "-stderr.txt")).write_bytes(result.stderr)
            (self.directory / (label + "-command.json")).write_text(json.dumps({
                "argv": list(args), "exit_code": result.returncode,
                "started_host_monotonic_nanos": started, "finished_host_monotonic_nanos": time.monotonic_ns()
            }, indent=2) + "\n")
        return result

    def read(self, label, *args, timeout=5):
        result = self.command(label, *args, timeout=timeout)
        if result.returncode:
            raise RuntimeError(f"{label}: command failed ({result.returncode})")
        return result.stdout.decode().strip()

    def device(self, label):
        for prop, expected in (("ro.kernel.qemu", "1"), ("ro.build.version.sdk", "35"),
                               ("ro.product.cpu.abi", "x86_64")):
            if self.read(label + "-" + prop, "shell", "getprop", prop) != expected:
                raise RuntimeError("Disposable emulator identity changed")

    def identity(self, request, label):
        pid = str(request["pid"])
        cmdline = self.command(label + "-cmdline", "exec-out", "cat", f"/proc/{pid}/cmdline")
        if cmdline.returncode:
            raise RuntimeError("QA process cmdline unavailable")
        return verify_process(request,
            self.read(label + "-pid", "shell", "pidof", PACKAGE), cmdline.stdout,
            self.read(label + "-uid", "shell", "cat", f"/proc/{pid}/status"),
            self.read(label + "-stat", "shell", "cat", f"/proc/{pid}/stat"))

    def native_exit(self):
        self.exit_probes += 1
        value = self.read(f"guest-debugger-exit-{self.exit_probes}", "exec-out", "cat", self.exit_marker, timeout=3)
        if not re.fullmatch(r"[0-9]{1,3}", value) or not 0 <= int(value) <= 255:
            raise RuntimeError("Guest debugger completion missing")
        self.native_pending = False
        self.state["guest_debugger_exit"] = int(value)
        return int(value)

    def capture(self, request, last_absent_poll_start):
        validate_request(request)
        self.capture_deadline = handoff_deadline(self.instrumentation_deadline, last_absent_poll_start, time.monotonic())
        self.state.update(status="CAPTURING", request=request,
            last_absent_poll_started_host_monotonic_nanos=int(last_absent_poll_start * 1_000_000_000),
            capture_deadline_host_monotonic_nanos=int(self.capture_deadline * 1_000_000_000))
        acknowledgement = {"schema": 1, "pid": request["pid"], "nonce": request["nonce"], "status": "FAILED"}
        try:
            self.device("capture")
            if self.read("capture-adb-uid", "shell", "id", "-u") != "0":
                raise RuntimeError("Native stack collector lost root")
            before = self.identity(request, "before")
            self.state["process_start_ticks"] = before
            if self.capture_deadline - time.monotonic() < 22:
                raise TimeoutError("Insufficient time for bounded guest debugger")
            self.exit_marker = f"/data/user/0/{PACKAGE}/{CACHE}/stack-exit-{request['nonce']}"
            shell = (f"/system/bin/timeout -k 2s 15s /system/bin/debuggerd -b {request['pid']}; "
                     f"code=$?; printf '%s\\n' \"$code\" > {self.exit_marker}; exit \"$code\"")
            self.native_pending = True
            try:
                result = self.command("native", "shell", "sh", "-c", shlex.quote(shell), timeout=22)
            finally:
                self.native_exit()
            if self.identity(request, "after") != before:
                raise RuntimeError("QA app process restarted")
            if result.returncode or self.state["guest_debugger_exit"] or not result.stdout.strip():
                raise RuntimeError(f"Native debugger failed ({result.returncode})")
            self.state["status"] = "CAPTURED"
            acknowledgement["status"] = "CAPTURED"
        except Exception as error:
            self.state.update(status="FAILED", error=str(error))
        finally:
            try:
                if self.native_pending:
                    raise RuntimeError("Guest debugger completion unproved; no acknowledgement")
                shell = f"cat > {CACHE}/stack-ack.tmp && mv {CACHE}/stack-ack.tmp {CACHE}/stack-ack.json"
                result = self.command("ack", "shell", "run-as", PACKAGE, "sh", "-c", shlex.quote(shell),
                                      payload=(json.dumps(acknowledgement) + "\n").encode())
                if result.returncode:
                    raise RuntimeError("Stack acknowledgement failed")
                self.state["acknowledged_host_monotonic_nanos"] = time.monotonic_ns()
            finally:
                self.capture_deadline = None

    def run(self, evidence):
        original_uid = None
        root_attempted = False
        instrumentation = None
        result_code = 1
        try:
            self.device("before")
            original_uid = self.read("adb-uid-before", "shell", "id", "-u")
            if original_uid not in ("0", "2000"):
                raise RuntimeError("Unexpected adb UID")
            self.state["adb_uid_before"] = original_uid
            if original_uid == "2000":
                root_attempted = True
                self.read("adb-root", "root", timeout=15)
                self.read("adb-root-wait", "wait-for-device", timeout=15)
            if self.read("adb-uid-root", "shell", "id", "-u") != "0":
                raise RuntimeError("Disposable stack diagnosis unavailable")
            self.device("root")
            help_result = self.command("guest-timeout-help", "shell", "/system/bin/timeout", "--help")
            self.state["guest_timeout_help_exit"] = help_result.returncode
            self.read("guest-timeout-command-probe", "shell", "/system/bin/timeout", "-k", "2s", "15s",
                      "/system/bin/true", timeout=22)
            output_path = Path(evidence) / "native-hologram.txt"
            with output_path.open("wb") as output:
                self.instrumentation_deadline = time.monotonic() + 240
                instrumentation = subprocess.Popen(["timeout", "-k", "2s", "240s"] + self.adb + ["shell", "am", "instrument", "-w", "-r",
                    "-e", "probe", "hologram", PACKAGE + ".test/at.heinz.deviceai.ContinuumVisualInstrumentation"],
                    stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
                handled = False
                last_absent_poll_start = None
                while instrumentation.poll() is None:
                    if time.monotonic() >= self.instrumentation_deadline:
                        raise TimeoutError("Original instrumentation deadline exceeded")
                    if not handled:
                        readiness = f"[ -f {CACHE}/stack-request.json ] || exit 44; head -c 4097 {CACHE}/stack-request.json"
                        poll_started = time.monotonic()
                        try:
                            response = self.command(None, "shell", "-T", "run-as", PACKAGE, "sh", "-c",
                                                    shlex.quote(readiness), timeout=3)
                        except subprocess.TimeoutExpired:
                            continue
                        request = request_response(response.returncode, response.stdout)
                        if request is None:
                            last_absent_poll_start = poll_started
                        else:
                            handled = True
                            self.capture(request, last_absent_poll_start)
                    time.sleep(0.25)
                result_code = instrumentation.returncode
            sys.stdout.write(output_path.read_text(errors="replace"))
            if self.state["status"] == "FAILED":
                result_code = 1
        except Exception as error:
            self.state.update(status="FAILED", error=str(error))
            result_code = 1
        finally:
            try:
                if instrumentation is not None and instrumentation.poll() is None:
                    try:
                        os.killpg(instrumentation.pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
                    try:
                        instrumentation.wait(timeout=2)
                    except subprocess.TimeoutExpired:
                        try:
                            os.killpg(instrumentation.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                        instrumentation.wait(timeout=2)
            except Exception as error:
                self.state["instrumentation_cleanup_error"] = str(error)
                result_code = 1
            try:
                if self.native_pending:
                    self.native_exit()
                if root_attempted:
                    self.read("adb-unroot", "unroot", timeout=15)
                    self.read("adb-unroot-wait", "wait-for-device", timeout=15)
                if original_uid is not None:
                    actual = self.read("adb-uid-after", "shell", "id", "-u")
                    self.state["adb_uid_after"] = actual
                    if actual != original_uid:
                        raise RuntimeError("Original adb UID not restored")
            except Exception as error:
                self.state["restore_error"] = str(error)
                result_code = 1
            self.state["finished_unix_ns"] = time.time_ns()
            (self.directory / "collection.json").write_text(json.dumps(self.state, indent=2) + "\n")
        return result_code


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("adb")
    parser.add_argument("serial")
    parser.add_argument("evidence")
    parser.add_argument("source")
    args = parser.parse_args()
    return Session(args.adb, args.serial, args.evidence, args.source).run(args.evidence)


if __name__ == "__main__":
    sys.exit(main())
