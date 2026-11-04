# Publikationsprüfung des festgelegten Stands 59891

**Ergebnis: `PASS_TEXT_CONSISTENCY_ONLY`, 514/514 Konsistenzprüfungen, Exit 0.**
Geprüft ist ausschließlich PR-508-Commit
`59891cfad6dd015f47c187cc688ea94b0026ad7d`. Der Auditor liest veröffentlichte
Textbelege und ausdrücklich erlaubte Git-Quellblobs. Er führt keinen Modell-
oder Trainingsprozess aus und verändert keine Forschungsdatei.

Die vollständigen Messwerte, Quellpfade, Git-Blob-IDs, SHA-256-Werte und
ausgelassenen Prüfeinträge stehen in [audit-result.json](audit-result.json).
[audit_publication.py](audit_publication.py) erzeugt diesen Bericht deterministisch.

## Unabhängig nachgerechnet

| Gegenstand | Ergebnis und Reichweite |
|---|---|
| Drei Runner, Selector, drei Registrierungen | **7/7 bytegleich** zwischen `9ceffc26a2159c8dc726c1f13d2882119159c130` und `59891…`; identische Git-Blob-IDs |
| Veröffentlichte Quellhashes | **65/65 Einträge**, entsprechend **29 verschiedenen Quellpfaden**, gegen aktuelle Bytes und den jeweils angegebenen Ausführungscommit geprüft |
| Auswahl, Freeze, Vorabquittung | **3/3 bytegleich** zum früheren Publikationscommit; sämtliche gegenseitigen Hash- und Identitätsverweise konsistent |
| Neue Rohlogs und Prozessquittungen | **6/6 Loghashes** aus den tatsächlich veröffentlichten Textbytes nachgerechnet; vier Finaltestarme und zwei spätere TRAIN-fit-Auswertungen korrekt zugeordnet |
| Git-Publikationsmetadaten | Commitinhalt, Baum, Eltern, Nachricht, Zeitangaben und fehlender Signaturheader stimmen mit dem rohen Git-Commitobjekt überein |
| Verweise | Lokale RESULTS-Verweise existieren im festgelegten Gitbaum; Die Zielinhalte werden dafür nicht geöffnet. |

`59891…` hat unmittelbar `9ceffc…` als Elterncommit. Der Unterschied besteht
aus Evidenzdateien und dem Handoff. Die Ausführungsreferenzen sind V1
`def5acf9cbba82ee51a53cc06fecc52e9cd0324d`, V2
`fc2eb797210bee25091caf734aacbfd694a3372e` und V3
`be4e49600fed1db4c697230bb8e42c150dbfbdaf`. Auch die drei aktuellen
Registrierungen entsprechen den Bytes ihres jeweiligen Ausführungscommits.

### Auswahl und eingefrorener Stand

Der veröffentlichte Kandidatenvergleich wählt V3: Wide erzielt dort 16/24
statt 13/24 DEV-Treffer. Der Auditor rechnet die veröffentlichte Rangfolge
aus diesen Angaben nach; die Auswertung sämtlicher DEV-Rohscores gehört zum
separaten Ergebnisaudit. `selectedFreezePayload`, der gewählte
Kandidateneintrag und die tatsächliche Freeze-Datei stimmen vollständig
überein. Gemeinsame Testidentität sowie Beispiel- und Tokenpläne passen
zusammen. Die finalen und TRAIN-fit-Summaries binden denselben Freeze.

| Datei unter `docs/evidence/lm1-training-recovery-20261104/final-selection/` | Tatsächlich nachgerechneter SHA-256 |
|---|---|
| `development-study-choice.json` | `1325d06208d8850e6ce3aa5969d68f37cdaa3e26c46c50f66743ddd3dc442cac` |
| `selection-freeze.json` | `6c069dd1ab21d792b9985333b14b2d6a0c51b66affbc8a90de0f37092c5a49f4` |
| `before-test-verification.json` | `b12fdea44b7a3531be407f542d66c104406e7d51038956a048403b57d18136c6` |

Die vier Finalquittungen nennen die eingefrorenen S0/W0/S1/W1-Pfade. Ihre
`TEST_INPUT`-Zeilen nennen die passenden Checkpoint- und Testhashes. Die beiden
zusätzlichen Quittungen nennen weiterhin dieselben S1/W1-Checkpoints, aber
`intent-data/train.tsv` und dessen registrierten Hash. Der wiederverwendete
Scorer heißt auch dort `test`; die tatsächlichen aufgezeichneten
Eingabeargumente und der TRAIN-fit-Bericht unterscheiden die Datenrollen.

Alle sechs Quittungen melden denselben V3-Quellcommit, die V3-Registrierung,
den registrierten JAR-Hash, `-Xmx3g`, das 900-Sekunden-Limit, Rückgabecode 0,
keinen Timeout und unveränderte registrierte Bytes. Das sind überprüfbare
Übereinstimmungen zwischen Textbelegen. Sie belegen für sich allein weder
den tatsächlichen Prozessstart noch die geladenen Binärbytes oder den
gemessenen Speicherverbrauch.

## Publikationszeit: was die Quellen tragen

`publication-metadata.json` gibt für `9ceffc…` **19:31:51 UTC** an. Das rohe
Gitobjekt enthält denselben Zeitpunkt. Die erste Finalquittung nennt
**19:31:59,698108 UTC**; der rechnerische Abstand beträgt **8,698108 Sekunden**.
Auch die aufgezeichnete Folge S0 → W0 → S1 → W1 → TRAIN-fit S1 → TRAIN-fit W1
ist zeitlich konsistent.

