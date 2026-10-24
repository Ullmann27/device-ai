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
- [ ] Negativtest Ctx2NegationTest schreiben und vor Fix ausführen (NoSuchElementException).
- [ ] generalized-only Negationssemantik implementieren, CTX1/CTX2-Suite ausführen.
- [ ] Geöffnetes TEST2 als Regression ausführen; Originalberichte erhalten; commit/push.

## Task 2 – Kontextadapter
Files: learning/context/Ctx3Context.kt, Ctx3Shadow.kt, ContinuumLearningCore.kt, entsprechende Tests.
Interface: decide(input, entities) → Query(query,sourceIds) | Clarify(reason) | Abstain(reason).
- [ ] Tests für Alias-/Pronomenmehrdeutigkeit, fünf Turns, Negation, Quelle/Widerruf, Größenlimits zuerst rot.
- [ ] Begrenzte Entitätsmengen und Quellbindung implementieren; Shadow standardmäßig OFF, unveränderte Baselineantwort.
- [ ] Befehlsfreiheit und Quellkonstanz testen; commit/push vor neuem Forschungsdurchlauf.

## Task 3 – Gespräch zu Coding-Plan
Files: ContinuumCodingConversation.kt, ContinuumM5LabActivity.kt, BuilderActivity/Manifest, JVM-/UI-Tests.
Interface: accept(text) → Preview/Clarify/Unsupported; approve(revision,hash) → ContinuumOwnedProject?
- [ ] Mehrteilige Notes/Counter-Fälle, fehlende Offline-Angabe, Widerspruch/Sync/Todo, veraltete Freigabe zuerst rot.
- [ ] Feste M4-Planbindung und explizite Vorschau/Freigabe; keine automatische Ausführung.
- [ ] Android-Labor mit OFF/SHADOW-Anzeige und existierendem Builder verbinden; UI-Integration testen; commit/push.

## Task 4 – Messung und Gesamtabschluss
- [ ] Unabhängigen TEST3 nur mit versiegeltem Hash vorregistrieren und Code einfrieren; einmalige finale Messung.
- [ ] CTX1/CTX2 bekannte Regression, volle Node-/Backend-/SDK-/Lint-/UI-Suites; Fehler beheben.
- [ ] Frischer Gesamt-Diff-Review, wichtige Befunde RED→GREEN; vollständige M5-Übergabe, Draft-PR und Push.

## Ledger
Baseline läuft auf unverändertem 331e2d2. TEST2 ist geöffnet und darf nur Regression heißen.
Ruling: Besitzer verlangt autonome Implementierung; keine erneuten Design-/Routinefreigaben. Kosten: begrenzter Scope muss klar bleiben.

Task 1: CTX2-Regressionssuite zuerst 4 Tests/3 Fehler, danach CTX1/CTX2 zusammen18/18 grün. SDK-Ausgang889/889 inkl8UI, Node1085/1085. Ursprünglicher TEST2-Runner scheitert bereits im unveränderten B1-Arm; deshalb keine falsche B2-Gesamtmessung behaupten. Reparierter B2 wird getrennt als bekannte TEST2-Regression gemessen. Keine Veränderung des B1-Verhaltens zur Rettung eines Forschungswerts.
Unabhängiger TEST3:24 Dialoge/198 Züge/42923 Bytes, SHA256 e5681030efe4f83a471cc2e91dae7bf3ed577f889b0fc0aabd48cf39bfd39077; Inhalt bis Freeze nicht vom Implementierer gelesen.
Task 2/3: typisierter CTX3-Gate, reine Shadow-Auswertung und M5-Labor/Builder-Übergabe SDK-kompiliert; gezielte Suite28/28 grün inklusive echter Core-Integration und Robolectric-Planfreigabe. OFF ist Standard, keine Antwortpromotion, keine Werkzeugausführung aus M5. Zwei während Prüfung gefundene Integrationsfehler behoben: falscher Konfigurationsordner und dauerhafte Sperre nach rein hypothetischer Frage. Robolectric-Dialoghandler wird tatsächlich abgearbeitet, bevor IPC-Ergebnis geprüft wird.
Geöffneter TEST2, B2-only:885 richtig/27 falsch/0 verpasst/8 unnötige Rückfragen;80 nötige Rückfragen,80 Enthaltungen. Kein Blindnachweis und keine produktive Promotion.
