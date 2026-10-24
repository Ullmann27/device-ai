# Continuum M5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Native autonome Ausführung durch Besitzer vorgegeben.

**Goal:** Reproduzierbare CTX2-Reparatur und sichere Kette Dialog → Rückfrage → validierter M4-Plan → Besitzerkontrolle.
**Architecture:** CTX3 ist ein typisierter Gate vor vorhandenem Gedächtnisretrieval; nur abschaltbarer Shadow. M5 erstellt feste M4-Pläne.
**Tech Stack:** Kotlin/JUnit/Robolectric, Node, bestehendes Android-SDK/Gradle8.11.1/JDK17.
**Spec:** docs/superpowers/specs/2026-10-24-continuum-m5-design.md

## Global Constraints
Nur eigenes Reviewbranch; keine Besitzergewichte/-schlüssel oder Haupt-APK. Originalforschung erhalten.
Keine automatische Aktion aus Interpretation, kein untrusted Shelltext. Alte CTX1-Standardvariante unverändert.

## Review Focus
- Alle Namen verneint: keine leere Listenauswahl oder späteres Wiederaufleben des verneinten Referenten.
- Quellenwechsel/Widerruf während Dialog: alte Quellen verlieren jeden Anspruch.
- Mehrere passende Aliase: Rückfrage, kein first-match-Vertrauen.
- Planrevision nach Vorschau: alte Freigabe darf keinen neuen Plan autorisieren.
- Trainingsdaten/bedingte/indirekte Befehle: keine Geräte-/Buildausführung.

## Task 1 – CTX2
- [x] Negativtest Ctx2NegationTest schreiben und vor Fix ausführen (NoSuchElementException).
- [x] generalized-only Negationssemantik implementieren, CTX1/CTX2-Suite ausführen.
- [x] Geöffnetes TEST2 als Regression ausführen; Originalberichte erhalten; commit/push.

## Task 2 – Kontextadapter
Files: learning/context/Ctx3Context.kt, Ctx3Shadow.kt, ContinuumLearningCore.kt, entsprechende Tests.
Interface: decide(input, entities) → Query(query,sourceIds) | Clarify(reason) | Abstain(reason).
- [x] Tests für Alias-/Pronomenmehrdeutigkeit, fünf Turns, Negation, Quelle/Widerruf, Größenlimits zuerst rot.
- [x] Begrenzte Entitätsmengen und Quellbindung implementieren; Shadow standardmäßig OFF, unveränderte Baselineantwort.
- [x] Befehlsfreiheit und Quellkonstanz testen; commit/push vor neuem Forschungsdurchlauf.

## Task 3 – Gespräch zu Coding-Plan
Files: ContinuumCodingConversation.kt, ContinuumM5LabActivity.kt, BuilderActivity/Manifest, JVM-/UI-Tests.
Interface: accept(text) → Preview/Clarify/Unsupported; approve(revision,hash) → ContinuumOwnedProject?
- [x] Mehrteilige Notes/Counter-Fälle, fehlende Offline-Angabe, Widerspruch/Sync/Todo, veraltete Freigabe zuerst rot.
- [x] Feste M4-Planbindung und explizite Vorschau/Freigabe; keine automatische Ausführung.
- [x] Android-Labor mit OFF/SHADOW-Anzeige und existierendem Builder verbinden; UI-Integration testen; commit/push.

## Task 4 – Messung und Gesamtabschluss
- [x] Unabhängigen TEST3 nur mit versiegeltem Hash vorregistrieren und Code einfrieren; einmalige finale Messung.
- [x] CTX1/CTX2 bekannte Regression, volle Node-/Backend-/SDK-/Lint-/UI-Suites; Fehler beheben.
- [x] Frischer Gesamt-Diff-Review, wichtige Befunde RED→GREEN; vollständige M5-Übergabe, Draft-PR und Push.

## Ledger
Baseline läuft auf unverändertem 331e2d2. TEST2 ist geöffnet und darf nur Regression heißen.
Ruling: Besitzer verlangt autonome Implementierung; keine erneuten Design-/Routinefreigaben. Kosten: begrenzter Scope muss klar bleiben.

