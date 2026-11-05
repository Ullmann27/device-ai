# G1-INTEGRATE-WEIGHT-002: Nachtforschung und sichere Fortsetzung

Eigener Draft-PR: https://github.com/Ullmann27/device-ai/pull/492

Branch: `research/continuum-weight-space-audit-20261104`.
Beginn dieser Runde: `0af14f75b04e68ba849a92316b8be51041e8738b`.
Zuletzt geprüfter fremder PR #508:
`59891cfad6dd015f47c187cc688ea94b0026ad7d`, weiterhin Draft/unmerged.
Operativer Zustand: `STATE.json` am aktuellen eigenen Branch-Head.

## Verbindlicher Zustand

**Fünf belegte Meilensteine; sechster Modellversuch am vorab festgelegten
Paritätsgate verworfen und exakt zurückgesetzt. Akzeptierter Stand: M5.**

Keine weiteren Modellläufe in dieser Runde, keine Trainingsfortsetzung,
keine Lockerung eines Gates und keine Wiederholung mit kleinerer Amplitude.
Das offene Forschungsproblem darf durch Quellenanalyse und Gate-Arbeit
weiterbearbeitet werden. Eine neue Messrunde benötigt ein eigenständiges,
vor der Messung veröffentlichtes Protokoll mit unabhängig geprüftem Gate.
Das stellt keine automatische Trainingsfreigabe dar.

Erlaubte Änderungen: `experimental/weight-space-20261104/` und zugehörige
Tests. Produktive Modellruntime, bestehende Checkpoints, fremde aktive Runner,
Hauptbranch, Release/APK und Promotion bleiben außerhalb dieses Auftrags.
Keine kostenpflichtigen Trainingsdienste, kein GitHub-Actions-Lauf,
keine Kommentare oder Nachrichten an Dritte. Nur der eigene Draft-PR
wird über erwarteten Head-SHA und Fast-forward aktualisiert, mit `[skip ci]`.
Die jüngste Nutzerpräferenz verlangt sequenzielles Arbeiten ohne neue Helferbots.

## Tatsächlich erreichte Schritte

| Schritt | Belegter Fortschritt | Belege |
|---|---|---|
| M1 | Echte W1-Replikate unterscheiden sich; exaktes Zurückfalten ist nicht durch die frühere Replikationsidentität begründet | `M1-REPLICA-RESULTS.md`, `replica-audit.json` |
| M2 | 240 Verteilungen, 1.440 Aktivierungsspuren und 48 native Ausgaben; kontextabhängiger EOS-Stopp und 16 private Blockinterventionen | `M2-M4-RESULTS.md`, `inference-evidence/` |
| M3 | Alle 240 Vorwärtsrechnungen separat in NumPy nachvollzogen; größte Wahrscheinlichkeitsabweichung 1,728535e-6 | `numerical-verification.json` |
| M4 | 16 quellengebundene Wiederaufnahmefälle ohne JVM-Start bestanden | `resume-verification.json` |
| M5 | Alle 6.562.304 W0-Koordinaten erhalten auf vier Proben Gradienten; D0 hat 9.712.128 Parameter, davon zunächst 7.873.536 mit Gradient; 20 Differenzenprüfungen und acht weitere Wiederaufnahmefälle bestanden | `M5-GRADIENT-RESULTS.md`, `gradient-audit.json`, `gradient-resume-verification.json` |
| M6, verworfen | Feste Pfadöffnung scheitert bei Probe 3: 1,311302185e-5 statt höchstens 1e-5; exakte Wiederherstellung, null Rückwärtsläufe, drei gesperrte Wiederaufnahmefälle | `M6-PARITY-REJECTION.md`, `open-path-failure-verification.json` |

Die einzelnen Testserien hatten 20, sieben und vier neue Testmethoden.
Diese Zahlen sind historische Ausführungen dieser Runde, kein behaupteter
gemeinsamer erneuter Gesamtlauf. Die numerisch unabhängige Implementierung
und Nachprüfer stammen aus derselben Root-Sitzung, nicht von neuen Bots.

Kein Optimizerupdate und kein neuer serialisierter Modellcheckpoint in
dieser Runde. Die alten Qualitätsbefunde bleiben bestehen: S1 31/48, W1 25/48,
beide allgemeine Kapazitätsabnahme FAIL. Der alte Finaltest ist verbraucht.
Längere Ausgabe, Gradienten ungleich null und zusätzliche Parameter sind
keine neue Qualitätsabnahme.

## Quellen und Wiederherstellung

