# LM1-Diagnose: unabhängiger Abgleich der Rohbelege

Prüfdatum: 04.11.2026. Versuch: `training-recovery-study-v1`, vier registrierte Diagnosearme. Prüfweg: eigener TSV-/JSON-Abgleich und eigene Nachrechnung aus den abgeschlossenen Rohlogs. Der Trainingshelfer hat auch die ausgewertete Kotlin-Diagnose implementiert; diese Prüfung ist keine unabhängige Autorenreplikation des Trainings.

**Ergebnis: Die geprüften Rohbelege sind untereinander konsistent. Alle vier Optimierungsläufe sind vollständig abgeschlossen; keiner erfüllt das registrierte Kriterium einer erfolgreichen begrenzten Fortsetzung.** Sämtliche Schlussbenchmarks brechen am unveränderten Kapazitätsprompt mit EOS und null neuen Token ab. Die fest vorgegebene erste Trainings-Minibatch wird dagegen in jedem Arm besser vorhergesagt. Die Prefixdiagnosen belegen dabei keinen allgemeinen EOS-Kollaps.

Die neue Gesamtlernrate von einem Achtel verbessert die Entwicklungs-BPB leicht, behebt den Kapazitätsfehlschlag aber nicht. Die isolierte Verringerung des finalen Norm-Updates wurde nachweisbar angewandt und liefert hier keine wirksame Behebung. Diese Ergebnisse grenzen die Hypothese ein; eine abschließend bestimmte EOS-Ursache folgt daraus nicht. [S], [D-small], [D-wide], [D-nf], [D-rate]

## 1. Umfang und Identität

Gelesen wurden ausschließlich die vier Diagnoseprotokolle, ihre anfänglichen Kapazitätsprotokolle, die zugehörigen Controller-stdout-/Schlusskapazitätslogs, acht Prozess-Receipts sowie `diagnostic-summary.json`. Es wurden keine Modelle ausgeführt, keine Checkpoint-, Trainingskorpus- oder Testdatensatzinhalte geöffnet und keine registrierten Dateien verändert. Neu berechnet wurden Datei-SHA256 der Logs, Receipts und Summary. Die Modell-, JAR-, Quell- und Eingabeidentitäten wurden anhand der protokollierten Bindungen verglichen; ihre Datei-Bytes wurden in diesem Audit nicht erneut gehasht.

Die untersuchten Originaldateien lagen unter `/workspace/scratch/3112c86b5191/training-recovery-study-v1/`. Die relativen Beleglinks unten verweisen auf die gleichnamigen `study-v1`-Archivpfade dieses Evidenzverzeichnisses.

| Identität | In den Belegen gebundener Wert |
| --- | --- |
| Ausführungs-Commit laut allen acht Receipts | `def5acf9cbba82ee51a53cc06fecc52e9cd0324d` |
| Bestehende Modell-/Laufzeitbasis laut Manifest | `30cd3a89810ed14fa84eca95236d3177e016118d` |
| Registrierung | `540f646e6963bcc9902a362f77d963fd39aa76513837125b86085768af8b8992` |
| JAR laut Receipts | `901b94e4c4d9b54c5ba422a5fa3b00bd9f28f187b68ab0b79f7f9b8fc6b62911` |
| `LmRecoveryDiagnostic.kt` laut allen vier Quellmanifesten | `4eb3eb594f03a30ad00b750a31ea899c8a937f0783473b71f70a576648f7f4e3` |
| Original-LM1 | `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd` |
| Tokenizer | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |
| Trainingskorpus | `27e67455e23dcc410855b2fd0f0292f9a00556d72d62aefb018e9eab4be034c9` |
| Entwicklungsdaten | `62da26a44f5c010c10e74b5edb8ac795160e3c62028292a2b9994a839a6fc111` |
| Tatsächlicher Tokenplan | `8e0d6ef53de6c84e48c74ecc4dbd4b70e25632caadc624f31ad0ac4aad681fee` |
| Indexplan | `346e93c510dafe5e12ee6a0e54ae94a0f0d099e7c0fc8c6f8973e5ff9a6b5066` |
| Neu berechnete Summary-SHA256 | `6039245783c17119dc26f74d0b953e4f89eedd3a8a07a3a3ccb625d5ba52bea6` |

