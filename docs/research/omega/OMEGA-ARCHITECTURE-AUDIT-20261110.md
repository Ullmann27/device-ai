# OMEGA: unabhängige Architekturdiagnose

Auditbasis: lokaler Commit `515afc28af11b9befeb5a61f9c44411d423635a9`,
Git-Baum `735a663d522e16d56e979c78a908e9f3baa2f031`.
Der Integrator hat Remote-HEAD `28bee009ef54e8c60eb1468dc90c9f4a5a51edea`
gegen genau diesen Baum bestätigt.
Dieser Bericht behauptet keine vollständige Zeilenprüfung aller historischen Zweige.
Er untersucht die tatsächlich verbundenen Hauptpfade und die fünf benannten PR-Kandidaten.
Keine Produktquelle, Renderer, Modellgewichte, Datenbank oder Berechtigungen geändert.

## Die drei vorrangigen Engpässe

### 1. Gesprächsbezug und Arbeitsauftrag besitzen keine gemeinsame belastbare Identität

Die zentrale Benutzerformulierung wird als Anschlussfrage erkannt, erzeugt aber
keinen Themenhinweis. Das wurde mit den unveränderten Produktionsklassen ausgeführt:
`requiresPreviousOwnerTurn=true`, `renderHint=EMPTY`. Ursache: Die Follow-up-Policy
kennt „vorher“, der `LocalConversationSituationTracker.RETURN_CUE` kennt nur
„vorhin/davor/dort weiter/weiter bei/zurück zu/wieder zu/fortsetzen“.

Das größere strukturelle Problem: `LocalConversationContext` hält vier vollständige
Turns. Der Themen-Tracker hält daneben bis zu acht Themen, aber nur Stichwörter
und gekürzte Labels. Die bereits vorhandenen Felder `currentGoal`, `currentTask`,
`currentProject`, `openQuestions` und `recentCorrections` werden von keinem
Produktionsaufrufer gefüllt. Nach Abschweifungen bleibt daher eventuell ein
Themenname erhalten, aber kein prüfbarer Entwicklungsstand mit zu bewahrenden
Vorgaben. Ein bloß längerer Prompt rekonstruiert die verlorene Bindung nicht sicher.

Anker:
- `cognitive/LocalDialogueContextLimits.kt:7` – vier Turns.
- `cognitive/LocalConversationContext.kt:65` – append/Abschneiden;
  `:116` – nur vollständige Besitzerturns als Evidence.
- `cognitive/LocalConversationSituationTracker.kt:23`, `:53`, `:115` –
  Themenbildung, Rückbezug, erlaubte Rückbezugswörter.
- `cognitive/ConversationSituationModel.kt:89`, `:138`, `:145` – vorhandene,
  im Produktpfad unbenutzte Scope-/Frage-/Korrekturmethoden.
- `AgentActivity.kt:10060` – Follow-up-Task wird aus Besitzerturns zusammengesetzt;
  `:10157` prüft Existenz von Kontext, nicht eindeutige Bindung an einen Gegenstand.
- `cognitive/LocalFollowUpTaskFrame.kt:43` – bisherige mutierende Aufträge werden
  vorsichtshalber nicht erneut als Task eingebracht. Das schützt Autorität,
  ersetzt aber keinen separaten Datenvertrag für fortbestehende Anforderungen.

Priorität: Dieser Fehler liegt vor Planung, Modellausgabe, Werkzeugwahl und
Visualisierung. Mehr Rechenzeit auf dem falschen oder verlorenen Ziel verstärkt
keinen verlässlich brauchbaren Fortschritt.

### 2. Die vorhandene Ergebnisprüfung bestätigt hauptsächlich Sprachform

`AgentActivity.kt:10386` verwendet `LocalDialogueQualityGate`; `:10403` setzt
`evaluationConfirmsOutcome=false`. Das ist eine richtige Sicherheitsgrenze.
`LocalDialogueQualityGate` prüft leere/überlange Antworten, Prompt-Leaks,
Wiederholungen und bestimmte Kontextverweigerungen. Sie prüft ausdrücklich keine
Sachrichtigkeit, Kausalität, Zielerfüllung oder unveränderten Restanforderungen.

