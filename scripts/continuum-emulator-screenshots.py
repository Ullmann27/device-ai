#!/usr/bin/env python3
"""Capture isolated feature-preview UI and verify allowlisted navigation on an emulator.

usage: continuum-emulator-screenshots.py <apk> <outDir>
Requires adb and aapt (or ADB/AAPT paths). Never installs on physical devices.
Display overrides approximate Fold sizes, not hinge/cutout/Samsung behavior.
"""
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = 'at.heinz.deviceai.featurepreview'
# applicationIdSuffix does not change the Kotlin Activity namespace.
ACT = 'at.heinz.deviceai.AgentActivity'
ADB = os.environ.get('ADB', 'adb')
AAPT = os.environ.get('AAPT', 'aapt')
HOME_DESCRIPTION = 'Continuum. Dein KI-Universum. Ohne Grenzen.'
PROFILES = [
    ('phone', 1080, 2400, 420),
    ('fold-cover', 1080, 2520, 420),
    ('fold-inner', 1968, 2184, 420),
    ('fold-inner-landscape', 2184, 1968, 420),
]
# Only navigation; no tool commands, model downloads, research or training buttons.
NAVIGATION = {
    'start': ('Start', 'Start – Startseite und Übersicht öffnen'),
    'chat': ('Chat', 'Chat – Mit der lokalen KI sprechen'),
    'lernen': ('Lernen', 'Lernen – Lernzentrum und Trainingsstand öffnen'),
    'entwickeln': ('Entwickeln', 'Entwickeln – Sichere Entwicklungswerkzeuge öffnen'),
    'mehr': ('Mehr', 'Mehr – Forschung, Modelle, Speicher und System öffnen'),
}


def samples():
    for profile in PROFILES:
        for scale in ('1.0', '1.3'):
            yield profile[0] + ('-font130' if scale == '1.3' else ''), profile, scale


def adb(*args, check=True, binary=False, timeout=120):
    result = subprocess.run([ADB, *args], capture_output=True, timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.decode(errors='replace')[:400]}")
    return result.stdout if binary else result.stdout.decode(errors='replace')


def sh(command, check=True):
    return adb('shell', command, check=check)


def require_emulator():
    if sh('getprop ro.kernel.qemu').strip() != '1':
        raise RuntimeError('Refusing installation: selected adb target is not an Android emulator')


def validate_apk_badging(badging):
    match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    if not match or match.group(1) != PKG:
        raise RuntimeError(f'Refusing installation: APK package must be {PKG}')


def screencap(path):
    data = adb('exec-out', 'screencap', '-p', binary=True)
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise RuntimeError('screencap did not return PNG data')
    with open(path, 'wb') as handle:
        handle.write(data)


def ui_dump(path):
    # Clear the old dump so an unsuccessful dump cannot reuse stale evidence.
    sh('rm -f /sdcard/continuum-ui.xml')
    sh('uiautomator dump /sdcard/continuum-ui.xml')
    xml = adb('exec-out', 'cat', '/sdcard/continuum-ui.xml')
    with open(path, 'w', encoding='utf-8') as handle:
        handle.write(xml)
    nodes(xml)
    return xml


def nodes(xml):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError as error:
        raise RuntimeError('UI hierarchy is missing or malformed') from error
    result = []
    for node in root.iter('node'):
        match = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.get('bounds', ''))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        result.append({
            'text': node.get('text', ''), 'desc': node.get('content-desc', ''),
            'cls': node.get('class', ''), 'pkg': node.get('package', ''),
            'id': node.get('resource-id', ''), 'clickable': node.get('clickable') == 'true',
            'enabled': node.get('enabled') == 'true', 'focused': node.get('focused') == 'true',
            'bounds': [left, top, right, bottom], 'w': right - left, 'h': bottom - top,
        })
    if not result:
        raise RuntimeError('UI hierarchy contains no bounded nodes')
    return result


def focus():
    dump = sh('dumpsys window windows') + sh('dumpsys window')
    match = re.search(r'mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}', dump)
    return match.group(1) if match else ''


def destination_reached(destination, hierarchy):
    app_nodes = [node for node in hierarchy if node['pkg'] == PKG]
    if destination == 'chat':
        return any(node['cls'] == 'android.widget.EditText' and node['focused'] for node in app_nodes)
    if destination == 'start':
        return any(node['desc'] == HOME_DESCRIPTION for node in app_nodes)
    title = {'lernen': 'Lernzentrum', 'entwickeln': 'Entwicklungszentrum', 'mehr': 'Weitere Arbeitsbereiche'}[destination]
    return any(node['text'] == title for node in app_nodes)


def navigation_target(hierarchy, destination):
    matches = [node for node in hierarchy if node['pkg'] == PKG and node['clickable'] and node['enabled']
               and node['desc'] in NAVIGATION[destination] and min(node['w'], node['h']) > 0]
    if len(matches) != 1:
        raise RuntimeError(f'{destination}: expected exactly one safe navigation target, found {len(matches)}')
    return matches[0]


