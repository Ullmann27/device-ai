#!/usr/bin/env python3
"""Condition metadata for the existing read-only Fold7 collector."""
import argparse
import hashlib
import json
import math
import re
import sys
import uuid

import fold7

SCHEMA = "continuum.system.performance.capture.v1"


def battery_conditions(text):
    level = fold7.number(r"^\s*level:\s*(\d+)", text)
    scale = fold7.number(r"^\s*scale:\s*(\d+)", text)
    percent = round(level * 100 / scale) if level is not None and scale and 0 <= level <= scale else None
    status = fold7.number(r"^\s*status:\s*([1-5])\b", text)
    charging = True if status == 2 else False if status in (3, 4, 5) else None
    return percent, charging


def available_bytes(text):
    for row in reversed(text.splitlines()):
        fields = row.split()
        if len(fields) >= 6 and fields[-1] == "/data":
            try:
                total, used, available = map(int, fields[1:4])
                return available * 1024 if total > 0 and 0 <= available <= total and used >= 0 else None
            except ValueError:
                return None
    return None


def new_frames(before, after):
    first = fold7.parse_gfx_frames(before)
    if not first:
        return [], "FRAME_BOUNDARY_UNAVAILABLE"
    boundary = max(row[0] for row in first)
    seen = set()
    result = []
    for intended, completed, duration in fold7.parse_gfx_frames(after):
        if intended <= boundary or (intended, completed) in seen:
            continue
        seen.add((intended, completed))
        result.append(duration)
    if len(result) > 4096:
        return [], "FRAME_SAMPLE_BOUND_EXCEEDED"
    return result, None if result else "NO_NEW_COMPLETED_FRAMES"


def apk_build_key(serial):
    code, output = fold7.adb(serial, "pm", "path", fold7.PACKAGE)
    paths = [line.removeprefix("package:") for line in output.splitlines() if line.startswith("package:")]
    if code != 0 or not paths or len(paths) > 32:
        return ""
    digests = []
    for path in sorted(paths):
        if not re.fullmatch(r"/data/app/[A-Za-z0-9_+./=\-]+\.apk", path):
            return ""
        code, result = fold7.adb(serial, "sha256sum", path)
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s+\S+\s*", result)
        if code != 0 or not match:
            return ""
        digests.append(match.group(1).lower())
    code, fingerprint = fold7.adb(serial, "getprop", "ro.build.fingerprint")
    if code != 0 or not fingerprint.strip():
        return ""
    material = fingerprint.strip() + "\n" + "\n".join(digests)
    return hashlib.sha256(material.encode()).hexdigest()


