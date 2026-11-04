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