Alle vier Quellmanifeste enthalten dieselben neun Pfad-/SHA-Paare. Alle acht Receipts binden dieselbe Registrierung und denselben JAR-Hash. Die in den Receipts gespeicherten stdout-SHA256 stimmen mit den tatsächlich gelesenen Bytes überein. [R-small], [R-wide], [R-nf], [R-rate], [S]

Bei `small-standard` enthält der Controller-stdout vor der internen `diagnostic.tsv` zusätzlich eine JVM-Warnung über eine bereits gesperrte `hsperfdata`-Datei (`[warning][perf,memops]`, `errno = 11`). Nach dieser einen Zeile sind die Inhalte identisch. Der Receipt-Hash bindet korrekt den vollständigen stdout einschließlich Warnung. Bei den anderen drei Armen sind interne Diagnose-TSV und Controller-stdout bytegleich. Dieser begrenzte Unterschied wurde berücksichtigt und nicht als identische Datei ausgegeben.

## 2. Vollständigkeit und Ausgangsgates

Jeder Arm besitzt genau die 16 fortlaufenden `TRAIN_STEP`-Einträge 1 bis 16 und einen abschließenden `TRAIN_RESULT`. Manifest und Ergebnis nennen übereinstimmend **3.255 Zieltoken**, davon laut Planmanifest **26 EOS-Targets** und damit 3.229 Inhaltstargets. Batchgröße 4 und 16 Schritte entsprechen 64 geplanten Sequenzen. EOS macht somit rund **0,799 % der Zielpositionen** aus; aus dieser Zählung allein folgt keine Übergewichtung von EOS.

Alle Arme melden Seed 20261104, zwei Worker, Warmup 2, Clip 1 und Weight Decay 0,01. Ein kleiner Arm enthält 132.160 Parameter, die drei breiten Arme jeweils 6.562.304. Die vier Messpunkte 0/1/4/16 sind vollständig vorhanden: jeweils vier `FIXED_DEV`-, vier `FIXED_FIRST_BATCH`- und 28 `EOS_PROBE`-Zeilen. Die Entwicklungsmenge bleibt bei 839 Inhaltstargets und zwölf terminalen EOS-Targets. Quelltext- und Inputtoken-Hashes sämtlicher Proben bleiben über Schritte und Arme gleich. Alle geprüften numerischen Werte sind endlich. [D-small], [D-wide], [D-nf], [D-rate]

Alle vier anfänglichen Kapazitätsbenchmarks bestehen mit drei beobachteten ersten Ausgabetoken und drei vollständigen 32-Token-Ausgaben. Ihr Ergebnis ist an den jeweiligen Schritt-0-Checkpoint gebunden. `INITIAL_GATES=true` liegt jeweils vor dem ersten Trainingsschritt. Die größten Anfangsabweichungen der breiten Modelle betragen 0,0000276566 für vollständige Logits, 0,00000190735 für Wahrscheinlichkeiten und 0,0000114441 für rekurrente Logits; sie liegen unter den registrierten Grenzen. [B-small], [B-wide], [B-nf], [B-rate]

Alle vier Trainingsprozesse enden mit Exit 0 ohne Timeout. Die Eingabe-/Quellunverändertheit wird sowohl vom Kotlin-Ergebnis als auch von den Controller-Receipts bestätigt. Alle vier Schlussbenchmarks enden mit Exit 1 ohne Timeout und enthalten einen regulären negativen Kapazitätsbefund. `COMPLETED` in der Summary bezeichnet daher einen abgeschlossenen Optimierungslauf, keinen angenommenen Modellkandidaten.

## 3. Feste Entwicklungsdaten und feste Trainings-Minibatch

BPB bezieht sich auf die festen Entwicklungs-Inhaltstoken; kleiner ist besser. Delta bedeutet Schritt 16 minus Schritt 0.

| Arm | BPB Schritt 0 | BPB Schritt 16 | Delta BPB | BPB-Teilbedingung >0,00001 Verbesserung | Gesamtkriterium |
| --- | ---: | ---: | ---: | --- | --- |
| [small-standard][D-small] | 2,352541415836 | 2,352506801295 | −0,000034614541 | erfüllt | nicht erfüllt: Kapazität |
| [wide-standard][D-wide] | 2,352541414134 | 2,436533181321 | +0,083991767187 | nicht erfüllt | nicht erfüllt |
| [wide-nf-scaled][D-nf] | 2,352541414134 | 2,436379135800 | +0,083837721666 | nicht erfüllt | nicht erfüllt |
| [wide-rate-scaled][D-rate] | 2,352541414134 | 2,351867622580 | −0,000673791554 | erfüllt | nicht erfüllt: Kapazität |