def capture(args):
    if not args.consent_read_only:
        raise ValueError("Explicit --consent-read-only required")
    if not 5 <= args.samples <= 120 or not 0.0 <= args.interval <= 30.0:
        raise ValueError("Use 5..120 samples and 0..30 second intervals")
    if args.font_scale not in (1.0, 1.5, 2.0) or not math.isfinite(args.frame_budget_ms) or not 1 <= args.frame_budget_ms <= 1000:
        raise ValueError("Invalid display conditions")
    for value in (args.protocol_key, args.workload_key, args.scenario):
        if not re.fullmatch(r"[A-Za-z0-9._\-]{1,120}", value):
            raise ValueError("Use short nonprivate protocol/workload identifiers")
    if not fold7.shutil.which("adb"):
        raise RuntimeError("ADB_NOT_INSTALLED")
    code, devices = fold7.run(["adb", "devices"])
    available = [row.split()[0] for row in devices.splitlines()[1:]
                 if len(row.split()) >= 2 and row.split()[1] == "device"]
    if code != 0 or args.serial not in available:
        raise RuntimeError("DEVICE_NOT_AUTHORIZED_OR_CONNECTED")
    build_key = apk_build_key(args.serial)
    first_code, first = fold7.adb(args.serial, "dumpsys", "gfxinfo", fold7.PACKAGE, "framestats")
    battery_code, battery_start = fold7.adb(args.serial, "dumpsys", "battery")
    start_battery = battery_conditions(battery_start if battery_code == 0 else "")
    args.workload_confirmed = args.background_controlled
    legacy = fold7.capture(args)
    last_code, last = fold7.adb(args.serial, "dumpsys", "gfxinfo", fold7.PACKAGE, "framestats")
    battery_code, battery_end = fold7.adb(args.serial, "dumpsys", "battery")
    end_battery = battery_conditions(battery_end if battery_code == 0 else "")
    space_code, space = fold7.adb(args.serial, "df", "-k", "/data")
    metrics, unavailable, failed = {}, {}, {}
    pss = [row.get("pss_kib") for row in legacy["observations"]]
    metrics["OWN_PSS_BYTES"] = [value * 1024 for value in pss if value is not None]
    failed["OWN_PSS_BYTES"] = sum(value is None for value in pss)
    free = available_bytes(space) if space_code == 0 else None
    if free is None:
        unavailable["AVAILABLE_STORAGE_BYTES"] = "DF_UNAVAILABLE"
    else:
        metrics["AVAILABLE_STORAGE_BYTES"] = [free]
    frame_values, frame_error = new_frames(first if first_code == 0 else "", last if last_code == 0 else "")
    metrics["FRAME_MILLIS"] = frame_values
    if frame_error:
        unavailable["FRAME_MILLIS"] = frame_error
    for key in ("APP_START_MILLIS", "UI_REACTION_MILLIS", "MODEL_TASK_MILLIS", "OWN_CPU_MILLIS"):
        unavailable[key] = "NOT_INSTRUMENTED"
    thermal = [row.get("thermal_status") for row in legacy["observations"]]
    temperature = [row.get("battery_temperature_c") for row in legacy["observations"]]
    stable_thermal = thermal[0] if thermal and None not in thermal and len(set(thermal)) == 1 else None
    stable_temperature = temperature[0] if temperature and None not in temperature and max(temperature) - min(temperature) <= 2 else None
    stable_battery = start_battery[0] if None not in (start_battery[0], end_battery[0]) and abs(start_battery[0] - end_battery[0]) <= 5 else None
    stable_charging = start_battery[1] if start_battery[1] == end_battery[1] else None
    return {"schema": SCHEMA, "provenance": "device_adb_read_only", "id": str(uuid.uuid4()),
            "observedAtMs": round(fold7.time.time() * 1000),
            "conditions": {"deviceKey": hashlib.sha256(args.serial.encode()).hexdigest(),
                "buildKey": build_key, "protocolKey": args.protocol_key, "workloadKey": args.workload_key,
                "displayKey": f"{args.display}:{args.font_scale}:{args.frame_budget_ms}",
                "warmState": args.warm_state.upper(), "batteryPercent": stable_battery,
                "charging": stable_charging, "batteryTemperatureC": stable_temperature,
                "thermalStatus": stable_thermal, "backgroundControlled": args.background_controlled},
            "metrics": metrics, "unavailable": unavailable, "failedSamples": failed,
            "conditionObservations": {"batteryStart": start_battery, "batteryEnd": end_battery,
                "thermalStatus": thermal, "batteryTemperatureC": temperature},
            "limitations": ["No speedup verdict from capture alone",
                "Device key is a pseudonymous serial hash; store privately",
                "Read-only app APK hashing may add transient IO before sampling",
                "Frame boundary uses fresh gfxinfo timestamps, not counter reset",
                "Display, warm state and background control are operator declarations",
                "PSS does not mean reclaimable or wasted RAM",
                "Battery temperature is not CPU/GPU temperature",
                "Uninstrumented startup/UI/model/CPU tasks remain unavailable"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--consent-read-only", action="store_true")
    parser.add_argument("--protocol-key", required=True)
    parser.add_argument("--workload-key", required=True)
    parser.add_argument("--scenario", required=True)
    parser.add_argument("--display", choices=("inner", "cover"), required=True)
    parser.add_argument("--font-scale", type=float, required=True)
    parser.add_argument("--frame-budget-ms", type=float, required=True)
    parser.add_argument("--warm-state", choices=("unknown", "warm", "cold"), default="unknown")
    parser.add_argument("--background-controlled", action="store_true")
    parser.add_argument("--samples", type=int, default=5)
    parser.add_argument("--interval", type=float, default=5)
    parser.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    fold7.atomic_json(args.output, capture(args))
    print("Saved read-only capture; no performance verdict.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError) as exc:
        print(f"SYSTEM_CAPTURE_ERROR: {type(exc).__name__}", file=sys.stderr)
        sys.exit(2)