Der Commit ist unsigniert. Autor- und Committerzeit sind Git-Inhalt und kein
unabhängiger Server-Push-Zeitstempel. Erfolgreiche Branch-Aktualisierung,
bytegleicher Remote-Abruf und anschließender Prozessstart stehen als
Beobachtungsbericht des Koordinators im JSON. Dieser Auditor besitzt dafür
keine unabhängige Host- oder Zeitattestation. Er bestätigt außerdem, dass der
frühere Gitbaum keine finalen `test-*`-Logs enthält; daraus folgt keine
Aussage über damals uncommittete Dateien oder Prozesse außerhalb dieses
Gitbaums. Die Angaben in RESULTS und im veröffentlichten Finalaudit benennen
diese Beleggrenze ebenfalls.

## Reichweite der gemeldeten 98 Integritätsprüfungen

`publication-integrity.json` enthält 95 SHA-Angaben und drei weitere
Prüfeinträge. Alle gemeldeten Hashwerte sind intern konsistent und entsprechen
den jeweiligen Registrierungsangaben. Davon werden hier **65 Quelleinträge
tatsächlich nachgehasht**. Die übrigen **30** werden nur zwischen Textbelegen
verglichen:

- 3 Einträge zum Datengenerator;
- 18 Einträge zu Datensätzen, Datensatzmanifest, Entwicklungstext oder Korpus;
- 9 Einträge zu Checkpoint, Tokenizer oder Studien-JAR.

Der tatsächlich veröffentlichte SHA-256 des unabhängigen Reviews stimmt
ebenfalls. Der gemeldete Hash der vorherigen Originalfassung und die Aussage,
es seien ausschließlich Links angepasst worden, werden mangels dieser
Originalfassung nicht eigenständig rekonstruiert. Die leere Fehlerliste des
separaten 622-Prüfungen-Finalaudits passt zum Publikationsbericht; seine
numerischen Fallprüfungen werden hier nicht als eigene Prüfung mitgezählt.

Die 514 lokalen Prüfungen enthalten wiederholte Identitäts- und
Querverweisprüfungen. Sie sind keine 514 unabhängigen Modelltests.

## Archivbeleg und Binärgrenze

`archive-manifest.json` nennt **nur den Dateinamen**
`LM1_Training_Research_20261104.zip`. Es enthält keinen absoluten Pfad, keine
Artefakt-URI, keine Library-ID und keine konkreten Mitgliedsnamen.

| Feld | Veröffentlichte Angabe; hier keine Binärmessung |
|---|---|
| Größe | 51.103.436 Bytes |
| ZIP-SHA-256 | `428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016` |
| `manifestSha256` | `23783270c359e7c0c50ff5ed74f2dc2ef076a0fd30341a2be0c2c10f75c550b6` |
| `membersVerified` | 10 |
| Ausführungsquelle | `be4e49600fed1db4c697230bb8e42c150dbfbdaf` |
| Freeze-Publikation | `9ceffc26a2159c8dc726c1f13d2882119159c130` |

Der tatsächlich geprüfte SHA-256 der **Git-Textdatei** `archive-manifest.json`
ist `3f3a215505905610fe631799966c40049efc5aaf7d14e2f5c1b24ec9dca56a94`.
Er ist von dem darin angegebenen `manifestSha256` zu unterscheiden. RESULTS
beschreibt vier Gewichtsdateien, drei Studien-JARs, eine Standardbibliothek,
README und Manifest. REPRODUCE nennt `v1-experiment.jar`, `v2-experiment.jar`,
`v3-experiment.jar` und `kotlin-stdlib-2.0.20.jar`. ZIP- und Mitgliedsbytes
werden von diesem Auditor nicht geöffnet. Eine gesonderte Binärprüfung des
Koordinators bleibt ein eigenständiger Nachweis.

## Abgrenzung zur bereits eingefrorenen Reparatur

Die Runner-/Selector-/Registrierungsbytes sind weiterhin die Originale von
`9ceffc…`. Im historischen Freeze steht für S1/W1 weiterhin
`REQUIRES_SEPARATE_BENCHMARK`; die Summary besitzt kein `testAuthorization`.
Die neuen Veröffentlichungsbelege integrieren daher keine der separat
validierten, prospektiven Gate-Korrekturen. Deren Patch, Fixtures, Tests und
vorherige Reports bleiben durch diese Prüfung unverändert. Die vorliegenden
Finalergebnisse wurden für keinerlei neue Trainings- oder Auswahlregel
verwendet.

## Reproduzieren

Im Repositorywurzelverzeichnis:

```bash
python experimental/weight-space-20261104/continuation-review/source-audit/final-publication-59891/audit_publication.py --repo . |
  cmp - experimental/weight-space-20261104/continuation-review/source-audit/final-publication-59891/audit-result.json
```

`cmp` endet bei identischem Bericht mit Exit 0. Der Auditor schreibt nur auf
stdout. Er verwendet feste Commit-IDs und eine ausdrückliche Textdateiliste;
Git-Netzwerktransporte und Lazy-Fetch sind gesperrt. Fehlende lokale Objekte
führen zum Abbruch statt zu einem stillen Nachladen. Die genannten fünf
Commitobjekte und erlaubten Textblobs müssen deshalb lokal vorhanden sein.

| Artefakt | SHA-256 |
|---|---|
| `audit_publication.py` | `697c3c431218937348aafd8fe2403224af08ac586842b38876d91636a76cd95e` |
| `audit-result.json` | `9d0a91db9482d5b4dc153c35af399dd82f95da122046e9b619002efe0803b9ec` |
