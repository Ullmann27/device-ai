#!/usr/bin/env python3
"""Real Android screenshots and a tap test of the Continuum start screen on a running emulator (adb).

Used by .github/workflows/continuum-ui-screenshots.yml. Only first-party Android tooling (adb, wm, am, screencap,
uiautomator, dumpsys). Profiles emulate the owner's devices by display size and density; the rasteriser is the real
Android one, but hinge, cutouts and Samsung system bars differ from the physical Galaxy Z Fold7 (documented in the
report). Nothing is installed outside the emulator.

The candidate under test is the feature-preview build (-PdeviceAiFeaturePreview=true): its own package
at.heinz.deviceai.featurepreview and data, never the owner's main app. Its activities keep the namespace
at.heinz.deviceai, so the component is given fully qualified.

Every tap is checked against TARGETS: the tapped element (recognised by its TalkBack description) must open the
screen it promises - its title must appear as text in the next UI dump. Start-screen cards are drawn views that only
carry descriptions, so a title match cannot come from the card that was tapped. Elements without a rule are reported
as UNMAPPED, never as passed. Exit code 1 when a tap fails or nothing could be verified.

usage: continuum-emulator-screenshots.py <apk> <outDir> [package] [activity]
"""
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "at.heinz.deviceai.featurepreview"
ACTIVITY = "at.heinz.deviceai.AgentActivity"
EXPECTED_VERSION_CODE = "9"           # app/build.gradle.kts, deviceAiFeaturePreview
ADB = os.environ.get("ADB", "adb")

# name, width px, height px, density dpi. Galaxy Z Fold7: cover 1080x2520, main 1968x2184 (portrait) at 420 dpi
# gives 411 x 960 dp and 750 x 832 dp; the phone profile is a common 1080x2400 / 420 dpi device.
PROFILES = [
    ("phone", 1080, 2400, 420),
    ("fold-cover", 1080, 2520, 420),
    ("fold-inner", 1968, 2184, 420),
    ("fold-inner-landscape", 2184, 1968, 420),
]

# Tapped element (regex on its TalkBack description) -> texts of which one must appear in the screen it opens.
# "text" looks at text nodes of the new screen (dialog titles, centre headings); "desc" at drawn start-screen views.
# Order matters: the first matching rule decides. Sources: AgentActivity.openHomeSlot, buildRail, the phone
# navigation (ContinuumWorkAreaNavigationSpec labels "Titel – Erklärung") and the show* screens' titles.
TARGETS = [
    (r"^Modellstatus", ("DEVICE AI · MODELLE", "KI-Sprachmodell"), "text"),
    (r"^Lernen und Forschung", ("Lernzentrum",), "text"),
    (r"^Speicher und Quellen", ("Gedächtniszentrum", "Mentor · Quellenbibliothek"), "text"),
    (r"^Entwicklung und Code", ("Entwicklungszentrum", "Backlog"), "text"),
    (r"^Systemstatus", ("Systemstatus",), "text"),
    (r"^Systemaktivität", ("CONTINUUM · FEHLER & DIAGNOSE",), "text"),
    (r"offene Hinweise$|^Keine offenen Hinweise", ("CONTINUUM · FEHLER & DIAGNOSE",), "text"),
    (r"^Wissensarchiv durchsuchen", ("Continuum · lokales Wissensarchiv",), "text"),
    (r"^Persönliche Einstellungen", ("Deine KI personalisieren",), "text"),
    (r"^VERBUNDEN:", ("Continuum · Internetzugang", "Dauerhaften Webzugang aktivieren?"), "text"),
    (r"^DENKEN:|^Chat\b", ("Schreibe eine Nachricht", "‹ START"), "text"),
    (r"^LERNEN:|^Lernen\b", ("Lernzentrum",), "text"),
    (r"^ANALYSIEREN:|^Analysieren\b", ("Internet ist ausgeschaltet", "RESEARCH", "Recherche"), "text"),
    (r"^ENTWICKELN:|^Entwickeln\b", ("Entwicklungszentrum",), "text"),
    (r"^EVOLVIEREN:", ("Persistenter gemeinsamer Lernkern",), "text"),
    (r"^Speicher$", ("Gedächtniszentrum",), "text"),
    (r"^System$", ("Systemstatus",), "text"),
    (r"^Mehr\b", ("Weitere Arbeitsbereiche",), "text"),
    (r"^Start\b|^Continuum\. Dein KI-Universum", ("Modellstatus",), "desc"),
]