| Messung | Vor Ausführung eingefrorener Commit | Registrierung SHA-256 |
|---|---|---|
| M2–M4 | `3397fc445b5c40dee987399bd38d8c1226bd5b87` | `9dd0171df1e8bf1adb0ed64cc3fa8bea1436d78773003341d75aa896492dc70e` |
| M5 | `c966de3b9c0e12f0f3eb469d964c79ba41ba0138` | `3ee5dc40b67fad6c86c0c0b8487740834526530637e5ee9a9f992691d26f4f88` |
| M6 | `8e0dadceef6a39cac86817b52340b79e185b160f` | `0b9b97874c42da9ee1f0c4f4f113790f6c50e960612a961dcac579b42d8daa78` |

Die Registrierungen binden tatsächliche Quellen, Eingaben, Build und Java.
Build-Receipts enthalten Compilerdateien, Hashes und vollständige Befehle.
Die drei tatsächlich verwendeten Diagnose-JARs sind im separaten Archiv
`LM1_Night_Diagnostic_Runtimes_20261104.zip` gespeichert. Der vollständige
Manifest- und Archivnachweis steht in `diagnostic-runtime-archive.json`.

Originales Modellarchiv: `LM1_Training_Research_20261104.zip`, 51.103.436 Bytes,
SHA-256 `428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016`.
Es enthält S0/S1/W0/W1, die ursprünglichen Laufzeit-JARs und stdlib.
Private Abrufidentitäten werden nur im privaten Fortsetzungsauftrag geführt.

Tokenizer-Rekonstruktion aus dem offiziellen Repository:
Commit `d2304a7658e1256b160c8012e1bac28268ff64a5`, Datei
`tokenizer/tokenizer-v512-94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f.lmbp`.
Vor Benutzung den gleichnamigen SHA-256 überprüfen.

Die in Receipts enthaltenen lokalen Pfade sind Ausführungsbelege. Ihr
Fortbestehen in einer späteren Sitzung darf nicht vorausgesetzt werden.
Fehlende Umgebung oder Eingaben bedeuten BLOCKED/NOT_RUN, niemals erfundene
Messwerte. Verfügbare Quellenarbeit kann unabhängig davon fortgesetzt werden.

## Nächste sinnvolle Arbeit ohne Modellstart

1. Aktuellen eigenen PR-Head, `AGENTS.md`, dieses Dokument, `STATE.json` und
   den aktuellen #508-Head lesen. Aktive Lease respektieren; bei fremdem
   Head-Update nicht überschreiben. Historische Messungen bleiben an ihren
   ursprünglichen Quellstand gebunden.
2. Den verworfenen Pfadöffnungsversuch anhand der Formeln und vorhandenen
   Belege analysieren. Der dritte Eingabefall ist eine gemessene Grenze;
   daraus keine allgemein optimale Amplitude oder maximale Parameterzahl ableiten.
3. Die bereits vorhandene kombinierte Kapazitäts-/Kandidatenwahl-Reparatur
   `../continuation-review/source-audit/pr508-capacity-and-choice-gates.patch`
   gegen den aktuellen fremden Stand prüfen. Sie ist bisher nicht in #508
   integriert. Änderungen nur als isolierte eigene Reparatur und Tests im
   erlaubten Bereich vorbereiten; fremde aktive Trainingspfade nicht editieren.
4. Ein prospektives Retentions-/Lernratendesign ausarbeiten: Die relative
   `nf`-Gradientenskala ist bei W0 ungefähr achtfach. Das ist eine Hypothese
   für die Versuchsgestaltung, weder festgestellte alleinige EOS-Ursache noch
   bereits ausgewählte Lernrate. Neue Datenaufteilung, Gate-Herkunft,
   Ressourcen, Abbruch und Auswahl müssen vor einer künftigen Messung stehen.
5. Jede neue Reparatur an echten Quellen unabhängig gegen den ursprünglichen
   Fehler und fehlerhafte Wiederaufnahme prüfen. Keine wiederholten fertigen
   Messungen nur zum Erhöhen der Fortschrittszahl. Ergebnisse und Sperren
   zeitnah als nachvollziehbare Commits sichern.

Die ursprünglich unveränderten Qualitäts- und Ressourcenanforderungen gelten
weiter. Rückwärtsdiagnose-Budgets sind zusätzliche Messbudgets und ändern
keine Android-Inferenzanforderung. Ungeprüfte Kandidaten bleiben ungeprüft.

## Fortsetzungsplanung

Der tatsächliche Planungsstatus steht in `STATE.json`. Eine eingetragene
Planung belegt noch keine spätere Ausführung. Fortsetzungen sollen mehrere
konkrete Quellen-/Gate-Schritte erreichen und nur belegte Meilensteine oder
einen konkreten Blocker knapp auf Deutsch melden. Keine Behauptung einer
ununterbrochenen Hintergrundausführung.