`BoundedAgent.kt:472` ruft diesen Evaluator auf; `:562` speichert daraus überwiegend
PARTIAL-Erfahrungen. `:571` verlangt zusätzlich echte Tool-Belege und einen
geeigneten Evaluator für `outcomeVerified`. Damit existieren vorsichtige Belege,
aber für den Beispielauftrag kein Ende-zu-Ende-Orakel „richtige Animation geändert,
übrige Wünsche erhalten“. Die bestätigten technischen Quittungen sind notwendig,
beweisen allein aber keine erfüllte Benutzerabsicht.

Priorität: Ohne fachlich begrenzten Prüfer können weitere Denkversuche lediglich
anders formulierte unbestätigte Antworten erzeugen. Ein Prototyp sollte deshalb
zunächst deterministisch prüfbare Bindungs-/Erhaltungsinvarianten messen und
keine allgemeine Intelligenzsteigerung oder erfolgreiche Grafikbearbeitung behaupten.

### 3. Arbeitsanzeige, Freigabe und reale Ausführung sind noch nicht überall dieselbe Phase

Der bestehende Vertrag `AgentTaskEvidence.kt` enthält bereits Task-ID, Versuch,
Sequenz, Call-ID, Argumentfingerprint, frische Quittungen und Arbeitszustände.
Diesen Vertrag wiederverwenden; kein neuer Ereignisbus ist erforderlich.

Konkrete Diskrepanzen im eingefrorenen Quellstand:

1. `BoundedAgent.kt:417` emittiert `EXECUTING` aus `onExecutionStarted`.
   `AgentToolLayer.kt:343` ruft diesen Legacy-Callback vor Commit und erneuter
   Abbruchprüfung auf. Der tatsächliche `onDispatchStarted` folgt erst bei `:360`.
   Der vorhandene `ToolDispatchObserverTest.legacyTrailingObserverStillRunsBeforeCommitAndCanCancel`
   beschreibt den kontrollierten Gegenfall: Callback sichtbar, Backendaufrufe null.
   Das beweist keine unberechtigte Werkzeugausführung, wohl aber eine zu frühe
   kognitive Ausführungsanzeige.
2. `AgentActivity.kt:8800` ordnet schon eine vorhandene
   `approvedModelWebSession` dem Hologrammzustand RESEARCH zu.
   Autorisierte Sitzung und tatsächlich begonnene Recherche sind verschiedene Ereignisse.
3. `AgentActivity.kt:10499` beendet die Cinema-Timeline mit
   `chiefTurn.deliver()` vor der späteren Prüfung des Resultats. Diese boolesche
   Lifecycle-Antwort besagt „erstmals zugestellt“, nicht „Auftrag nachweislich erfüllt“.
   `CockpitAnimationTriggers.turnEnded` setzt aktive/wartende Schritte abhängig
   von diesem Bool auf DONE/FAILED.

Positiver Bestand: `createToolGate` startet die konkrete Werkzeug-/Rechercheszene
im tatsächlichen Runner (`AgentActivity.kt:9718`), und `ToolWorkJournalBridge`
speichert verspätete, wiederholte oder ungewisse Quittungen als UNKNOWN.
Die Tool-Autorität ist also nicht insgesamt ungesichert. Die Korrektur besteht
in konsistenter Darstellung vorhandener Belege und in eng gebundener Zustimmung.

Priorität: Der Nutzer muss auf Darstellung und Abschluss vertrauen können.
Neue Lichteffekte können eine verfrühte Zustandsmeldung nicht berichtigen.
Cinema-Rendering kann unabhängig weiterentwickelt werden; nur seine Eingangsdaten
müssen aus belegten Zuständen kommen.

## Bestehendes Architekturmodell