def wait_destination(destination, path, timeout=20):
    deadline = time.monotonic() + timeout
    while True:
        hierarchy = nodes(ui_dump(path))
        current_focus = focus()
        if current_focus.startswith(PKG + '/') and destination_reached(destination, hierarchy):
            return hierarchy
        if time.monotonic() >= deadline:
            raise RuntimeError(f'{destination}: destination not reached; focus={current_focus!r}')
        time.sleep(1)


def launch():
    sh(f'am force-stop {PKG}')
    output = sh(f'am start -W -n {PKG}/{ACT}')
    if 'Error:' in output or 'Status: ok' not in output:
        raise RuntimeError(f'Activity launch failed: {output[:500]}')


def tap_navigation(destination, path):
    target = navigation_target(nodes(ui_dump(path)), destination)
    left, top, right, bottom = target['bounds']
    sh(f'input tap {(left + right) // 2} {(top + bottom) // 2}')
    return target


def main(apk, out):
    os.makedirs(out, exist_ok=True)
    report = {'package': PKG, 'activity': ACT, 'profiles': [], 'taps': [], 'status': 'FAILED', 'notes': [
        'Emulator x86_64/software GPU: not physical Samsung performance or fold/hinge/cutout evidence.',
        'Touch-target sizes are reported separately; this gate verifies capture and navigation, not visual similarity.',
    ]}
    # All checks precede install or settings mutations.
    require_emulator()
    badging = subprocess.run([AAPT, 'dump', 'badging', apk], check=True, capture_output=True, text=True).stdout
    validate_apk_badging(badging)
    old_font = sh('settings get system font_scale').strip()
    old_animator = sh('settings get global animator_duration_scale').strip()
    try:
        adb('install', '-r', '-g', apk, timeout=300)
        sh('settings put global animator_duration_scale 0')
        sh('settings put global sysui_demo_allowed 1')
        for command in ('enter', 'clock -e hhmm 1427', 'battery -e level 100 -e plugged false',
                        'network -e wifi show -e level 4', 'network -e mobile show -e datatype none -e level 4',
                        'notifications -e visible false'):
            sh(f'am broadcast -a com.android.systemui.demo -e command {command}')
        for tag, (name, width, height, dpi), scale in samples():
            sh(f'wm size {width}x{height}')
            sh(f'wm density {dpi}')
            sh(f'settings put system font_scale {scale}')
            launch()
            hierarchy = wait_destination('start', os.path.join(out, tag + '.xml'))
            screencap(os.path.join(out, tag + '.png'))
            small = [node for node in hierarchy if node['pkg'] == PKG and node['clickable']
                     and min(node['w'], node['h']) * 160 / dpi < 48]
            report['profiles'].append({
                'name': tag, 'px': [width, height], 'dpi': dpi, 'fontScale': scale, 'focus': focus(),
                'dp': [round(width * 160 / dpi), round(height * 160 / dpi)],
                'touchTargetsBelow48dp': [{'label': node['desc'] or node['text'], 'bounds': node['bounds']} for node in small],
            })
            if name not in ('phone', 'fold-inner'):
                continue
            for destination in NAVIGATION:
                path = os.path.join(out, f'{tag}-tap-{destination}.xml')
                launch()
                wait_destination('start', path)
                if destination == 'start':
                    # A no-op Start tap must fail: first leave home for Chat.
                    tap_navigation('chat', path)
                    chat = wait_destination('chat', path)
                    if destination_reached('start', chat):
                        raise RuntimeError('Chat still exposes home; cannot prove return navigation')
                    sh('input keyevent KEYCODE_BACK')  # dismiss IME to expose the phone bar
                    time.sleep(1)
                target = tap_navigation(destination, path)
                reached = wait_destination(destination, path)
                shot = f'{tag}-tap-{destination}.png'
                screencap(os.path.join(out, shot))
                report['taps'].append({'profile': tag, 'destination': destination, 'passed': True,
                                       'label': target['desc'], 'bounds': target['bounds'], 'focusAfter': focus(),
                                       'screenshot': shot, 'textsAfter': [node['text'] or node['desc'] for node in reached][:40]})
        sh('wm size 1080x2400')
        sh('wm density 420')
        sh('settings put system font_scale 1.0')
        sh('settings put global animator_duration_scale 1')
        launch()
        wait_destination('start', os.path.join(out, 'phone-animated.xml'))
        time.sleep(2)
        screencap(os.path.join(out, 'phone-animated.png'))
        report['gfxinfo'] = sh(f'dumpsys gfxinfo {PKG}')[:6000]
        report['meminfo'] = sh(f'dumpsys meminfo {PKG}')[:4000]
        report['status'] = 'PASSED'
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        for command in ('wm size reset', 'wm density reset',
                        f'settings put system font_scale {old_font}',
                        f'settings put global animator_duration_scale {old_animator}',
                        'am broadcast -a com.android.systemui.demo -e command exit'):
            try:
                sh(command, check=False)
            except (OSError, subprocess.SubprocessError):
                pass  # cleanup must preserve the original failure and its report
        with open(os.path.join(out, 'report.json'), 'w', encoding='utf-8') as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2)
    print(json.dumps({'status': report['status'], 'profiles': len(report['profiles']), 'taps': len(report['taps'])}))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: continuum-emulator-screenshots.py <apk> <outDir>')
    main(sys.argv[1], sys.argv[2])