## Fortsetzung vom 05.11.2026, Quellenprüfung ohne Modellstart

Eigenständiger geprüfter Publikationscommit: [`e6dcac515d8670fdc5d3653fa1f919e1a03424c2`](https://github.com/Ullmann27/device-ai/commit/e6dcac515d8670fdc5d3653fa1f919e1a03424c2). Neue Dateien: [M6-Postmortem, aktueller #508-Gate-Abgleich und prospektives Retentionsdesign](CONTINUATION-01-SOURCE-REVIEW.md) sowie [maschinenlesbarer Quellen-/Diff-Hunk-Prüfbericht](continuation-01-source-audit.json). Aktueller tatsächlich gelesener #508-Kopf: `59891cfad6dd015f47c187cc688ea94b0026ad7d` (Draft). Der ursprüngliche Quellencheckout unter `/workspace/scratch/0549bca61ccf/device-ai-weight-audit` ist in dieser Ausführungsumgebung **nicht vorhanden**. Aus dem GitHub-Connector wurden dennoch die originalen drei Runner, M6-Rohlog, Kotlinquelle, Registrierung und bereits gesicherte Audits eingelesen.

**Neu quellengebunden geprüft:** 12 ursprüngliche Paritätsprompts PASS, drei registrierte M6-Öffnungs-Prompts mit dritter Verletzung, exakter Beginn-/Ende-Parameterdigest und 0 Gradienten-/Lossprotokolle. Die dritten `1.3113021850585938e-5` überschreiten `1e-5` um **31,1302185058594 %** des Grenzwerts. Keine Messung der verbliebenen neun Fälle, keine Rückwärtsrechnung, kein Optimizerupdate. Es wurde auch kein Kontrafaktum mit anderen Gewichten gestartet. Am #508-HEAD sind alle **16/16** Original-Kontexthunks des vorhandenen kombinierten Kapazitäts-/Wahl-Patches über alle **3/3** Runner ohne Fuzz passend; die drei Originale selbst sind weiterhin **ungepatcht**. Dies ist kein ausgeführter Reparaturtest. Ältere 165/165 und 37/37 synthetische Testfälle bleiben als historischer Nachweis auf ihrem Quellstand erhalten und wurden nicht erneut gezählt.

**Nicht durchgeführt:** Checkpoint-/Archiv-SHA-256-Neuverifizierung, dynamische Gegenproben für beschädigte Checkpoints und unerlaubte Wiederaufnahme, Java/Python-Runnerausführung, Training, APK/Fold7/Finaltest. Status für diese Prüfungen: **NOT_RUN**, mangels Checkout/Inputs; kein Ersatz durch Scheintests. Alte M5/M6-Registrierungen, Checkpoints und Ergebnisse bleiben unangetastet. Neue Studienparameter, LR-Multiplikator, Retentionsentscheidung oder Qualitätspromotion wurden **nicht** festgelegt. Die Quellenstudie skizziert die nötige neue, vorab veröffentlichte Datenaufteilung und Stop-/Freigabeverträge. Die bekannte etwa achtfache relative `nf`-Gradientenskala ist eine Messgröße, keine alleine bewiesene EOS-Ursache.

**Nächste Runde:** Falls eine zulässige originale Ausführungsumgebung wieder verfügbar ist, die existierenden synthetischen RED→GREEN-Gateproben einschließlich Fehl-Baseline, kaputtem Checkpoint, fehlender oder umetikettierter Receipt und fremdem Resume **ohne Modellstarts** neu gegen den verifizierten Patch ausführen; künftige Reparatur nur isoliert im eigenen erlaubten Bereich. Sonst weiter quellengebundenen Plan präzisieren. Der Patch ist nicht in #508 übernommen; keine fremden Runner ändern, keine historischen Receipts neu schreiben. `STATE.json` dokumentiert die freigegebene Lease, die offene Sperre und dieses Commit.


## Fortsetzung 02 — unabhängig geprüfte Grenze des Finaltest-Verbrauchs

**Neu publizierter Quell- und synthetischer Prototypcommit:** [`7e6bda9b8180c98dd011cbfde590047424cccd60`](https://github.com/Ullmann27/device-ai/commit/7e6bda9b8180c98dd011cbfde590047424cccd60), inklusive [Dokumentation](continuation-02/README.md), [maschinenlesbarem Audit](continuation-02/source-and-synthetic-audit.json), rein lesendem [Cross-Study-Prototyp](continuation-02/pretest_exclusivity.py) und [12 synthetischen Unittests](continuation-02/test_pretest_exclusivity.py). Der gebundene fremde #508-HEAD war bei der Prüfung weiter `59891cfad6dd015f47c187cc688ea94b0026ad7d`. Keine aktive Lease beim Erstellen oder Publizieren; dieses Übergabedokument hält sie weiterhin **frei**.

**Abgesicherte neue Erkenntnis:** Der exakte kombinierte Patch bleibt auf drei #508-Originalquellen mit **16/16 Hunk-Kontexten passend**. Drei gezielte Mutationen an alten Hunk-Ankern verhindern jeweils einen exakten Match; dies sind *quelltextbasierte Mutationsprüfungen*, keine dynamischen Runner-Regressionen. Der Patch schützt v2/v3 gegen **lokale** vorhandene `self.logs/test-*`-Einträge, prüft aber nicht aktiv den Finaltest-Verbrauch in anderen Studien-Ausgabewurzeln. Das boolesche `allFinalTestLogsAbsent` in der gepinnten Wahlprüfung ist kein unabhängiger Beweis, dass nirgendwo ein Finaltest schon verwendet wurde. Der bekannte alte Test in #508 ist verbraucht.

**Neuer isolierter Research-Prototyp:** Pinned Plan, zwingend alle drei Studienwurzeln, Ablehnung vorhandener Testlogs, Receipts, Summary/Startmarker, fehlender/aliasierter Wurzeln und veränderter JSON-/SHA-Identität; rein lesend, keine tatsächlichen Quellenrunner verändert. Lokal **12/12 Python-3.13.5-Unittests PASS**, **elf negative, eine saubere Positivkontrolle**. Die auf GitHub gespeicherten Skripte haben dieselben Git-Blob-SHAs wie die lokal getesteten Bytes: `5b068596a0b2301f373fe89421189654db15c963` und `aa85775476cbb70e9d3e5852db860575f76cc932`. Die SHA-256-Einträge stehen im Audit.

**Sperren:** Der Prototyp ist weder in den bestehenden kombinierten Patch noch in #508 integriert. Der alte Finaltest darf nicht erneut ausgeführt werden. Eine lokale Zustandsprüfung kann weder eine externe Zeit-/Publikationsattestation noch eine globale atomare Verbrauchssperre ersetzen. Die vorhandenen historischen **165/165** und **37/37** wurden nicht erneut ausgeführt. **Originalrunner RED→GREEN, manipulierte reale Checkpoints, Baseline und vollständige Resume-Prüfung: NOT_RUN**, weil Originalcheckout/Inputs in dieser Umgebung fehlen. Bereits vorhandene Quellen durften über den GitHub-Connector gelesen werden, nicht über Container-DNS. Java-Installation allein ist keine freigegebene Modellumgebung. **M5_UNCHANGED; M6_PARITY_FAIL_RESTORED; null Modellstarts/Optimizerupdates.** Keine LR-Auswahl, Training, Promotion, APK oder Änderung fremder Branches.

**Nächste zulässige Arbeit:** Vor künftigem Modellversuch mit neuem autorisierten Checkout einen source-pinned RED→GREEN-Test für Cross-Study-Finalverbrauch und falsche Wiederaufnahme am *wirklich gepatchten* Runner ergänzen, inklusive manipulierter Baseline, fehlender Kapazitätsreceipt und kaputtem Checkpoint. Danach erst eine zuvor veröffentlichte, extern nachvollziehbare exklusive Finaltestfreigabe designen. Die neue, unabhängige Datenteilung und Retentions-/LR-Kandidaten müssen weiterhin vor jeder neuen Messung prospektiv registriert werden. Keine automatische Freigabe.

## Fortsetzung 03 — sechs RED→GREEN-Sicherheitsgegenfälle mit Quellenpin (05.11.2026)

**Publizierter unabhängiger Stand:** [Commit `0e7afb37ba3ac73aa074f6c02dd242e5c7826232`](https://github.com/Ullmann27/device-ai/commit/0e7afb37ba3ac73aa074f6c02dd242e5c7826232), nur unter [`overnight-research/continuation-03/`](continuation-03/README.md); vorheriger eigener Head `07516a8bcebeeaf9af6a4453d0182cdd88debebd`, #508 unverändert `59891cfad6dd015f47c187cc688ea94b0026ad7d`.

Neuer **wirklich ausgeführter** Python-3.13.5-Test: **19/19 PASS** am lokal synthetischen Inventar, davon **6 bisherige RED**-Fälle am exakt gepinnten Vorversions-Guard und **6 GREEN** am isolierten, gehärteten Nachfolger plus saubere Kontrolle und zwölf wiederverwendete **originale** Unittests. Die ursprünglichen Quellen sind anhand Git-Blob `5b068596a0b2301f373fe89421189654db15c963` und `aa85775476cbb70e9d3e5852db860575f76cc932` festgebunden; die veröffentlichten neuen Git-Blobs entsprechen den lokal geprüften `b322675b6c68798ff9cef83160bab436a77f8814` und `0da4412367ea85cb83f9efb7333bd51269949b12`. Neugelernt: Teil-TSVs/-Receipts, unerwartete `test-`-Dateiendungen, partielle Summary und Symlink-Vorfahren können alte lokale Inventarscans passieren. Diese Befunde sind **keine** alten realen Studienfehler und keine Freigabe. Neuer Guard bleibt rein lokal/read-only und von PR #508 getrennt.

**Unverändert gesperrt:** Der Originalcheckout unter `/workspace/scratch/0549bca61ccf/device-ai-weight-audit` fehlt tatsächlich; Git-DNS nicht verfügbar. Originalcontroller mit gefälschter Baseline, fehlerhaften echten Checkpoints und unerlaubter Wiederaufnahme **NOT_RUN**; alle Modellläufe verboten; keine Originalquelle, produktive Runtime, M5/M6-Ergebnisse, Finaldaten oder fremden Runner geändert. Qualität weiterhin **FAIL**, alter Finaltest verbraucht, **M5_UNCHANGED/M6_PARITY_FAIL_RESTORED**, null Modellstarts und null Optimizerupdates. Der eigene Branch ist nicht im Main gemergt, und keine Änderung ist in #508 integriert.

**Nächster unabhängiger Prüfpfad:** passenden authentischen Checkout und die registrierten Inputs tatsächlich verifizieren, dann den kombinierten Patch gegen tatsächlichen #508-Head source-pinned auf synthetische ungültige Baselines, manipulierte Receipts/Checkpoints und falsche Wiederaufnahme prüfen, ohne ein Modell zu starten. Für eine künftige Studie sind exklusive extern attestierte Finalnutzung und vorab definierte neue Train/Dev/Final-Daten-/Retentions-/LR-Gates weiterhin erforderlich. Die Lease ist frei; keine Bots oder Actions gestartet.

## Fortsetzung 04 — zusätzliche lokale Inventar- und Zeitfenster-Gegenproben (05.11.2026)

**Tatsächlich veröffentlichter Forschungscommit:** [`f79dbb4b5b81918c11d14ec4c0fd3def64c06969`](https://github.com/Ullmann27/device-ai/commit/f79dbb4b5b81918c11d14ec4c0fd3def64c06969); basiert per Fast-forward mit erwartetem Head auf `3bba7b6e3f1bd590827e09994952e377b7446ec1`. Aktueller fremder Draft-PR #508 wurde erneut geprüft und bleibt bei `59891cfad6dd015f47c187cc688ea94b0026ad7d`. Artefakte ausschließlich unter [`continuation-04/`](continuation-04/README.md): source-pinned Kandidat, Testdatei, README und JSON-Audit.

**Neuer ausgeführter Nachweis:** Lokales Python 3.13.5: `python3 -B -m unittest -v test_pretest_exclusivity_expanded.py` → **20/20 PASS**. Sieben **neue** synthetische Gegenfälle waren am exakt gepinnten älteren Guard RED und am neuen rein lesenden Forschungsprototyp GREEN; eine saubere lokale Positivkontrolle und zwölf source-pinned bestehende Regressionstests ebenfalls grün. Der Vorversions-Guard übersah verschachtelte Testlogs/Marker, Root-Testartefakte, versteckte Summarymarker, inneren Symlink, überlappende Studienausgaben sowie in einem künstlich definierten zweiten Hashzeitpunkt injizierte Testdaten. Das sind **synthetische Gegenbeispiele**, keine tatsächlichen historischen Testverwendungen. Der Nachfolger scannt konservativ und doppelt, sperrt Überlappung und Symlink-Descendants. Der neue Guard Git-Blob ist `16217bfb24daba244d17af5d4ea65810cd43ce99`, Testblob `3472ab6b804483a2f8253266b571c4a6f54db985`; beide wurden nach Publikation erneut über den Connector verifiziert. Der neue Quellcode wurde gegen die exakten lokal getesteten SHA-256 `3b5b213522c22c83b821269e71372e57747bccc50813e3865169a11cb19eec50` / `3fb1565db92b9d0101ee53d71ecde378517679a50032523807fac4f7067c537e` gebunden.

**Sperren bleiben:** Das neue Modul ist keine atomare Exklusivität, verhindert nicht alle TOCTOU-Interleavings und beweist keinen früheren globalen Finaltest-Nichtverbrauch. Es ist weiterhin **nicht** in die kombinierten #508-Runner integriert. Authentischer Checkout fehlt nach erneuter Verzeichnisprüfung; DNS verhindert Git-Klon, Original-JARs/Checkpoints bleiben ungeprüft und unbenutzt. Originalcontroller mit kaputter Baseline/Checkpoint/Resume **NOT_RUN**; null Modellstarts/Optimizerupdates; kein Training, keine Tests der verbrauchten finalen Menge, keine Qualitätsfreigabe. **M5_UNCHANGED, M6_PARITY_FAIL_RESTORED, alte Qualität FAIL**, alte Finaldaten verbraucht. Keine aktiven Runner, fremden Zweige, historischen Belege oder Produktionssysteme geändert. Lease ausdrücklich **frei**.

**Weiteres Vorgehen:** Erst originale autorisierte Controllerdateien und registrierte Eingaben mit Hashes sichern und durch unabhängige synthetische negative Runnerfälle (Baseline FAIL, beschädigte Checkpoints, ungültige Receipt und Cross-Study-Finalreuse) beweisen, dass der wirklich gepatchte Controller vor jedem Modellprozess sperrt. Danach allenfalls eine neue, vorab dokumentierte unabhängige Daten-/Retentions-/Lernratenstudie separat zur Freigabe vorlegen. Alte S1/W1-Trainingseinläufe sowie Finaltest bleiben gesperrt.


## Fortsetzung 05 — unabhängige Kandidatenstatus-Baseline als fehlendes Gate (05.11.2026)

Die #508-Quellrunner und der eigene kombinierte Patch wurden erneut auf die aktuellen Heads `b8dba7ef9abdf126958e13e08612fa591b4dd3b8` / `59891cfad6dd015f47c187cc688ea94b0026ad7d` gepinnt: **16/16 exakte Kontexthunks** auf drei authentisch über den Connector gelesenen Runnern, weiterhin **nicht** in #508 integriert. Eine neue, direkt am Original-`freeze_evidence` und dem hypothetisch angewendeten `study_choice_authorization` untersuchte Kontrollflusslücke ist dokumentiert: `NO_DEVELOPMENT_SIGNAL` und `ELIGIBLE` sind ohne unabhängigen initialen Dev-SCORE nicht jeweils auf `wideSelected >= 12 && wideSelected >= wideInitial + 4` geprüft; lokale Choice-/Hash-Selbstkonsistenz genügt nicht als externe Attestation. Details, exakte Blobs und Beweisschranken: [Fortsetzung-05-README](continuation-05/README.md) / [Audit](continuation-05/source-audit.json).

Der erlaubte isolierte, **nicht integrierte** [synthetische Choice-Oracle](continuation-05/choice_signal_oracle.cjs) erreichte **20/20 tatsächlich im V8-Isolat ausgeführte modellfreie Fälle** (7 ACCEPT/13 BLOCK). Kein echter Python-/JVM-Runner-Test. Die beiden privaten Archive wurden aus der Library materialisiert; nachfolgende wiederholte Container-/Python-Ausführung schlug mit `redis.exceptions.ResponseError` fehl, folglich **keine** SHA-256- oder JAR-/Checkpoint-Verifikation und **keine** Modell-/Controller-Ausführung. Auf die bloße Materialisierung darf kein PASS gestützt werden. Originalcheckout fehlt.

**Weiterhin:** M5_UNCHANGED, M6_PARITY_FAIL_RESTORED, Qualität FAIL, alter Finaltest verbraucht, keine Modellstarts, Rückwärtsläufe, Updates oder Training. Nächster zulässiger Schritt ist die unabhängige Source-/Log-/Receipt-gepinnte Validierung des **initialen** Dev-SCORE für beide Studien, danach vollständige negative Originalcontroller-Regressionen zu falscher Baseline, kaputtem Checkpoint und unzulässiger Wiederaufnahme ohne Modellstart. Der Prototyp darf nicht zu einer Finaltest- oder Trainingsfreigabe umgedeutet werden. Lease am Ende **frei**.

**Verifizierter Belegcommit für Fortsetzung 05:** [`04a4da861a1e206e09aeea322f685b32e27be94d`](https://github.com/Ullmann27/device-ai/commit/04a4da861a1e206e09aeea322f685b32e27be94d). Nach Update der eigenen Ref über die GitHub-REST-Refs-API als echter Fast-forward ab `b8dba7ef9abdf126958e13e08612fa591b4dd3b8` nachgelesen, Parent bestätigt. Das aus diesem neuen Commit erneut gelesene CJS-Blob `98eff6703a5aa680f8c5ef34244658150b44a008` wurde anschließend **erneut 20/20 PASS im V8-Isolat** ausgeführt. Externe Python/JVM/Originalcontroller und Archiv-Hashprüfung bleiben NOT_RUN. Fremd-PR #508 steht bei `59891cfad6dd015f47c187cc688ea94b0026ad7d`. Lease bleibt freigegeben.


## Fortsetzung 06 — authentische Archivintegrität und modellfreie Manipulationsprüfungen (05.11.2026)

**Erster veröffentlichter Belegcommit:** [`4e2609ed8d041ec687514e02e76563825d4ac16a`](https://github.com/Ullmann27/device-ai/commit/4e2609ed8d041ec687514e02e76563825d4ac16a), geprüft als direkter Fast-forward von `f8b2ac35e144dc95cba4941904c26f6b91c48c39`. Zwei originale, privat wiederhergestellte ZIP-Archive wurden diesmal **wirklich** im Container SHA-256-verifiziert: **beide 2/2 Archiv-SHA-256 exakt**; die ZIP-CRC-Prüfung ohne Fehler; **3/3 Diagnose-JARs**, **9/9 Modell-/Laufzeit-Dateien** (darunter alle vier Original-Checkpoints), **4/4 Modellkarten-Konsistenz** und **2/2 Laufzeit-Abhängigkeitsabgleiche** PASS. Private Wiederherstellungsdaten und Hash-Strings stehen **nicht** im Git. Die Originalarchive und Checkpoints bleiben unverändert.

**Neue wiederholbare Quellen und Tests** ausschließlich unter [`continuation-06/`](continuation-06/README.md): schreibgeschützter [Archivprüfer](continuation-06/verify_archive.py), [synthetische Negativtests](continuation-06/test_verify_archive.py), [redigierter Audit](continuation-06/integrity-audit.json). Beide Pythondateien wurden tatsächlich lokal unter **Python 3.13.5** ausgeführt: **13/13 PASS**, davon eine saubere Kontrolle und zwölf Ablehnungsfälle; beschädigte synthetische Checkpointdaten, vertauschte Gesamt-SHA, fehlende/zusätzliche ZIP-Einträge, ungültige Pfade/Symlinks, korrupte Größen und widersprüchliche Modelldeklarationen werden verworfen. Original lokal getestete Git-Blob-SHAs `cfcc5bcd892675b076cf42feadf04c5b13dccd7a` und `a39e162a83c35cbeb682c9aeb99063a605368260` entsprachen **exakt** den auf GitHub als Blobs geschriebenen Bytes. Keine Payload-Ausführung.

**Neuer eng begrenzter Befund:** Das Original-Manifest bezeichnet die post-selection-TRAIN-Fitwerte als `dataset=TRAIN`, einzelne eingebettete Scoreobjekte aber als `split=test`. Das ist ohne originale Runner-Einordnung eine **Provenienz-/Feldnamensmehrdeutigkeit**, kein Beleg für historischen Finaltest-Missbrauch. Die beiden archivierten Qualitäts-/Kapazitätsgates melden weiterhin **FAIL**. Die bisherige ca. achtfache relative nf-Gradientenskala bleibt ohne neue LR- oder EOS-Ursachenentscheidung.

**Grenzen:** ursprünglicher Checkout-Pfad fehlt tatsächlich; anonymer Git-Zugriff per DNS blockiert. Python/Java sind jetzt verfügbar, aber keine vollständige authentische Controller-Quelle lokal. Deshalb bleiben echte vollständige #508-Controller-Gates mit manipuliertem Baseline-Receipt, beschädigtem Checkpoint und unzulässigem Resume weiterhin **NOT_RUN**. Die 16/16 Patch-Hunks wurden in einer früheren Runde quellengebunden geprüft, diesmal **nicht** neu ausgeführt. PR #508 wurde nicht verändert. Das beschriebene ZIP-Gate ist **nicht** in Runner oder Patch integriert und ist **keine Trainingsfreigabe**. M5_UNCHANGED, M6_PARITY_FAIL_RESTORED, alte Qualität FAIL und alter Finaltest verbraucht; **0 Modellstarts, 0 Backward-Pässe, 0 Optimizerupdates**.

**Nächste Aufgaben:** authentischen, quellgebundenen vollständigen Controller samt Initial-Dev-Receipt und Testlogs reproduzierbar materialisieren; die ursprüngliche Candidate-Entwicklungsschwelle mit initialer Baseline belegen, dann ausschließlich mit **synthetischen** Modellausführung blockierenden Stubs fehlerhafte Baseline/Checkpoint/Resume/Cross-Study-Verbrauch testen. Prospektive unabhängige Train/Dev/Final-Teilung, Retention/LR und externe atomare Finaltest-Verbrauchsbuchung bleiben vor jedem späteren Modellversuch zwingend. Lease **frei**, keine Bots/Actions/Fremdbranches geändert.

## Fortsetzung 07 — Rohdaten-/DEV-Baseline-Quellenprüfung (05.11.2026)

**Veröffentlichter und auf GitHub wieder gelesener eigener Evidenzcommit:** [`597b54feea705d633024ef46bcf5b08c1971e261`](https://github.com/Ullmann27/device-ai/commit/597b54feea705d633024ef46bcf5b08c1971e261), direkter Fast-forward von `e42be960a1bef2576fa07a1dda69eded4205e639`; #508 unverändert auf `59891cfad6dd015f47c187cc688ea94b0026ad7d` (Draft/unmerged). Darin ausschließlich [Fortsetzung-07-Quellen](continuation-07/README.md) unter erlaubtem Forschungspfad.

**Tatsächliche, unabhängige Prüfungen:** **6/6 vollständige Original-TSV-Logs** (`intent-small/intent-wide` in v1, v2, v3) wurden an #508 samt **6/6 Original-Receipts** gelesen. Die vollständigen UTF-8-Inhalte wurden im JS-Isolat mit eigener SHA-256-Implementierung (vorher **3/3 Referenzvektoren bestanden**) gehasht; alle **6/6** SHA-256 stimmen mit den archivierten Receipts überein. Das neue, auf Git-Blob `724d54e45470eca8fe22e50d005e612c17d7c336` gepinnte, read-only `raw_dev_receipt_gate.cjs` bestätigte alle **6/6** Original-Rohlog-/Baseline-/Checkpoint-Auswahl-Checks. Dazu **17/17** synthetische Gate-Fälle im V8-Isolat PASS, Test-Git-Blob `584e36cdc5ca22295d7c540bd032821f79039ddf`; kein Node-/Python-Originalrunner gestartet. SHA-256, Git-Blobidentitäten, negative Fälle und Prüfschranken sind im [Rohdaten-Audit](continuation-07/raw-dev-audit.json) dokumentiert.

**Quellenbasierte DEV-Entscheidung:** `wide.selected >= 12 && wide.selected >= wide.initial + 4`. Tatsächlich `v1: 0→10/24` (**NO_DEVELOPMENT_SIGNAL**), `v2: 0→13/24` (**DEV_ONLY_ELIGIBLE**), `v3: 0→16/24` (**DEV_ONLY_ELIGIBLE**). Alle sechs Selektionswerte und Schritte stimmen mit den authentischen Roh-SCORE-Zeilen überein. Das ändert nicht die spätere Qualitäts- oder Finaltestbewertung. Ein widersprüchlicher deklarierter Status kann vom unabhängigen Prüfer isoliert abgefangen werden; die eigentlichen #508-Controller wurden **nicht verändert**.

**Laufzeit-Grenze bestätigt:** Der alte Workspace-Pfad existiert nicht. Python 3.13.5, Java 21, Node 22 und Git sind vorhanden; anonymer GitHub-Clone scheitert bereits an DNS. Authentische vollständige Python-Originalcontroller + reale Resume-Checkpoints waren deshalb lokal nicht für eine Runner-Regression verfügbar. **Echte Controller-RED→GREEN, kaputtes Checkpoint-Resume, Cross-Study-Final-Claim: NOT_RUN.** Frühere private ZIP-Integritätsprüfung bleibt historisch gültig, wurde jetzt nicht wiederholt. Keine Modellprozesse, Rückwärtsläufe, Optimizerupdates, Trainings-, Finaltest- oder Java-Läufe. **M5_UNCHANGED; M6_PARITY_FAIL_RESTORED; alte Qualität FAIL; alter Finaltest CONSUMED.** Kein Merge nach main, keine Änderung an #508; PR #492 bleibt Draft. Lease **frei**.

**Nächster erlaubter Einstieg:** Authentischen Originalcontroller in einer autorisierten lokalen Laufzeit reproduzierbar bereitstellen, mit modellfreien Prozess-Stubs den existierenden kombinierten Patch auf falsche Baseline, korrumpierten Checkpoint, unerlaubte Wiederaufnahme und globale Finaltest-Exklusivität testen. Alle anderen Trainings-, Modell- und Finaldatenpfade bleiben gesperrt.
