import json,hashlib,pathlib
p=pathlib.Path('/workspace/continuum-checks/review-evidence-window-v1')
def case(id,question,source,nonnull,contains=(),maxChars=350,hashMode='correct'):
 return dict(id=id,question=question,source=source,nonnull=nonnull,contains=list(contains),maxChars=maxChars,hashMode=hashMode)
longterm='Quantenanker'+'x'*120
cases=[
case('window-01','Wozu dient das Wolkenradarverfahren?', 'Allgemeiner Buchvorspann. '*35+'Das Wolkenradarverfahren misst Schichtgrenzen mit der Kennung NEBEL-241. '+'Anhang ohne Fachbegriffe. '*20,True,['Wolkenradarverfahren','NEBEL-241']),
case('window-02','Wozu dient das Wolkenradarverfahren?', 'Das Wolkenradarverfahren misst sichtbare Wolkenhöhen.',False,hashMode='wrong'),
case('window-03','Was bedeutet Quantenmarker?', 'Fremdthema. '*40+'Keine passenden Messangaben zu diesem Datensatz.',False),
case('window-04','Wie arbeitet Prisma-17?', 'Prisma-170 ist eine anders bezeichnete Baureihe mit eigenem Datenblatt.',False),
case('window-05','Wie arbeitet Prisma-17?', 'Vorwort. '*65+'Prisma-17 nutzt gepulste Laser zur Abtastung. '+'Nachsatz. '*12,True,['Prisma-17']),
case('window-06','Was ist Caféquanten?', '😀 Zwischenbemerkung. '*28+'Cafe\u0301quanten beschreibt eine fiktive Messreihe. '+'Restlicher Anhang. '*20,True,['Cafe\u0301quanten'],maxChars=100),
case('window-07','Wie wirkt der Silberpuls?', '😀'*45+' Vorwort. '*20+'Der Silberpuls wird im Labor als Vergleichssignal verwendet. '+'😀 Nachwort. '*20,True,['Silberpuls'],maxChars=81),
case('window-08','Was sind Alpengletscher Windkanal Resonanz?', 'Alpengletscher '*100+'Zwischenteil. '*10+'Windkanal und Resonanz stehen hier gemeinsam im Versuchssatz. '+'Schluss. '*10,True,['Windkanal','Resonanz'],maxChars=120),
case('window-09','Was bedeutet '+longterm+'?', 'Vorspann zur Forschung. '+longterm+' bezeichnet ein einziges unteilbares Fantasiewort.',False,maxChars=80),
case('window-10','Was bedeutet Sternenkammer?', 'Neutrale Einleitung. '*30+'Die Schlussbemerkung nennt zuletzt die Sternenkammer.',True,['Sternenkammer'],maxChars=100),
case('window-11','Was bedeutet Sternenkammer?', 'Sternenkammer '+('x'*6000),False),
case('window-12','Sternenkammer '+('q'*512), 'Die Sternenkammer ist ein ausdrücklich fiktiver Laborraum.',False),
]
policy=dict(name='independent-evidence-window-v1',status='FROZEN_BEFORE_EXECUTION',scope='Pure lexical selector integrity, complete-anchor inclusion, exact substring/SHA256/UTF8 offsets; not factual correctness, title/consent gates, Android integration or source authority.',oracle='Non-null results must be exact bounded original-source substrings with both hashes/byte offsets correct and contain the labeled complete anchors. No result is allowed when no complete matching anchor can fit the budget.',cases=cases)
raw=(json.dumps(policy,ensure_ascii=False,indent=2)+'\n').encode()
assert not (p/'corpus.json').exists()
(p/'corpus.json').write_bytes(raw)
sha=hashlib.sha256(raw).hexdigest()
(p/'CORPUS.sha256').write_text(sha+'  corpus.json\n')
print('FROZEN cases='+str(len(cases))+' sha256='+sha)
