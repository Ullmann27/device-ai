from pathlib import Path
from hashlib import sha256
import argparse
import json
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.utils import ImageReader

parser = argparse.ArgumentParser()
parser.add_argument('--artifact-root', required=True, type=Path)
parser.add_argument('--avatar-manifest', type=Path)
args = parser.parse_args()
ROOT = args.artifact_root.resolve()
DELIVERY = ROOT / 'apk53-a980-delivery'
OUT = DELIVERY / 'Continuum-Code53-Originalbilder-und-Herkunft.pdf'
SOURCE = 'a980a19ed3ab44323009ae27eb299d322ff11679'
MANIFEST_SHA = '79516e94a2ceb82422ef5ccd64345519907a2fe6c0ec770b3668b9e95bf27f79'
MANIFEST = args.avatar_manifest or ROOT / 'apk53-a980-original-avatar/blender-images/pose-replay-manifest.json'
AVATAR_SPEC = [('Ruhe', 'Ruhe', 'ruhe.png'),
               ('Fensterblick', 'Fensterblick', 'fensterblick.png'),
               ('Sprechpose', 'Sprechen', 'sprechen-geskriptet.png')]
NATIVE_EXPECTED = {
    'Continuum-Code53-a980-Ruhebild.png': 'b111077257f093ed687b00f098803332637b886d4de160c55b3feb9d6a0d0f52',
    'Continuum-Code53-a980-Lichtfluss-280ms.png': '95749cc20bca20df4b05017a51e644d73539d7b841b6b4390327f8261779ab94',
    'Continuum-Code53-a980-Fensteraufbau-650ms.png': '1ee358f71572320f750c4c9531fc49734b48876cfa777b69ed54dfa39eb1be56',
    'Continuum-Code53-a980-Arbeitsfenster.png': '6024495eb3c028a13db6020a7c35a0864e8cbced9e6bf698fc8c7e425d93fee9',
    'Continuum-Code53-a980-Rueckkehr.png': 'f7e26eb7b3c32f4de037c22858c358cf5374c7ce566ee4555cca0fa32763cbc5',
    'Continuum-Code53-a980-Cover-Arbeitsfenster.png': '11f6fe13fc6889d9f6f32527f452a7cfd647ec8efb1ceeb15fa30117713a6f3a',
}

def validate_native_inputs(delivery):
    for filename, expected in NATIVE_EXPECTED.items():
        if sha256((delivery / filename).read_bytes()).hexdigest() != expected:
            raise ValueError(f'{filename} differs from frozen capture')

def validate_avatar_inputs(delivery, manifest_path):
    raw = manifest_path.read_bytes()
    if sha256(raw).hexdigest() != MANIFEST_SHA:
        raise ValueError('Unexpected pose manifest')
    expected = {row['file']: row['imageSha256'] for row in json.loads(raw)['frames']}
    result = []
    for label, suffix, original in AVATAR_SPEC:
        path = delivery / f'Continuum-Code53-Originalavatar-{suffix}-CPU.png'
        digest = sha256(path.read_bytes()).hexdigest()
        if digest != expected.get(original):
            raise ValueError(f'{path.name} differs from pose manifest')
        result.append({'file': path.name, 'manifestFile': original, 'sha256': digest})
    return result

avatar_frames = validate_avatar_inputs(DELIVERY, MANIFEST)
validate_native_inputs(DELIVERY)
FONT = Path('/usr/share/fonts/truetype/dejavu')
pdfmetrics.registerFont(TTFont('Body', str(FONT / 'DejaVuSans.ttf')))
pdfmetrics.registerFont(TTFont('Bold', str(FONT / 'DejaVuSans-Bold.ttf')))
W, H = 595.276, 841.89
c = canvas.Canvas(str(OUT), pagesize=(W, H), pageCompression=1, invariant=1)
c.setTitle('Continuum Code53 - Originalbilder und Herkunft')
c.setAuthor('Continuum Entwicklung')

def text(x, y, value, size=10, bold=False, color='#233340'):
    c.setFillColor(HexColor(color))
    c.setFont('Bold' if bold else 'Body', size)
    c.drawString(x, y, value)

def lines(y, values, size=10, step=17):
    for value in values:
        text(38, y, value, size)
        y -= step
    return y

def header(index, title, subtitle):
    c.setFillColor(HexColor('#081825'))
    c.rect(0, H - 112, W, 112, fill=1, stroke=0)
    text(38, H - 33, 'CONTINUUM  /  CODE 53', 10, True, '#67e8ed')
    text(38, H - 64, title, 21, True, '#ffffff')
    text(38, H - 88, subtitle, 9, False, '#c8dae3')
    c.setStrokeColor(HexColor('#c4d1db'))
    c.line(38, 42, W - 38, 42)
    text(38, 27, 'Originalbilder aus Testläufen - kein Fold7-Gerätetest', 8)
    c.setFont('Body', 8)
    c.drawRightString(W - 38, 27, f'{index} / 7')

