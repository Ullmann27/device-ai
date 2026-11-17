# Fortsetzungsprompt für Sol / Astra / weitere Entwickler

Arbeite im Repository `Ullmann27/device-ai`, Branch
`sol/continuum-autonomous-coding-20261024`. Zuerst tatsächlichen HEAD und
`git status --short` lesen. Basis des Auftrags war
`aa8780a8489e9e972e9a35a5af9e22866a0ea6e2`; main ist ausdrücklich unberührt.
Der Besitzer verlangte bei seiner Meldung „noch 30 %“ einen zügigen Commit und
diese Übergabe. Code offen bearbeiten, keine Codesperren oder Eigentumslocks
für andere Entwickler errichten. Normale kurzlebige Laufzeit-Leases bleiben
Sicherheitsmechanismen und dürfen nicht entfernt werden.

Lies `AGENTS.md`, Experimentvertrag/Register, aktuellen Issue #41 und
`docs/CONTINUUM-AUTONOMOUS-CODING-RESULT-20261024.md`. PR123/124 sind Vorfahren,
keine alten Branches blind mergen. Die drei Audits und Forschung liegen unter
`docs/evidence/sol-autonomous-coding-20261024/`.

## Bereits fertig – nicht erneut entwickeln

`coding-task.mjs`, `coding-context.mjs`, `coding-provider.mjs` und
`coding-orchestrator.mjs` verbinden typisierte, commitgebundene Aufgaben mit
der vorhandenen CCC1→MobileJob→Backlog→Teamleader→Repair→zweiter Worktree-Kette.
Vorhandener Docker-Executor ist angeschlossen; Hostpfad bleibt auf reine
numerische Module begrenzt. Maximal drei Dateigrants, drei Modellaufrufe,
hartes Kontextbudget und Revisionshistorie. Neue scoped Reparaturen werden
niemals automatisch als Lern-/Replay-Erfolg übernommen.

Android hat ehrlichen allgemeinen Auftragsentwurf/JSON-Export in bestehender
Entwicklungsoberfläche und getrennte Bestätigung. Noch KEIN Transport dieses
neuen Vertrags zur Termux-Ausführung. Keine neue App, kein Release.

## Wesentlicher Beleg und offenes Problem

Erstes echtes lokales Qwen-Coder-1.5B-Experiment: zwölf Tests, Baseline vier
Fehler, erster eigener Patch drei Fehler, zweiter eigener Patch zwölf grün,
unabhängig zwölf grün. Zwei echte Modellaufrufe/64,792 s. Original unverändert.
Prompt/Antwort/Patch/Quittungen sind eingecheckt. Erster Implementierungsstand
war während dieses Laufs noch dirty; daraus keinen frozen Benchmark machen.

Wiederholung am eingefrorenen `2c985fba` endete dagegen mit drei abgewiesenen
Modellantworten (`ABSTAINED`). Diagnose: no-op Patch an der Eingabevalidierung,
keine Änderung; Client weist `oldText == newText` korrekt ab. Fünf Pipeline-
Modellaufrufe plus zwei Diagnoseaufrufe plus ein Smoke = acht insgesamt.
NICHT als zuverlässige autonome Coding-Fähigkeit ausgeben.

## Nächste kleine vollständige Aufgabe (P1)

Abschließende Checks: volle Node-Suite1.153pass/6skip/0fail;
Backend60/60; fokussiert Kotlin21/21; echter SDK-/Gradle-Lauf
`:app:testDebugUnitTest :app:lintDebug` Exit0 in5m13s,961pass/3skip,
Lint0Befunde. Source-Manifest516Dateien ohne Abweichung. Keine APK/kein Gerät.

1. Verifiziere endgültige Teststatusdatei und Git-HEAD. Wiederhole keine bereits
   grüne Gesamtsuite ohne neue Änderung/konkreten Grund.
2. Verbessere begrenztes Fehlerfeedback bei ungültigem/no-op Modelloutput:
   eigener fester Fehlercode, persistenter abgewiesener Antwortfingerabdruck,
   nächste Revision bekommt die wirkliche Ablehnungsdiagnose; kein dreifaches
   blindes Wiederholen. Keine Lockerung der Schema- oder Dateigates.
3. Füge echte TDD-Negativtests hinzu: abgelehnte no-op Antwort, erschöpftes
   Aufrufbudget ohne weiteren Prozessstart, Deadline nach Retrieval,
   Crash nach Mobile-Receipt vor CCC1-Receipt (Replay soll keine Modell-Auth
   benötigen). Keine neue Repair-Pipeline.
4. Friere Code vor neuem Versuch als Commit ein. Verwende eine NEUE, vorher
   geschriebene Akzeptanzaufgabe mit unveränderlichen Tests. Modellauswahl oder
   Trainingskontamination nicht aus dem Aufgabennamen behaupten. Echte
   Kotlin- und generative Mehrdateienversuche sind noch offen.
5. Erst danach (P2) neuen Taskvertrag über bestehenden Android/Termux-Rückkanal
   mit exakter Grantbindung/Replays/Abbruch anbinden. Auf Fold7 native
   Isolation/Toolchain wirklich prüfen; Docker ist keine Handyvoraussetzung.

## Reproduktion und lokale Artefakte

Standardchecks:

```sh
env -u NODE_TEST_CONTEXT node --test .github/continuum/*.test.mjs scripts/*.test.mjs
cd backend && npm ci --ignore-scripts && npm test
```

Echter Modellversuch (nur wenn Modellserver bewusst gestartet und isolierte
Kapazität real vorhanden):

```sh
node scripts/continuum-coding-benchmark.mjs /ABSOLUTES/NEUES/EXTERNES/BELEGVERZEICHNIS
```

Der Prozess benötigt `CONTINUUM_LOCAL_BRAIN_API_KEY`,
`CONTINUUM_LOCAL_BRAIN_MODEL`, `CONTINUUM_CODING_ISOLATION_CONFIG` und optional
`CONTINUUM_CODING_MODEL_SHA256`. Keine Secrets ins Repository schreiben oder
ausgeben. Lokaler Server ausschließlich `127.0.0.1:8089`, Client kann keine
Modellbefehle ausführen. Vor Loopback-Regressionstests den echten Modellserver
beenden, da der bestehende Integrationstest denselben festen Port benötigt.

In der bisherigen Cloud-Umgebung (nicht als dauerhaft verfügbar voraussetzen):
Modell `/workspace/scratch/models/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf`,
SHA `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`;
llama.cpp unter `/workspace/scratch/llama-runtime/llama-b11861`;
externe Toolchain-Umgebung
`/workspace/scratch/toolchains/android-gradle-env.sh`, SDK/Gradle/JDK-
Herkunftsmanifest vorhanden. Secrets ausschließlich externe private Dateien.
Der echte Modellserver wurde vor finaler Node-Regression wieder beendet.

Keine Signierung/Installation, keine Änderung der Paketkennung
`at.heinz.deviceai.mentorpaired`, kein main-Merge ohne Besitzerentscheidung.
CDX1-Stufen5–12 belegen keine bessere Coding-Intelligenz; keine experimentellen
Gewichte produktiv schalten. Ein grüner Testlauf bleibt begrenzte Evidenz,
kein Beweis fachlicher Vollständigkeit.
