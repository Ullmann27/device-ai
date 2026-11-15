"""Build portable Continuum project and static-web archives from the local sources."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'continuum-preview'
OUTPUT = ROOT / 'delivery'
OUTPUT.mkdir(exist_ok=True)
EXCLUDED = {'continuum-sheet.png', 'continuum-regular.woff2', 'continuum-semibold.woff2'}
SOURCE_COMMIT = '1b90611388cc2b1978ddfe32064a25dd3a08cee1'
ORIGINAL_HASH = 'c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a'

def allowed(path):
    return path.is_file() and not path.is_symlink() and path.name not in EXCLUDED and not any(p in {'node_modules', '__pycache__', '.git'} for p in path.parts) and path.suffix not in {'.blend1', '.log'}

app_files = [p for p in sorted(APP.rglob('*')) if allowed(p)]
assert hashlib.sha256((APP/'assets/continuum-avatar.glb').read_bytes()).hexdigest() == ORIGINAL_HASH
manifest = {
    'project': 'Device AI Continuum – Gestaltung mit erhaltenen Originalen',
    'sourceRepository': 'https://github.com/Ullmann27/device-ai',
    'sourceCommit': SOURCE_COMMIT,
    'designBranch': 'design/continuum-fui-web-20261115',
    'draftPullRequest': 'https://github.com/Ullmann27/device-ai/pull/850',
    'originalAvatarSha256': ORIGINAL_HASH,
    'originalAvatarBytes': 12038416,
    'originalOrb': 'interop/originals/ContinuumHologramRenderer.kt',
    'qa': {'domPassed': 21, 'domFailed': 0, 'browserGpuVerified': False, 'foldDeviceVerified': False},
    'limits': ['Argon4 ist eine gekennzeichnete Demonstration.', 'Android-KI, Mikrofon und TTS sind nicht verbunden.', 'Der Sprechzustand allein erzeugt keine Lippenbewegung.', 'Native Figmaframes zeigen den früheren Stand; zusätzlicher Codeupload fehlgeschlagen.', 'Die Video-Raumstudie verwendet einen ruhig gerenderten Originalkopf.'],
    'runtimeFiles': [{'path': str(p.relative_to(ROOT)), 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in app_files],
}
(ROOT/'Continuum_Quellennachweis.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')

project_files = list(app_files)
for directory in ['interop', 'handoff-3d', 'qa']:
    for p in sorted((ROOT/directory).rglob('*')):
        if allowed(p): project_files.append(p)
for name in ['Continuum_Designuebergabe.md', 'Continuum_Toolkette_und_Uebergabe.md', 'Continuum_Quellennachweis.json', 'Continuum_Vorschau.build.json', 'Continuum_Originale_Raumstudie.mp4', 'Continuum_Originale_Raumstudie.png', 'tools/build-standalone.py', 'tools/package-continuum.py']:
    p=ROOT/name
    if not p.is_file(): raise FileNotFoundError(p)
    project_files.append(p)
start = '''# Device AI Continuum – hier beginnen

## Sofort ansehen

Öffne die bestätigte private Webvorschau aus der Auslieferungsnachricht. Alle Projektdateien stehen dort unter „Projektdateien“. Die gesondert gelieferte Continuum_Vorschau.html enthält die vollständige Offlineansicht.

## Bearbeitbare App starten

Node.js 20 oder neuer. Im entpackten Projektordner:

```sh
cd continuum-preview
npm start
```

Es werden keine Laufzeitpakete installiert. Der Server nennt die lokale Adresse. Der Originalkopf wird vor dem Start auf seine Prüfsumme geprüft.

## Aufbau

- continuum-preview: fertige App einschließlich Originalmodell, Originalkugel, lokaler Schriften und Herkunftshinweise.
- interop: lesbare Adapter, unveränderte Kotlin-Originalquellen, reproduzierbare Builds und gezielte Prüfungen.
- handoff-3d: bearbeitbare Blender-Szenen, Display-GLB, Renderbilder und Builder.
- qa: Bedienprüfungen und tatsächliche Ergebnisse.
- Continuum_Designuebergabe.md: Aufbau, Entscheidungen und verbleibende Grenzen.
- Continuum_Toolkette_und_Uebergabe.md: konkrete Schritte und Prompts für die nächsten Anwendungen.

Der Webexport ist ein zusätzliches ZIP mit index.html direkt an der Wurzel; er eignet sich für statisches Hosting. Das Projektpaket enthält außerdem Quellen und 3D-Arbeitsdateien. Die Android-App und ihr Signierschlüssel werden hier nicht verändert.
'''
(ROOT/'START_HIER.md').write_text(start)
project_files.append(ROOT/'START_HIER.md')
project_zip=ROOT/'Continuum_Projektpaket.zip'
web_zip=ROOT/'Continuum_Webexport.zip'
with zipfile.ZipFile(project_zip,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in project_files: z.write(p, 'Continuum_Projekt/'+str(p.relative_to(ROOT)))
with zipfile.ZipFile(web_zip,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in app_files:
        if p.name not in {'package.json','serve.mjs','prepare-assets.mjs','projekt.html'}:
            z.write(p,str(p.relative_to(APP)))
for archive in [project_zip,web_zip]:
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in z.namelist())
    print(json.dumps({'archive':str(archive),'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}))