| Bereich | Existenz und Aufruf | Status der geprüften Basis | Grenze |
|---|---|---|---|
| Eingabe und Routing | `AgentActivity` → OwnerResearchCommand/AppLookup/ContextDirective/Bootstrap → `runContinuumDialogue`/`runLocal` | Produktpfad angeschlossen | Zahlreiche getrennte früh zurückkehrende Routen; kein gemeinsamer bestätigter Arbeitsauftrag |
| Kurzzeitkontext | `LocalDialogueRetention.context` → `LocalConversationContext` → FollowUpFrame/SituationHint/PromptHistory | Angeschlossen; vier Turns, Themenhinweise | Unbestätigter Kontext, keine Toolrechte |
| Bestätigte Erinnerungen | `ContinuumLearningCore`/`ContinuumMemoryLayers`/TemporalMemory; HybridMemoryRetriever → AiContextCompiler | Bestehende Store-/Retrieverpfade angeschlossen | Bestätigung, Revision und Widerruf nicht durch Modelltext ersetzbar |
| Langzeit-Arbeitsstand | `CockpitWorkJournal` → WorkDecisionRecall → LocalDecisionPromptDigest | Dokumente und echte Werkzeugquittungen angeschlossen | Kein allgemeiner Graph aller Animationsstände; Toolzustand ≠ Zielerfüllung |
| Lokales Modell | `runLocal` → BoundedAgent → LocalModelRuntime | Angeschlossen, Modellidentität und Abbruch geprüft | Kein neuer realer Gemma-Lauf in diesem Audit |
| Zusätzliche Denkzeit | `ChatReasoningRouter` → LocalModelReasoningAdapter → ContinuumIntelligenceCoordinator | Bereits live für ausgewählte schwere werkzeugfreie Aufgaben; max. vier Zusatzaufrufe, 20 s | Niedrig priorisierter unbestätigter Hinweis; kein allgemeiner Qualitätsnachweis |
| Werkzeuge | BoundedAgent → ApprovalProvider → ToolGate → registrierter Runner → ReceiptStore | Angeschlossen; Freigabe/Replay/Abbruch getrennt | Kein freier Shelltext aus Modellantworten |
| Ergebnis-/Erfahrungsbeleg | AgentTaskEvidence → ExperienceStore/ToolWorkJournalBridge | Angeschlossen, frische Quittungen erforderlich | Dialog-Evaluator prüft Form, nicht fachliche Erfüllung |
| Avatar/Cinema | CockpitAnimationBus/Triggers → Presenter/Shell; AvatarDirector aus Laufzeit-/TTS-Signalen | Angeschlossen | Teilweise voneinander abweichende Zustandsableitungen; Geräteprüfung offen |
| Stimme | TTS onStart/onAudioAvailable/onRangeStart/onDone → AvatarDirector/voiceSignal | Angeschlossen | Text-Pace ist Fallback, kein gemessener echter Audiosync |

## Benannte Forschungskandidaten und Anschluss

Die Nachbar-Worktrees wurden nur lesend untersucht; spätere fremde Änderungen
werden nicht in diesen Befund eingeschlossen. Remote-SHAs stammen aus dem
Integrator-Snapshot; lokale Commit-SHAs dürfen ohne Baumvergleich nicht als
identische Remote-Commits ausgegeben werden.

| PR | Remote-Snapshot | Codebefund | Entscheidung für diesen Schritt |
|---|---|---|---|
| #715 | `28bee009ef54e8c60eb1468dc90c9f4a5a51edea` | Fluid-Light-Shell und Lichtprojektion im Cinema-Pfad. Quell-Cognition unter `cognitive/` und `learning/context/` im lokalen Audit-HEAD byte-identisch zu geschütztem `17491f4d` | Laufende Cinema-Arbeit ungestört lassen |
| #716 | `00cf15d925d2c114d8ce57ee23dac473a89e6e0f` | Ctx3Context geändert, Ctx3Shadow aus LearningCore opt-in aufrufbar; Shadow ersetzt keine Antwort, führt keine Tools aus | Kein Beleg, dass natürlicher Hauptdialog bereits besser wurde; offene Negationsreviews separat prüfen |
| #717 | `4ee9f9c05a1f4623427b41735733ab633f291c6b` | CognitiveBudgetLocalResearch erzeugt default-deaktivierten Runner; keine Chat-Aufrufer. Lokales `requestedMode` wird vor selected TOOL_PLAN/RESEARCH_BACKED geprüft | Vor optionaler Förderung Routingreview beheben; keine zusätzlichen Live-Denkschleifen |
| #718 | `6eb6d3c26620f03a7844b9d6fe91b87a208d4910` | Isoliertes Lab, bestehender Reuse-Broker; kein App-Controllerimport. Bericht hält +9,4 % CPU bei Epoch-Churn und REJECT_PROMOTION fest | Negativer Befund bleibt gültig; nicht produktiv einschalten |
| #719 | `dafb3187faf2580d0a060a66fb7e82915f8fc6fc` | ContinuumMemorySkillResearch prüft immutable Snapshots, Scope und unabhängigen Verifier; default deaktiviert, Verifier default false; nur Skill-Kandidaten | Kandidatenbibliothek darf keine Tool-Autorität oder neue Fakten erteilen |