Die unabhängige Nachrechnung der registrierten Formel ergibt für **alle vier Arme `limitedContinuationSuccess=false`**, wie in der Summary. Die Änderung der Gesamtlernrate schützt die Entwicklungs-BPB in diesem Lauf deutlich besser als beide anderen breiten Rezepte. Daraus folgt noch keine unabhängige Generalisierung, weil diese Texte Entwicklungsdaten sind. [S]

Die folgenden NLL-Werte wurden jedes Mal auf **derselben ersten Minibatch mit 234 Zielpositionen** bestimmt. Sie sind natürliche Logarithmen pro Zieltoken, einschließlich vorhandener EOS-Targets.

| Arm | Schritt 0 | Schritt 1 | Schritt 4 | Schritt 16 | Abnahme 0→16 |
| --- | ---: | ---: | ---: | ---: | ---: |
| [small-standard][D-small] | 2,628634975 | 2,582362670 | 2,484243168 | 2,430477747 | 7,5384 % |
| [wide-standard][D-wide] | 2,628635108 | 2,342063966 | 1,962153723 | 1,917311160 | 27,0606 % |
| [wide-nf-scaled][D-nf] | 2,628635108 | 2,342165603 | 1,962514519 | 1,917971352 | 27,0355 % |
| [wide-rate-scaled][D-rate] | 2,628635108 | 2,589013111 | 2,506550890 | 2,461860056 | 6,3445 % |

Das belegt eine verbesserte Vorhersage dieser bereits trainierten Beispiele. Insbesondere lernen die beiden breiten Standard-/Normrezepte die erste Minibatch stärker, während ihre Entwicklungs-BPB um etwa 3,57 % beziehungsweise 3,56 % schlechter werden. Diese gemessene Trennung ist mit zu starker Anpassung an die Trainingsbeispiele vereinbar. Es wurde hier jedoch nicht der gesamte Trainingskorpus nach jedem Schritt ausgewertet. Die wechselnden `TRAIN_STEP.nll`-Werte wurden **nicht** als feste Lernkurve interpretiert.

## 4. EOS: Rangwechsel am Kapazitätsprompt, kein belegter globaler Kollaps

Die Margin ist `logit(EOS) − max(logit anderer gültiger Ausgabetoken)`, BOS ausgeschlossen. Eine positive Margin bedeutet hier EOS als eindeutiges Greedy-Argmax. Der unveränderte Kapazitätsprompt enthält 64 Inhaltstoken.

| Arm | p(EOS), Schritt 0→16 | Margin Schritt 0 | Schritt 1 | Schritt 4 | Schritt 16 |
| --- | --- | ---: | ---: | ---: | ---: |
| [small-standard][D-small] | 0,21975770 → 0,22654149 | −0,08270168 | −0,06776047 | −0,01108742 | +0,09713650 |
| [wide-standard][D-wide] | 0,21975711 → 0,21847048 | −0,08269882 | −0,00483513 | +0,21586323 | +0,54664420 |
| [wide-nf-scaled][D-nf] | 0,21975711 → 0,21834815 | −0,08269882 | −0,00483513 | +0,21630478 | +0,54675100 |
| [wide-rate-scaled][D-rate] | 0,21975711 → 0,22304790 | −0,08269882 | −0,07288265 | −0,04081631 | +0,02863216 |

Alle Schlussbenchmarks bestätigen diesen Rangwechsel durch **sechs von sechs gemessenen Aufrufen mit `stop=EOS` und `actualNewTokens=0`**: drei Ein-Token-Aufrufe und drei 32-Token-Aufrufe je Arm. Der Ablehnungsgrund lautet durchgehend `NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD`. Die kurze Laufzeit eines solchen Abbruchs erfüllt die Arbeitslast nicht. [C-small], [C-wide], [C-nf], [C-rate]

