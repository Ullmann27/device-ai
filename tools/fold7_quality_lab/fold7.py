#!/usr/bin/env python3
"""Read-only Fold7 acceptance telemetry. No app actions or raw dumps persisted."""
import argparse
import datetime as dt
import json
import re
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

PACKAGE = "at.heinz.deviceai.continuumlocal"
SCHEMA = "continuum.fold7.telemetry.v1"


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def run(argv, timeout=20):
    try:
        p = subprocess.run(argv, capture_output=True, text=True, timeout=timeout, check=False)
        return p.returncode, p.stdout
    except (OSError, subprocess.TimeoutExpired):
        return None, ""


def adb(serial, *args):
    return run(["adb", "-s", serial, "shell", *args])


def number(pattern, data, multiplier=1.0, flags=re.I | re.M):
    m = re.search(pattern, data, flags)
    return float(m.group(1)) * multiplier if m else None


def parse_meminfo(data):
    # TOTAL PSS from Android dumpsys summary, not Java heap allocation.
    val = number(r"^\s*TOTAL\s+(\d+)\s+\d+\s+\d+", data)
    if val is None:
        val = number(r"^\s*TOTAL PSS:\s*(\d+)", data)
    return int(val) if val is not None else None


def parse_cpu(data, package=PACKAGE):
    escaped = re.escape(package)
    return number(r"^\s*([\d.]+)%\s+\d+/" + escaped + r"(?::[A-Za-z0-9_.]+)?:\s", data)


def parse_battery(data):
    value = number(r"^\s*temperature:\s*(\d+)", data)
    return round(value / 10, 1) if value is not None else None


def parse_thermal(data):
    value = number(r"(?:Thermal Status|mStatus):\s*([0-6])\b", data)
    return int(value) if value is not None else None


