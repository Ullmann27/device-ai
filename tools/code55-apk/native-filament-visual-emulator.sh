#!/usr/bin/env bash
set -euo pipefail
[[ $# == 2 ]]
serial="$1"
evidence="$2"
[[ "$serial" =~ ^emulator-[0-9]+$ ]]
bash tools/code55-apk/native-filament-emulator.sh "$serial" "$evidence/lifecycle"
adb="$ANDROID_HOME/platform-tools/adb"
timeout -k 2s 300s "$adb" -s "$serial" shell am instrument -w -r \
  -e probe filament -e capture_frames true \
  at.heinz.deviceai.mentorpaired.test/at.heinz.deviceai.ContinuumVisualInstrumentation \
  | tee "$evidence/visual-instrumentation.txt"
python3 - "$evidence/visual-instrumentation.txt" <<'PY'
from pathlib import Path
import re, sys
text = Path(sys.argv[1]).read_text()
def value(name):
    found = re.findall(r'^INSTRUMENTATION_RESULT: ' + re.escape(name) + r'=(.*?)\r?$', text, re.M)
    assert len(found) == 1, (name, text)
    return found[0]
assert value('status') == 'ok'
assert value('last_phase') == 'finished'
assert value('captured_frames') == '24'
assert value('capture_kind') == 'NATIVE_RGBA_OUTPUT; synthetic waiting state'
assert value('sdk') == '35'
for name in ('content_cleared', 'panels_released', 'engine_released'):
    assert value(name) == 'true'
assert re.findall(r'^INSTRUMENTATION_CODE: (-?\d+)\r?$', text, re.M) == ['-1']
assert 'INSTRUMENTATION_FAILED' not in text
PY
timeout -k 2s 60s "$adb" -s "$serial" pull \
  /sdcard/Android/data/at.heinz.deviceai.mentorpaired/files/filament-review "$evidence/frames"
python3 - "$evidence/frames" <<'PY'
from pathlib import Path
import csv, hashlib, json, struct, sys, zlib
root = Path(sys.argv[1])
rows = list(csv.DictReader((root / 'frames.tsv').open(), delimiter='\t'))
assert len(rows) == 24
sizes = set()
for index, row in enumerate(rows):
    assert row['file'] == f'frame-{index:03d}.png'
    raw = (root / row['file']).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == row['sha256']
    assert raw[:8] == b'\x89PNG\r\n\x1a\n'
    offset = 8
    while offset < len(raw):
        length, = struct.unpack('>I', raw[offset:offset+4])
        chunk = raw[offset+4:offset+8+length]
        expected, = struct.unpack('>I', raw[offset+8+length:offset+12+length])
        assert zlib.crc32(chunk) == expected
        if chunk[:4] == b'IHDR':
            width, height = struct.unpack('>II', chunk[4:12])
            assert (width, height) == (int(row['width']), int(row['height']))
            assert 64 <= width <= 2048 and 64 <= height <= 2048
            sizes.add((width, height))
        offset += length + 12
    assert offset == len(raw)
assert len(sizes) == 1
assert len({r['sha256'] for r in rows}) > 1
assert sorted(p.name for p in root.glob('*.png')) == [r['file'] for r in rows]
receipt = dict(status='PASS', frames=len(rows), dimensions=list(sizes.pop()),
    renderer='Android API35 ANGLE/SwiftShader Filament', application_source='889e06d47fffd2230fa5b062dd1f31b65992e728',
    content='SYNTHETIC WAITING STATE; no capture grant, Android action, inference or speech',
    timebase='12 FPS offline export; not measured device performance', Fold7='NOT_RUN')
(root.parent / 'visual-proof.json').write_text(json.dumps(receipt, indent=2)+'\n')
print(json.dumps(receipt))
PY
ffmpeg -hide_banner -loglevel error -y -framerate 12 -i "$evidence/frames/frame-%03d.png" \
  -vf 'pad=ceil(iw/2)*2:ceil(ih/2)*2' -c:v libx264 -pix_fmt yuv420p \
  "$evidence/native-fold-synthetic-waiting.mp4"
ffprobe -v error -show_entries stream=nb_frames,width,height,r_frame_rate \
  -of json "$evidence/native-fold-synthetic-waiting.mp4" > "$evidence/video-info.json"
python3 - "$evidence/video-info.json" <<'PY'
import json, sys
streams=json.load(open(sys.argv[1]))['streams']
assert len(streams)==1 and streams[0]['nb_frames']=='24' and streams[0]['r_frame_rate']=='12/1'
PY