Bei `wide-standard` und `wide-nf-scaled` sinkt die absolute EOS-Wahrscheinlichkeit am Kapazitätsprompt sogar leicht. EOS wird dennoch das stärkste einzelne Token. Aus `p(best)=p(EOS)·exp(−Margin)` ergibt sich bei `wide-standard` eine Abnahme des stärksten anderen Tokens von ungefähr 0,23870 auf 0,12647. Der belegte Mechanismus ist deshalb ein **Rangwechsel zwischen Ausgabetoken**; ein Anstieg der gesamten Wahrscheinlichkeitsmasse auf EOS ist dafür nicht erforderlich. Welcher Anteil des internen Gewichtsupdates diesen Rangwechsel verursacht, wurde nicht getrennt attributiert.

### Nichtterminale Prefixpositionen

Die drei Prefixe umfassen tatsächlich 34, 35 und 37 Inhaltstoken, jeweils die erste Hälfte einer festen Entwicklungszeile. Ihre Hashes bleiben über alle Messpunkte und Arme identisch. Für alle **48 Prefixbeobachtungen** bleibt die EOS-Margin negativ; ihr größter Wert beträgt −6,374255. Die größte beobachtete EOS-Wahrscheinlichkeit ist 0,00021617622, also rund **0,02162 %**. EOS ist an keiner dieser geprüften Prefixpositionen Greedy-Argmax.

| Arm | Prefix 34: Margin Schritt 16 | Prefix 35 | Prefix 37 |
| --- | ---: | ---: | ---: |
| [small-standard][D-small] | −10,837129 | −6,649080 | −7,920490 |
| [wide-standard][D-wide] | −10,437732 | −6,817816 | −7,842470 |
| [wide-nf-scaled][D-nf] | −10,435022 | −6,815742 | −7,836368 |
| [wide-rate-scaled][D-rate] | −10,849810 | −6,680941 | −7,968427 |

Bei den drei vollständigen Entwicklungszeilen wird EOS am Ende von Schritt 16 in zwei von drei Fällen beim kleinen und beim Rate-Arm sowie in drei von drei Fällen bei den beiden anderen breiten Armen Argmax. Ein Stopp am tatsächlichen Segmentende kann korrekt sein. Die über alle zwölf vollständigen Entwicklungssegmente gemittelte EOS-NLL ändert sich zugleich von rund 1,576937 auf 1,566052 / 1,691314 / 1,691901 / 1,574825 in der obigen Armreihenfolge. Einzelergebnisse an drei Textenden sind folglich keine Aussage über jedes Segmentende.

Der registrierte Kapazitätsfehlschlag bleibt bestehen. Diese wenigen Kontextproben rechtfertigen weder „das Modell antwortet überall sofort mit EOS“ noch die gegenteilige Behauptung, beliebige andere Prompts seien funktionstüchtig.

## 5. Wurde der Norm-Update tatsächlich skaliert?

Für jeden der 16 Schritte wurde das Verhältnis `nfAppliedUpdateL2 / nfProposedUpdateL2` aus den Rohwerten berechnet:

| Arm | Kleinstes Verhältnis | Größtes Verhältnis |
| --- | ---: | ---: |
| small-standard | 1,0 | 1,0 |
| wide-standard | 1,0 | 1,0 |
| **wide-nf-scaled** | **0,124794617108** | **0,125046087609** |
| wide-rate-scaled | 1,0 | 1,0 |

Damit ist die tatsächlich angewandte Norm-Änderung im Normarm in den aggregierten Messwerten ungefähr ein Achtel des vorgeschlagenen Updates. Die größte relative Abweichung des gemessenen Normverhältnisses vom Soll 0,125 beträgt etwa 0,1644 %. Das ist mit der Float32-Rundung beim Rückschreiben kleiner Parameterdifferenzen vereinbar. Es liegt kein Logbeleg dafür vor, dass die Skalierung vergessen wurde. Einzelne Parameterkoordinaten wurden in diesem Rohlogaudit nicht neu aus Checkpoints rekonstruiert. [D-nf]

Trotz dieser Intervention liegen Entwicklungsergebnis, feste Trainings-Minibatch und Kapazitäts-Margin sehr nahe bei `wide-standard`. Die Entwicklungs-BPB sind gegenüber `wide-standard` zwar um 0,000154046 niedriger, bleiben jedoch um 0,083837722 schlechter als der eigene Ausgangszustand. **Diese konkrete Normskalierung behebt weder die Entwicklungsverschlechterung noch den EOS-Kapazitätsfehlschlag.** Eine Aussage, finale Normskalierung sei die alleinige bestätigte Ursache, wird durch diese Ergebnisse nicht gestützt.