def parse_gfxinfo(data, budget_ms):
    frames = []
    inside = False
    columns = None
    for line in data.splitlines():
        s = line.strip()
        if s == "---PROFILEDATA---":
            inside = not inside
            columns = None
            continue
        if not inside:
            continue
        fields = s.split(",")
        if s.startswith("Flags,"):
            columns = {name: i for i, name in enumerate(fields)}
            continue
        if not columns or "IntendedVsync" not in columns or "FrameCompleted" not in columns:
            continue
        try:
            if fields[columns["Flags"]] != "0":
                continue
            intended = int(fields[columns["IntendedVsync"]])
            completed = int(fields[columns["FrameCompleted"]])
            if not 0 < intended < completed < 2**63 - 1:
                continue
            # Preserve completed multi-second stalls.
            frames.append((completed - intended) / 1e6)
        except (ValueError, IndexError, KeyError):
            continue
    if not frames:
        return {"frame_count": 0, "frame_p95_ms": None, "over_budget_pct": None}
    ordered = sorted(frames)
    p95 = ordered[max(0, (95 * len(ordered) + 99) // 100 - 1)]
    return {"frame_count": len(frames), "frame_p95_ms": round(p95, 3),
            "over_budget_pct": round(100 * sum(x > budget_ms for x in frames) / len(frames), 2)}


def parse_package(data):
    version = re.search(r"\bversionName=([^\s]+)", data)
    code = re.search(r"\bversionCode=(\d+)\b", data)
    return {"version_name": version.group(1) if version else None,
            "version_code": int(code.group(1)) if code else None}


def host():
    return {"python": sys.version.split()[0], "adb": shutil.which("adb") is not None,
            "apksigner": shutil.which("apksigner") is not None,
            "aapt": shutil.which("aapt") is not None,
            "perfetto_host": shutil.which("perfetto") is not None,
            "device_connected": "NOT_CHECKED"}


def capture(args):
    if not args.consent_read_only:
        raise ValueError("Explicit --consent-read-only required")
    if not shutil.which("adb"):
        raise RuntimeError("ADB_NOT_INSTALLED")
    if args.samples < 1 or args.samples > 120 or args.interval < 0 or args.interval > 600:
        raise ValueError("Invalid sampling bounds")
    if args.font_scale not in (1.0, 1.5, 2.0) or args.frame_budget_ms <= 0:
        raise ValueError("Invalid display conditions")
    code, device_list = run(["adb", "devices"])
    available = [line.split()[0] for line in device_list.splitlines()[1:]
                 if len(line.split()) >= 2 and line.split()[1] == "device"]
    if code != 0 or args.serial not in available:
        raise RuntimeError("DEVICE_NOT_AUTHORIZED_OR_CONNECTED")
    model = adb(args.serial, "getprop", "ro.product.model")[1].strip()[:80]
    sdk = adb(args.serial, "getprop", "ro.build.version.sdk")[1].strip()[:8]
    pm_code, pm = adb(args.serial, "pm", "path", PACKAGE)
    installed = pm_code == 0 and any(line.startswith("package:") for line in pm.splitlines())
    pkg_data = adb(args.serial, "dumpsys", "package", PACKAGE)[1] if installed else ""
    identity = parse_package(pkg_data)
    identity.update({"package": PACKAGE, "package_installed": installed,
                     "signature": "NOT_VERIFIED", "apk_identity": "NOT_VERIFIED"})
    observations = []
    for i in range(args.samples):
        sample = {"utc": now()}
        queries = [("pss_kib", ("dumpsys", "meminfo", PACKAGE), parse_meminfo),
                   ("cpu_pct", ("dumpsys", "cpuinfo"), parse_cpu),
                   ("battery_temperature_c", ("dumpsys", "battery"), parse_battery),
                   ("thermal_status", ("dumpsys", "thermalservice"), parse_thermal)]
        for key, command, parser in queries:
            code, data = adb(args.serial, *command)
            sample[key] = parser(data) if code == 0 and installed else None
        observations.append(sample)
        if i + 1 < args.samples:
            time.sleep(args.interval)
    gfx_code, gfx = adb(args.serial, "dumpsys", "gfxinfo", PACKAGE, "framestats")
    frames = parse_gfxinfo(gfx if gfx_code == 0 and installed else "", args.frame_budget_ms)
    result = {"schema": SCHEMA, "provenance": "device_adb_read_only", "captured_utc": now(),
              "device": {"model": model or None, "sdk": sdk or None, "serial_stored": False},
              "identity": identity,
              "conditions": {"scenario": args.scenario, "display": args.display,
                             "font_scale": args.font_scale, "frame_budget_ms": args.frame_budget_ms,
                             "workload_confirmed_by_operator": bool(args.workload_confirmed)},
              "observations": observations, "gfxinfo": frames,
              "limitations": ["gfxinfo may contain historical frames; no reset was performed",
                              "battery temperature is not CPU/GPU silicon temperature",
                              "read-only sampling cannot prove energy consumption or model inference",
                              "font scale and display are operator-declared, not changed by this tool"]}
    return result


def atomic_json(path, payload):
    dest = Path(path)
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        raise FileExistsError(f"Refusing overwrite: {dest}")
    dest.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def metric(data, key):
    values = [s[key] for s in data.get("observations", []) if isinstance(s.get(key), (float, int))]
    return round(statistics.mean(values), 3) if values else None


def compare(baseline, candidate):
    problems = []
    if any(x.get("schema") != SCHEMA or x.get("provenance") != "device_adb_read_only" for x in (baseline, candidate)):
        problems.append("Both sources must be genuine device captures")
    if baseline.get("conditions") != candidate.get("conditions"):
        problems.append("Conditions mismatch")
    if baseline.get("device") != candidate.get("device"):
        problems.append("Device model/SDK mismatch")
    for x in (baseline, candidate):
        if not x.get("conditions", {}).get("workload_confirmed_by_operator"):
            problems.append("Controlled identical workload not confirmed")
        if not x.get("identity", {}).get("package_installed"):
            problems.append("Original package not installed")
    if problems:
        return {"status": "NOT_COMPARABLE", "reasons": list(dict.fromkeys(problems)), "deltas": {}}
    changes = {}
    for name in ("pss_kib", "cpu_pct", "battery_temperature_c", "thermal_status"):
        a, b = metric(baseline, name), metric(candidate, name)
        changes[name] = {"baseline": a, "candidate": b,
                         "delta": round(b - a, 3) if a is not None and b is not None else None}
    for name in ("frame_p95_ms", "over_budget_pct"):
        a, b = baseline.get("gfxinfo", {}).get(name), candidate.get("gfxinfo", {}).get(name)
        changes[name] = {"baseline": a, "candidate": b,
                         "delta": round(b - a, 3) if a is not None and b is not None else None}
    return {"status": "EXPLORATORY_DEVICE_COMPARISON", "reasons": ["No statistical certainty from two sessions",
            "gfxinfo windows may overlap unless device workflow isolates them"], "deltas": changes}


def verify_apk(path, expected_sha):
    if not Path(path).is_file():
        raise FileNotFoundError(path)
    if not re.fullmatch(r"[0-9a-fA-F]{64}", expected_sha):
        raise ValueError("Expected SHA256 certificate digest must be 64 hex characters")
    cert = {"expected_package": PACKAGE, "package_match": None, "certificate_match": None,
            "status": "NOT_VERIFIED"}
    if shutil.which("apksigner"):
        c, out = run(["apksigner", "verify", "--print-certs", path], 60)
        m = re.search(r"Signer #1 certificate SHA-256 digest:\s*([\da-fA-F:]+)", out)
        if c == 0 and m:
            cert["certificate_match"] = m.group(1).replace(":", "").lower() == expected_sha.lower()
    if shutil.which("aapt"):
        c, out = run(["aapt", "dump", "badging", path], 60)
        m = re.search(r"^package: name='([^']+)'", out, re.M)
        if c == 0 and m:
            cert["package_match"] = m.group(1) == PACKAGE
    if cert["certificate_match"] is True and cert["package_match"] is True:
        cert["status"] = "VERIFIED_LOCAL_APK"
    elif cert["certificate_match"] is False or cert["package_match"] is False:
        cert["status"] = "FAILED_IDENTITY_CHECK"
    return cert


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="command", required=True)
    sub.add_parser("host")
    cp = sub.add_parser("capture")
    cp.add_argument("--serial", required=True)
    cp.add_argument("--consent-read-only", action="store_true")
    cp.add_argument("--scenario", required=True)
    cp.add_argument("--display", choices=("inner", "cover"), required=True)
    cp.add_argument("--font-scale", type=float, required=True)
    cp.add_argument("--frame-budget-ms", type=float, required=True)
    cp.add_argument("--samples", type=int, default=3)
    cp.add_argument("--interval", type=float, default=5)
    cp.add_argument("--workload-confirmed", action="store_true")
    cp.add_argument("--output", required=True)
    co = sub.add_parser("compare")
    co.add_argument("baseline"); co.add_argument("candidate")
    co.add_argument("--output", required=True)
    ap = sub.add_parser("verify-apk")
    ap.add_argument("--apk", required=True)
    ap.add_argument("--expected-cert-sha256", required=True)
    args = p.parse_args(argv)
    if args.command == "host":
        result = host()
    elif args.command == "capture":
        result = capture(args)
        atomic_json(args.output, result)
    elif args.command == "compare":
        result = compare(json.loads(Path(args.baseline).read_text()),
                         json.loads(Path(args.candidate).read_text()))
        atomic_json(args.output, result)
    else:
        result = verify_apk(args.apk, args.expected_cert_sha256)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exc:
        print(f"LAB_ERROR: {exc}", file=sys.stderr)
        sys.exit(2)