Belegklassen: Cinema dokumentiert reale Android-Quellkompilierung und native
Robolectric-Canvas-Tests; das ist kein Fold7-/Filament-/AGSL-Hardwaretest.
Für die Forschungszweige liegen Hostbelege vor; deren Zahlen wurden in dieser
Auditspur nicht erneut gemessen und sind daher Vorbefunde, keine neuen Messungen.
**Fold7, echte Modellqualität, Geräte-CPU/RAM/Thermik/Latenz: NOT_RUN.**

## Priorisierte Architekturentscheidung

Empfohlen wird Architektur B als nächster Prototyp: ein begrenzter,
versionsgebundener Arbeitsvertrag innerhalb des bestehenden Kontextbesitzers.
Der ausgewählte Prototyp bleibt standardmäßig ausgeschaltet; ein vertrauenswürdiger
typisierter Aufrufer registriert den Auftrag im vorhandenen
`ConversationSituationModel` über `LocalConversationContext`. Er soll Ziel,
konkreten Gegenstand/Kandidaten, zu erhaltende Vorgaben,
offene Rückfrage, Originalbenutzerturn und Ergebnisbelege getrennt halten.
Das vorhandene `AgentTaskEvidence` bleibt Ausführungsbeleg; ToolGate bleibt
einzige Werkzeugautorität; CockpitAnimationBus bleibt einziger Cinema-Bus.
Ein „ja“ darf nur eine aktuelle konkrete Rückfrage beantworten und niemals
alte/andere Werkzeuge freigeben. Unklarer Gegenstand blockiert die Bearbeitung
und erzeugt eine gezielte Frage, aber keine Forschungsszene.

A (Prompt/Retrieval verbessern) bleibt Baseline und würde den fehlenden Vertrag
nicht nachweisbar ersetzen. C (adaptive zusätzliche Modellaufrufe) kommt erst
nach korrekter Bindung und unabhängigem Nutzenbeleg: #717/#718 liefern derzeit
keinen allgemeinen Modellgewinn. Keine zweite Gedächtnisdatenbank, kein neuer
Planer als konkurrierendes System und keine automatische Gewichtsänderung.

Die Integrationsentscheidung muss anhand des isolierten Prototyps fallen,
nicht anhand dieses Architekturvorschlags. Abbruch bei zusätzlicher unberechtigter
Aktion, verlorener Negation, falscher Erfolgsanzeige oder fehlendem Nutzen gegen
den unveränderten Quellstand. Negative Resultate bleiben im Messbericht.

## Ausgeführte unabhängige Diagnose

`docs/evidence/omega-architecture-audit-20261110/AuditProbe.kt` kompiliert gemeinsam
mit den drei unveränderten Produktionsdateien. Fünf Assertions bestanden;
die mittleren drei bestätigen ausdrücklich eine fehlende Verbindung, keinen
behobenen Fehler. Die letzte ist eine positive Kontrollformulierung.
Keine Modellaufrufe, keine Android-Ausführung, kein Datensatz für allgemeine
Intelligenz. Kein Timing-Gewinn wurde hier gemessen.

Reproduktion:

```bash
kotlinc app/src/main/java/at/heinz/deviceai/cognitive/ConversationSituationModel.kt \
  app/src/main/java/at/heinz/deviceai/cognitive/LocalConversationSituationTracker.kt \
  app/src/main/java/at/heinz/deviceai/cognitive/LocalConversationFollowUpPolicy.kt \
  docs/evidence/omega-architecture-audit-20261110/AuditProbe.kt \
  -include-runtime -d /tmp/omega-architecture-audit.jar
java -jar /tmp/omega-architecture-audit.jar
```

Die zugehörigen Input-Hashes, Ausgabe und Toolversionen liegen daneben.
Der Befund zum Tool-Start ist Quellprüfung mit vorhandenem Regressionstest,
kein in dieser Spur erneut ausgeführter JUnit-Test. Der Integrator lässt ihn
in seiner Szenariospur unabhängig ausführen. Die dortige eng begrenzte
Korrektur verwendet den vorhandenen Dispatch-Callback, keinen neuen Lifecycle.

## Exakte lokale/Remote-Baumbindung