Die Gesamtlernrate von einem Achtel beeinflusst alle Parameterupdates und bei AdamW auch den Decay-Schritt. Ihr besseres Entwicklungsergebnis ist ein Befund dieses gesamten Rezepts. Es isoliert weder die Norm allein noch einen einzelnen internen Tensor als Ursache. Der globale Rate-Arm erfüllt weiterhin die volle Generierungsbedingung nicht. [D-rate], [C-rate]

## 6. Schlussentscheidung und Grenzen

Die protokollierten Checkpoint-Identitäten stimmen jeweils zwischen Schritt-16-Speicherung, `TRAIN_RESULT`, Schlussbenchmark und Summary überein:

| Arm | Finaler Checkpoint-SHA256 laut Belegkette |
| --- | --- |
| small-standard | `262c55082b8b28c8424fd7fe02d9a1d3ab16961db7caace934cbd6318ef9bf82` |
| wide-standard | `70ba0c38062d5ed879acaa4b8e130315724887aa02d8e6a9412c6c4b4e53a225` |
| wide-nf-scaled | `182b0e20bf74f1e8ea45caacde88b94d472cc7d03d77635f3779afd4f17816d7` |
| wide-rate-scaled | `66a322c9f8784a8ca9f5226bee12a9096af49cd229eb0d2253826c4b5da6b4c2` |

Die Summary wurde zeilengetreu gegen die festen Entwicklungswerte, erste Minibatch, sämtliche EOS-Proben und Schlusskapazitäten abgeglichen. Ihre vier negativen Erfolgsentscheidungen und `independentHeldOutGeneralizationClaim=false` sind richtig. [S]

Zulässig sind die Aussagen: echte, vollständige Optimierungsläufe; verbesserte Vorhersage der festen ersten Trainings-Minibatch; eine kleine Entwicklungsverbesserung beim kleinen und beim reduzierten Gesamtlernrate-Arm; korrekt angewandte Normskalierung; vier fortbestehende EOS-Kapazitätsfehlschläge.

Nicht belegt sind eine reparierte Generierung, ein erfolgreicher Fortsetzungskandidat, allgemeine Intelligenz oder Anweisungsbefolgung, nützlicher Wissenserwerb jedes geänderten Parameters, unabhängige Test-Generalisation oder Android-Leistung. Die zusätzlichen geänderten Gewichte können außerdem Decay-Anteile enthalten; ihre bloße Anzahl ist kein Wissensmaß. Dieser Bericht bewertet die parallel laufende Intent-Studie nicht.

## 7. Rohlog-SHA256 und kompakte Nachrechnung

Die folgenden Werte wurden in diesem Audit aus den Original-Logbytes neu berechnet:

| Arm | Interne `diagnostic.tsv` | Schlusskapazitäts-TSV |
| --- | --- | --- |
| small-standard | `1c3b25d1b49e7cfbf4c643d146474459900632937739026caa3e375d01014134` | `5af4bff52196f12c9aa81cc09b5f8897125afebae8cfd417d2a01477af117a1c` |
| wide-standard | `0ab9e9dae50de6375c93218b238fcbb505604ffe8e640ce8c2e7c35f9753abc6` | `8a260b07ae24d05877bb238584a5d00a5274924c68b69bd67b2aa73cb9709eb7` |
| wide-nf-scaled | `d05a326d6669ee18556c93b1148ac276a2464bf2c4b135f41066e6aa15a48da4` | `335fa007fc3bf265b6a2359b18c7a367c55cbcc26af3fc44748174ebea253acb` |
| wide-rate-scaled | `46d26ca3edf2735c607319f01a6fe1ffba6d984c161cbd24e2482a62a1f0230b` | `c96532a1e3ca1bfd43e9bd2a4687ac2a208005d5747c969fe7d49a0b0740fc2e` |

Die folgende kompakte Nachrechnung liest nur archivierte Diagnosebelege. Sie startet keinen Modellprozess und verändert keine Dateien. Ausführen aus dem Repositoryroot:

```bash
python - <<'PY'
from pathlib import Path
import hashlib, json

root = Path('docs/evidence/lm1-training-recovery-20261104/study-v1')
summary = json.loads((root / 'diagnostic-summary.json').read_text())

def records(path, kind):
    result = []
    for line in path.read_text().splitlines():
        fields = line.split('\t')
        if fields[0] != kind:
            continue
        row = {}
        for field in fields[1:]:
            key, value = field.split('=', 1)
            assert key not in row
            row[key] = value
        result.append(row)
    return result

for name in ('small-standard', 'wide-standard', 'wide-nf-scaled', 'wide-rate-scaled'):
    label = 'diagnostic-' + name
    log = root / label / 'diagnostic.tsv'
    for suffix in ('', '-capacity-after'):
        output = root / 'logs' / (label + suffix + '.tsv')
        receipt = json.loads((root / 'logs' / (label + suffix + '.receipt.json')).read_text())
        assert hashlib.sha256(output.read_bytes()).hexdigest() == receipt['logSha256']
        assert receipt['returncode'] == (1 if suffix else 0)
        assert receipt['timeout'] is False
    steps = records(log, 'TRAIN_STEP')
    assert [int(row['step']) for row in steps] == list(range(1, 17))
    result, = records(log, 'TRAIN_RESULT')
    assert result['tokens'] == '3255' and result['steps'] == '16'
    dev = records(log, 'FIXED_DEV')
    fixed = records(log, 'FIXED_FIRST_BATCH')
    probes = records(log, 'EOS_PROBE')
    cap, = records(root / 'logs' / (label + '-capacity-after.tsv'), 'CAPACITY_RESULT')
    assert cap['checkpointSha256'] == result['checkpointSha256']
    assert [row['step'] for row in dev] == ['0', '1', '4', '16']
    assert len(probes) == 28
    assert all(float(row['eosMargin']) < 0 for row in probes if row['position'] == 'prefix')
    view = summary['variants'][name]
    assert view['before'] == dev[0] and view['after'] == dev[-1]
    assert view['fixedFirstBatch'] == fixed and view['eosProbes'] == probes and view['capacity'] == cap
    success = cap['pass'] == 'true' and float(dev[-1]['contentBpb']) < float(dev[0]['contentBpb']) - 0.00001
    assert view['limitedContinuationSuccess'] is success and not success
    ratios = [float(row['nfAppliedUpdateL2']) / float(row['nfProposedUpdateL2']) for row in steps]
    print(name, 'BPB delta', float(dev[-1]['contentBpb']) - float(dev[0]['contentBpb']),
          'fixed batch', fixed[0]['nll'], '->', fixed[-1]['nll'],
          'nf ratio range', min(ratios), max(ratios), 'success', success)
PY
```

[S]: study-v1/diagnostic-summary.json
[D-small]: study-v1/diagnostic-small-standard/diagnostic.tsv
[D-wide]: study-v1/diagnostic-wide-standard/diagnostic.tsv
[D-nf]: study-v1/diagnostic-wide-nf-scaled/diagnostic.tsv
[D-rate]: study-v1/diagnostic-wide-rate-scaled/diagnostic.tsv
[B-small]: study-v1/diagnostic-small-standard/capacity-before.tsv
[B-wide]: study-v1/diagnostic-wide-standard/capacity-before.tsv
[B-nf]: study-v1/diagnostic-wide-nf-scaled/capacity-before.tsv
[B-rate]: study-v1/diagnostic-wide-rate-scaled/capacity-before.tsv
[C-small]: study-v1/logs/diagnostic-small-standard-capacity-after.tsv
[C-wide]: study-v1/logs/diagnostic-wide-standard-capacity-after.tsv
[C-nf]: study-v1/logs/diagnostic-wide-nf-scaled-capacity-after.tsv
[C-rate]: study-v1/logs/diagnostic-wide-rate-scaled-capacity-after.tsv
[R-small]: study-v1/logs/diagnostic-small-standard.receipt.json
[R-wide]: study-v1/logs/diagnostic-wide-standard.receipt.json
[R-nf]: study-v1/logs/diagnostic-wide-nf-scaled.receipt.json
[R-rate]: study-v1/logs/diagnostic-wide-rate-scaled.receipt.json