Task 1: CTX2-Regressionssuite zuerst 4 Tests/3 Fehler, danach CTX1/CTX2 zusammen18/18 grün. SDK-Ausgang889/889 inkl8UI, Node1085/1085. Ursprünglicher TEST2-Runner scheitert bereits im unveränderten B1-Arm; deshalb keine falsche B2-Gesamtmessung behaupten. Reparierter B2 wird getrennt als bekannte TEST2-Regression gemessen. Keine Veränderung des B1-Verhaltens zur Rettung eines Forschungswerts.
Unabhängiger TEST3:24 Dialoge/198 Züge/42923 Bytes, SHA256 e5681030efe4f83a471cc2e91dae7bf3ed577f889b0fc0aabd48cf39bfd39077; Inhalt bis Freeze nicht vom Implementierer gelesen.
Task 2/3: typisierter CTX3-Gate, reine Shadow-Auswertung und M5-Labor/Builder-Übergabe SDK-kompiliert; gezielte Suite28/28 grün inklusive echter Core-Integration und Robolectric-Planfreigabe. OFF ist Standard, keine Antwortpromotion, keine Werkzeugausführung aus M5. Zwei während Prüfung gefundene Integrationsfehler behoben: falscher Konfigurationsordner und dauerhafte Sperre nach rein hypothetischer Frage. Robolectric-Dialoghandler wird tatsächlich abgearbeitet, bevor IPC-Ergebnis geprüft wird.
Geöffneter TEST2, B2-only:885 richtig/27 falsch/0 verpasst/8 unnötige Rückfragen;80 nötige Rückfragen,80 Enthaltungen. Kein Blindnachweis und keine produktive Promotion.

Nachtrag P1: Verzeichnisweite Vermischung mit zwei realen Core-Instanzen reproduziert (CLARIFY erwartet, QUERY erhalten). Neue explizite Kontext-ID plus Verzeichnisnamespace,16 Sitzungen,32 Metadatenereignisse,15min Inaktivitätsablauf, Reset/Quellenrevision. Bestehende AgentActivity-sessionId wird durch Runtime bis zum Shadow übergeben; UI-Neusitzung verwirft alten Shadow. Core-Standardinstanzen haben eigene IDs. Neue Isolation-/Parallel-/Ungültig-/Profil-/Widerrufstests. Keine Änderung der sichtbaren Baselineantworten.
P1-Activity: versioniertes Android-SavedState mit höchstens32 Eingaben,512 Zeichen/Eingabe,512 Zeichen Entwurf; reine Rekonstruktion der Anforderungen. Keine Freigabe wird gespeichert, alte Dialoge an Conversation-Instanz und Revision gebunden. Ohne OS-SavedState explizite Aufforderung zur Neueingabe.
P3: UNRESOLVED_RETRIEVAL_SCOPE wird ausdrücklich nicht als aufgelöste Referenz geloggt. Metadaten binden Modellhash und Quellenrevision; keine Rohtexte. DEV-Leistungslauf 0/10/50/128/256 Fakten,OFF/ON,kurz/lang und Widerruf vorbereitet.
Reviewkorrekturen vollständig RED→GREEN: unbekannte Namen/Negation/Aliasfokus/negative Projektanforderungen. Zusätzliche SavedState-Version wurde zuerst als veraltete gültige Vorschau reproduziert und danach abgefangen. Echte SDK-JUnit-Aggregation jetzt912 Fälle,0 Fehler/Skips; abschließendes Lint wartet noch auf Abschluss. Zwei statische Node-Quellewartungen verlangten die alte Runtime-Signatur; sie prüfen jetzt explizit Gesprächs-ID und Reset und bestehen20/20 im betroffenen Modul.
Freeze9a30327/f93473a wurde vor jeder TEST3-Ausführung ausgesetzt: Nachtragsfälle „noch nicht bauen“ und indirekter Dokumenttext invalidierten eine fertige Vorschau nicht. Isolierter Regressionstest rot; Fix außerhalb des CTX3-Algorithmus. Kein TEST3-Inhalt wurde gelesen/ausgeführt, unabhängiger Evaluator wartet. Neuer Gesamt-Freeze nach Prüfung; alter Freeze bleibt nachvollziehbar erhalten. Volle Node-Endsuite nach Verdrahtungskorrektur1085/1085,Backend60/60 grün.

Abschluss: Codefc1d996, genau eine unabhängige TEST3-Messung, FAIL/keine Promotion. Node1085/1085,Backend60/60,SDK913/913 inkl12Widgetfälle,4Checks/Lint/Parität grün. Zusätzliche Nachtragsfälle und echte JVM-Neustartprobe belegt. P2-Generalisierungsstudie und30-Sekunden-Modus bewusst offen; vollständige Anschlussarbeit in M5-Übergabe.