def rule_for(label):
    return next((r for r in TARGETS if re.search(r[0], label or "")), None)


def verify_tap(label, after):
    """('PASS' | 'FAIL' | 'UNMAPPED', expected) for a tap on [label] given the nodes of the next screen."""
    rule = rule_for(label)
    if rule is None:
        return "UNMAPPED", []
    _, expected, where = rule
    haystack = [n["text"] for n in after] if where == "text" else [n["desc"] for n in after]
    ok = any(e in h for h in haystack if h for e in expected)
    return ("PASS" if ok else "FAIL"), list(expected)


def summarise(taps):
    out = {"PASS": 0, "FAIL": 0, "UNMAPPED": 0}
    for t in taps:
        out[t["verdict"]] += 1
    return out


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


def main():
    apk, out_dir = sys.argv[1], sys.argv[2]
    pkg = sys.argv[3] if len(sys.argv) > 3 else PACKAGE
    act = sys.argv[4] if len(sys.argv) > 4 else ACTIVITY

    def launch(wait=7.0):
        sh(f"am force-stop {pkg}")
        sh(f"am start -W -n {pkg}/{act}")
        time.sleep(wait)

    os.makedirs(out_dir, exist_ok=True)
    report = {"package": pkg, "activity": act, "profiles": [], "taps": [], "notes": []}
    adb("install", "-r", "-g", apk, timeout=300)
    package_info = sh(f"dumpsys package {pkg}")
    version = re.search(r"versionCode=(\d+)", package_info)
    report["installed"] = {"versionCode": version.group(1) if version else None,
                           "versionName": (re.search(r"versionName=(\S+)", package_info) or [None, None])[1]}
    if not version:
        raise SystemExit(f"{pkg} is not installed: the APK is not the expected variant")
    if pkg == PACKAGE and version.group(1) != EXPECTED_VERSION_CODE:
        report["notes"].append(f"versionCode {version.group(1)} differs from the feature preview's {EXPECTED_VERSION_CODE}")
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
            screencap(os.path.join(out_dir, f"{tag}.png"))
            xml = ui_dump(os.path.join(out_dir, f"{tag}.xml"))
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
            ns = [n for n in nodes(ui_dump(os.path.join(out_dir, f"{name}-taps-base.xml"))) if n["clickable"] and n["pkg"] == pkg]
            for i, n in enumerate(ns[:32]):
                launch(5.0)
                l, t, r, b = n["bounds"]
                sh(f"input tap {(l + r) // 2} {(t + b) // 2}")
                time.sleep(2.5)
                shot = f"{name}-tap{i:02d}.png"
                screencap(os.path.join(out_dir, shot))
                after = nodes(ui_dump(os.path.join(out_dir, f"{name}-tap{i:02d}.xml")))
                label = n["desc"] or n["text"] or n["cls"]
                verdict, expected = verify_tap(label, after)
                report["taps"].append({
                    "profile": name, "index": i, "label": label, "bounds": n["bounds"], "verdict": verdict,
                    "expected": expected, "focusAfter": focus(), "screenshot": shot,
                    "textsAfter": [x["text"] or x["desc"] for x in after if (x["text"] or x["desc"])][:25],
                })
                sh("input keyevent KEYCODE_BACK")
    sh("wm size reset")
    sh("wm density reset")
    sh("wm size 1080x2400")
    sh("wm density 420")
    sh("settings put global animator_duration_scale 1")
    launch(12.0)
    screencap(os.path.join(out_dir, "phone-animated.png"))
    report["gfxinfo"] = sh(f"dumpsys gfxinfo {pkg}")[:6000]
    report["meminfo"] = sh(f"dumpsys meminfo {pkg}")[:4000]
    report["notes"].append("Emulator (x86_64, software GPU): frame times and memory are not those of the physical device.")
    report["tapSummary"] = summarise(report["taps"])
    with open(os.path.join(out_dir, "report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print(json.dumps({k: (v if k != "taps" else len(v)) for k, v in report.items() if k not in ("gfxinfo", "meminfo")},
                     ensure_ascii=False)[:4000])
    for t in report["taps"]:
        if t["verdict"] != "PASS":
            print(f"{t['verdict']}: {t['profile']} '{t['label'][:60]}' expected one of {t['expected']}")
    summary = report["tapSummary"]
    sys.exit(1 if summary["FAIL"] or not summary["PASS"] else 0)


if __name__ == "__main__":
    main()