def picture(path, box):
    x, y, width, height = box
    im = ImageReader(str(path))
    iw, ih = im.getSize()
    ratio = min(width / iw, height / ih)
    dw, dh = iw * ratio, ih * ratio
    c.drawImage(im, x + (width - dw) / 2, y + (height - dh) / 2,
                width=dw, height=dh, mask='auto')

header(1, 'Der unveränderte Originalavatar', 'Blender 4.3.2 / Cycles CPU / exportierte Animation aus a980a19')
for i, (label, suffix, original) in enumerate(AVATAR_SPEC):
    x = 38 + i * 176
    text(x, 696, label, 11, True)
    picture(DELIVERY / f'Continuum-Code53-Originalavatar-{suffix}-CPU.png', (x, 468, 166, 208))
lines(433, [
    'Diese drei Bilder gehören zu acht tatsächlich gerenderten Testposen.',
    'Der Original-GLB und seine 21 Morph Targets wurden nicht verändert.',
    'Die Posewerte stammen aus dem bestandenen AvatarMotionEvidenceTest.',
    'Kopf, Hals, Augen, Kiefer und Morphgewichte wurden unverändert übertragen.',
])
lines(344, [
    'Renderprofil: 512 x 640 Pixel, 16 Samples, ein CPU-Thread.',
    'Beleuchtung und Glow stammen aus der vorhandenen Studiovorschau.',
    'Die Sprechpose nutzt geskriptete TTS-Wortereignisse, kein echtes Audio.',
    'Blickereignisse sind Testdaten, kein Nachweis einer Kamerawahrnehmung.',
])
text(38, 248, 'Abnahmegrenze', 13, True)
lines(224, [
    'Dies ist kein Screenshot der Filament-Ausgabe auf dem Telefon.',
    'Filament/AGSL-GPU, echte Lippensynchronität, Fold7 und Thermik: NOT_RUN.',
    'Die folgenden Seiten zeigen den separaten Android-Canvas-Testpfad.',
    'Dort wird der bereits vorhandene 2D-Avatarersatz verwendet.',
])
text(38, 125, 'Quellstand', 10, True)
text(38, 105, SOURCE, 9)
c.linkURL('https://github.com/Ullmann27/device-ai/pull/746', (38, 65, 380, 82), relative=0)
text(38, 69, 'Quellgebundene Prüfnachweise: GitHub PR #746', 9, False, '#16647b')
c.showPage()

frames = [
    ('Ruhebild', 'Continuum-Code53-a980-Ruhebild.png', 'idle', False),
    ('Lichtfluss bei 280 ms', 'Continuum-Code53-a980-Lichtfluss-280ms.png', 'work-280ms', False),
    ('Fensteraufbau bei 650 ms', 'Continuum-Code53-a980-Fensteraufbau-650ms.png', 'work-650ms', False),
    ('Arbeitsfenster bei 1.180 ms', 'Continuum-Code53-a980-Arbeitsfenster.png', 'work-1180ms', False),
    ('Kontrollierte Rückkehr', 'Continuum-Code53-a980-Rueckkehr.png', 'returned', False),
    ('Arbeitsfenster im Cover-Layout', 'Continuum-Code53-a980-Cover-Arbeitsfenster.png', 'work-1180ms', True),
]
receipts = []
for index, (title, filename, phase, cover) in enumerate(frames, 2):
    path = DELIVERY / filename
    digest = sha256(path.read_bytes()).hexdigest()
    header(index, title, 'Robolectric API28 / natives Canvas / geskripteter Auftragsablauf')
    if cover:
        picture(path, (38, 195, W - 76, 510))
        y = 170
    else:
        picture(path, (38, 250, W - 76, 448))
        y = 219
    lines(y, [
        f'Phase: {phase}. Original-PNG, unverändert in dieses Dokument eingebettet.',
        f'Testfläche: {"412 x 915" if cover else "884 x 760"} Pixel; keine zugesicherte Geräteauflösung.',
        'Vorhandener 2D-Avatarersatz; keine Filament-Ausgabe oder Modellinferenz.',
        'Fold7-Gerätetest, GPU-Framezeiten und Wärmeverhalten: NOT_RUN.',
    ], 9, 15)
    text(38, 91, f'Quelle: {SOURCE}', 8)
    text(38, 75, f'PNG SHA-256: {digest}', 6.7)
    c.showPage()
    receipts.append({'file': filename, 'sha256': digest, 'phase': phase})

c.save()
receipt = {'source': SOURCE, 'pages': 7, 'file': OUT.name,
           'sha256': sha256(OUT.read_bytes()).hexdigest(), 'bytes': OUT.stat().st_size,
           'imageHandling': 'Unchanged original PNGs embedded with preserved aspect ratio',
           'nativeFrames': receipts, 'avatarFrames': avatar_frames,
           'avatarManifestSha256': MANIFEST_SHA,
           'Fold7': 'NOT_RUN', 'Filament': 'NOT_RUN'}
(DELIVERY / 'image-proof-pdf.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