| PR | Lokaler auditierter Commit | Git-Baum (Integrator gegen Remote geprüft) |
|---|---|---|
| #715 | `515afc28af11b9befeb5a61f9c44411d423635a9` | `735a663d522e16d56e979c78a908e9f3baa2f031` |
| #716 | `1d71e4dfe4a801157bcc73a0a40f7e1bac90d008` | `13445d2b227e561c06bcada82eb6584fe3bbb6a0` |
| #717 | `a7a4c2592731e30fe4ef1cc3b1f91cc621b89058` | `0bc0148057fd5117735c1df0d4cb46ad6005e726` |
| #718 | `884defb2baafc179f8eb2e8ec6dca06735a85917` | `120269a4eb628ec036a0479f2d923d4fac17aa37` |
| #719 | `30aa7ba6446739e5b3b30b596e255bf12bf153fd` | `7fbcba8edc5fc0df9b230b4da023b6bfac0a92ba` |

Die Inhalte der vollständigen Bäume, nicht ähnlich benannte Branches oder
aktuelle uncommittete Nachbar-Worktrees, definieren den Auditgegenstand.

## Offene Revieweinwände gegen den eingefrorenen Stand

| PR / Einwand | Unabhängige Prüfung | Befund |
|---|---|---|
| #715 Lichtfarbe | `FilamentAvatarRenderer.kt:263` verwirft Hostaccent bei offenem Fly-in mit null-Accent | Im eingefrorenen Stand weiterhin vorhanden; nicht in dieser Spur repariert |
| #715 frühes Ergebnis des wartenden Auftrags | `JarvisShellView.kt:350` verwirft während return; `:335` setzt deferred result zurück | Im eingefrorenen Stand weiterhin vorhanden |
| #715 Preview-Quellbindung | Manifest helper `a483d275…`, tatsächliche Helperdatei `7e8a1667…` | PNG-only-Check benötigt eigene korrekte Bindung; keine aktuelle Previewbehauptung daraus ableiten |
| #715 Node-Testanzahl | Handoff 20, QA.md 18 für die genannten fünf Dateien | Unterschiedliche/unklare Belegzählung, nicht zusammenrechnen |
| #716 relative Kontrastreferenz | Reale Ctx3Context-Kompilierung: „Nicht die zweite, sondern die andere“ ergibt s2 | Reproduzierter Fehler, verworfene Option wird gewählt |
| #716 negatives Prädikat | Reale Ctx3Context-Kompilierung: „warum startet die zweite nicht?“ ergibt Clarify | Reproduzierter Verlust des expliziten Bezugs; Großschreibung „Warum“ liefert vorher Abstain wegen unbekannter Entität |
| #717 specialized strategy überschrieben | `CognitiveBudgetController.kt:40` verarbeitet requestedMode vor ausgewählter TOOL_PLAN/RESEARCH_BACKED-Strategie | Quellbefund weiter vorhanden; keine aus diesem Audit behauptete JUnit-Messung |
| #718 / #719 | Im Integrator-Snapshot keine offenen Inline-Threads | Keine Aussage über Fehlerfreiheit |

Die zusätzliche ausführbare Ctx-Diagnose hat drei Assertions; sie bestätigt
Fehlerzustände und ist kein grüner Qualitätsnachweis. Der erste Lauf erwartete
bei großgeschriebenem „Warum“ wie der Reviewtext Clarify und scheiterte; der
vollständige kurze Fehleroutput bleibt als `review-probe-initial.txt` erhalten.
Die korrigierte Diagnose unterscheidet diesen weiteren Rückhaltepfad ausdrücklich.
Kein Tuning des Produktcodes und kein zurückgehaltener Fehlversuch.

Reproduktion der zweiten Diagnose auf #716: `CtxReviewProbe.kt` zusammen mit
`Ctx3Context.kt` aus genau dem oben genannten #716-Baum mittels `kotlinc
-include-runtime -d /tmp/omega-ctx-review.jar` kompilieren und mit
`java -jar /tmp/omega-ctx-review.jar` ausführen. Source-Hash liegt unter
`review-source-sha256.txt`. Die Ctx3Context-Datei wird nicht in den Prototyp kopiert.

## Übergabe

Eigener Branch: `research/omega-architecture-audit-20261110`.
Integrator: OMEGA-Hauptagent; Ziel für den geprüften Prototyp ist dessen eigener
Arbeitsbranch gegen `cinema/fluid-light-stage-20261110`, keine direkte Änderung
von #715/#706/main. Veröffentlichung dieser Diagnose übernimmt der Integrator.
Rollback dieser Auditspur: nur die neue Diagnose und ihre Evidenzdateien entfernen.
