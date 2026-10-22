#!/usr/bin/env python3
"""Real Android screenshots and a tap test of the Continuum start screen on a running emulator (adb).

Used by .github/workflows/continuum-ui-screenshots.yml. Only first-party Android tooling (adb, wm, am, screencap,
uiautomator, dumpsys). Profiles emulate the owner's devices by display size and density; the rasteriser is the real
Android one, but hinge, cutouts and Samsung system bars differ from the physical Galaxy Z Fold7 (documented in the
report). Nothing is installed outside the emulator.

usage: continuum-emulator-screenshots.py <apk> <outDir> [package] [activity]
"""
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK, OUT = sys.argv[1], sys.argv[2]
PKG = sys.argv[3] if len(sys.argv) > 3 else "at.heinz.deviceai"
ACT = sys.argv[4] if len(sys.argv) > 4 else ".AgentActivity"
ADB = os.environ.get("ADB", "adb")

# name, width px, height px, density dpi. Galaxy Z Fold7: cover 1080x2520, main 1968x2184 (portrait) at 420 dpi
# gives 411 x 960 dp and 750 x 832 dp; the phone profile is a common 1080x2400 / 420 dpi device.
PROFILES = [
    ("phone", 1080, 2400, 420),
    ("fold-cover", 1080, 2520, 420),
    ("fold-inner", 1968, 2184, 420),
    ("fold-inner-landscape", 2184, 1968, 420),
]


def adb(*args, check=True, binary=False, timeout=120):
    r = subprocess.run([ADB, *args], capture_output=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.decode(errors='replace')[:400]}")
    return r.stdout if binary else r.stdout.decode(errors="replace")


def sh(cmd, check=False):
    return adb("shell", cmd, check=check)


def screencap(path):
    with open(path, "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))


def ui_dump(path):
    sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml", check=False)
    with open(path, "w", encoding="utf-8") as f:
        f.write(xml)
    return xml


def nodes(xml):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not m:
            continue
        l, t, r, b = map(int, m.groups())
        out.append({
            "text": n.get("text", ""), "desc": n.get("content-desc", ""), "cls": n.get("class", ""),
            "pkg": n.get("package", ""), "clickable": n.get("clickable") == "true",
            "bounds": [l, t, r, b], "w": r - l, "h": b - t,
        })
    return out


def focus():
    m = re.search(r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}", sh("dumpsys window windows") + sh("dumpsys window"))
    return m.group(1) if m else ""


def launch(wait=7.0):
    sh(f"am force-stop {PKG}")
    sh(f"am start -W -n {PKG}/{ACT}")
    time.sleep(wait)


def main():
    os.makedirs(OUT, exist_ok=True)
    report = {"package": PKG, "activity": ACT, "profiles": [], "taps": [], "notes": []}
    adb("install", "-r", "-g", APK, timeout=300)
    # Tidy, reproducible status bar like the reference images (14:27, full battery and signal).
    sh("settings put global sysui_demo_allowed 1")
    for c in ("enter", "clock -e hhmm 1427", "battery -e level 100 -e plugged false", "network -e wifi show -e level 4",
              "network -e mobile show -e datatype none -e level 4", "notifications -e visible false"):
        parts = c.split(" ", 1)
        extra = parts[1] if len(parts) > 1 else ""
        sh(f"am broadcast -a com.android.systemui.demo -e command {parts[0]} {extra}")
    for name, w, h, dpi in PROFILES:
        sh(f"wm size {w}x{h}")
        sh(f"wm density {dpi}")
        time.sleep(2)
        for scale in ("1.0", "1.3"):
            if scale != "1.0" and name not in ("phone", "fold-inner"):
                continue
            sh(f"settings put system font_scale {scale}")
            launch()
            tag = name if scale == "1.0" else f"{name}-font130"
            screencap(os.path.join(OUT, f"{tag}.png"))
            xml = ui_dump(os.path.join(OUT, f"{tag}.xml"))
            ns = nodes(xml)
            small = [n for n in ns if n["clickable"] and min(n["w"], n["h"]) * 160 / dpi < 48]
            report["profiles"].append({
                "name": tag, "px": [w, h], "dpi": dpi, "dp": [round(w * 160 / dpi), round(h * 160 / dpi)],
                "focus": focus(), "clickable": sum(1 for n in ns if n["clickable"]),
                "touchTargetsBelow48dp": [{"label": n["desc"] or n["text"], "bounds": n["bounds"]} for n in small],
                "texts": [n["text"] or n["desc"] for n in ns if (n["text"] or n["desc"])][:80],
            })
        sh("settings put system font_scale 1.0")
        if name in ("phone", "fold-inner"):
            launch()
            ns = [n for n in nodes(ui_dump(os.path.join(OUT, f"{name}-taps-base.xml"))) if n["clickable"] and n["pkg"] == PKG]
            for i, n in enumerate(ns[:24]):
                launch(5.0)
                l, t, r, b = n["bounds"]
                sh(f"input tap {(l + r) // 2} {(t + b) // 2}")
                time.sleep(2.5)
                shot = f"{name}-tap{i:02d}.png"
                screencap(os.path.join(OUT, shot))
                after = nodes(ui_dump(os.path.join(OUT, f"{name}-tap{i:02d}.xml")))
                report["taps"].append({
                    "profile": name, "index": i, "label": n["desc"] or n["text"] or n["cls"], "bounds": n["bounds"],
                    "focusAfter": focus(), "screenshot": shot,
                    "textsAfter": [x["text"] or x["desc"] for x in after if (x["text"] or x["desc"])][:25],
                })
                sh("input keyevent KEYCODE_BACK")
    sh("wm size reset")
    sh("wm density reset")
    sh("wm size 1080x2400")
    sh("wm density 420")
    sh("settings put global animator_duration_scale 1")
    launch(12.0)
    screencap(os.path.join(OUT, "phone-animated.png"))
    report["gfxinfo"] = sh(f"dumpsys gfxinfo {PKG}")[:6000]
    report["meminfo"] = sh(f"dumpsys meminfo {PKG}")[:4000]
    report["notes"].append("Emulator (x86_64, software GPU): frame times and memory are not those of the physical device.")
    with open(os.path.join(OUT, "report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print(json.dumps({k: (v if k != "taps" else len(v)) for k, v in report.items() if k not in ("gfxinfo", "meminfo")},
                     ensure_ascii=False)[:4000])


if __name__ == "__main__":
    main()
