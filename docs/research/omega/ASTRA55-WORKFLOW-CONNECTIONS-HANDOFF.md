# Astra55 · Workflow-Verbindungen

## Auftrag und Zuständigkeit

Bestehende Arbeitsereignisse an Avatar und Audio absichern; kein neuer Eventbus und keine neue Aufgabenverwaltung.
Branch: `work/astra55-support-workflow-connections-20261112`.
Ausgangsstand: `d7a59c6f6855dab3884e44725d6a2c3a55634806` mit Integration `c1dcf613a8`.
Nur `CockpitWorkEvent.kt`, `ComputerWorkAvatarBridge.kt`, fokussierte Tests und Hostbelege geändert.
AgentActivity, Controller, Capture, Monitor, ToolGate, Paket und Originalsignatur bleiben in ihrer bisherigen Zuständigkeit.

## Wirkung

- Neue Dispatches können keine bereits überholte Ausgangsbeobachtung übernehmen.
- Späte OBSERVED-Ereignisse können den angenommenen Beobachtungsstand nicht zurücksetzen.
- Schrittbezogene Pause/Rückfrage kann keinen anderen laufenden Schritt beenden.
- Avatar-Aktionskennungen enthalten Capture-Sitzung, längengebundene Auftragskennung und Schrittkennung.
  Zwei Aufgaben mit demselben Schrittnamen behalten ihren eigenen Bewegungsimpuls.

Die bestehende Kette ist instanziiert: Activity → OwnerComputerSessionController → Workflow/VerifiedNodeAction
→ LiveActionCockpitAdapter → bestehender CockpitAnimationBus → ComputerWorkAvatarBridge
→ AvatarDirector/AvatarPoseReducer und ComputerWorkCuePlayer.
VERIFIED erreicht den gebundenen Reducer; es erzeugt keine zusätzliche Besitzerzustimmung.

## Tatsächliche Tests

Direkter Kotlin-2.0.20-Compiler/JUnit, 512 MB, ein Prozessor, kein Gradle.
Originalquellen von Workflow, Action-Verifier, Adapter, Bus, Bridge, Director/Pose und CuePlayer ausgeführt.
Nur der Android/OS-Port wird im Pipeline-Test durch synthetische Beobachtungen ersetzt.
Sechs Enum-/Datendeklarationen werden unverändert aus ihren Originaldateien extrahiert.

- Baseline: Kompilierung PASS; 49 bestehende Tests PASS; 12 Regressionen, davon vier FAIL.
- Reparatur: Kompilierung PASS; 49 bestehende Tests PASS; alle zwölf Regressionen PASS.
- Einschließlich echter VERIFIED-Weitergabe, Stop während ausstehender Nachbeobachtung,
  verworfener später Erfolgsmeldung, verworfenen wartenden Klicktons und erneuter Sitzungsbindung.
- Quellhashes blieben während der beiden Prüfläufe unverändert.
- Erster isolierter Compileraufbau fehlte um die bestehende AvatarSentenceAccentPolicy; ergänzt.
  Dieser Harnessfehler ist unter compile-setup-attempt dokumentiert und kein App-Buildbefund.

Belege: `docs/evidence/astra55-support-20261112/workflow-connections/`.
Reproduktion: `tools/astra55-workflow-connections/run.py` mit `--repo`, `--compiler-lib` und `--out`.

## Aktueller integrierter Vertragsstand

Hauptcommit `627f2b3b593737b3f6659524baaa1b7ba29329ea` ist übernommen.
Der Test-Port implementiert das zusätzliche `expectedSearchField`; originale LiveSearchSemantics und
ClickRequest.matchesExpected sind in der geprüften Quellkette enthalten.

**Geprüfter Quellcommit: `c19e7ea837c7a3c9e5d7404906102c335ae2a374`.**
Auf diesem Vertragsstand: Kompilierung PASS, **60 bestehende Tests + zwölf Regressionen = 72/72 PASS**.
Die acht vorhandenen LiveSearchClickTest-Fälle sind zusätzlich aufgenommen; die aktualisierte Workflowklasse
bringt weitere bestehende Fälle mit. Baseline und erster Reparaturlauf bleiben als getrennte Belege erhalten.

Der letzte Lauf fror den Arbeitsbaum bei HEAD `3f6fd72737c09aa8fda5bdc74f5616942dc7fbd7` samt angepasstem
Test-Port ein. Nach dem Commit wurden alle 27 Quell-/Test-/Deklarationsdateien per SHA-256 mit dessen
Git-Baum verglichen: vollständig identisch. Beleg: `workflow-connections/source-binding.json`.
Keine Aussage über ungeprüfte neue Quellen wird aus den alten grünen Läufen abgeleitet.

## Grenzen

PORTIERT im eigenen Entwicklungszweig; HOST-VERIFIZIERT für den oben gebundenen Prüfumfang.
Android/Gradle/GPU/APK: NOT_RUN. GERÄTEBESTÄTIGT/Fold7: NOT_RUN. LOCAL_ONLY / NOT_PUSHED.
Keine Remote-Mutation, kein Main-Merge, keine Signierung oder Installation.
Register: EXP-07 / EXP-11; bestehende Negativtests und bestehende Orchestrierung, keine neue Forschung.
Rückfall: nur die beiden kleinen Produktionsänderungen zurücknehmen, keine Datenmigration erforderlich.

## Integratorhinweis

newSession() muss einen abgeschlossenen, weiter sichtbaren Computer-Feed vor dem Chat-Sitzungswechsel stoppen.
Der Hauptintegrator besitzt diese Activity-/Controller-Korrektur; dieser Zweig verändert die Dateien nicht.
