# Continuum M4 Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Native autonome Ausführung ist durch den Besitzerauftrag vorgegeben.

**Goal:** Reale M4-APK und Regression mit wiederaufnehmbaren, unabhängig geprüften Projektbelegen.

**Architecture:** Bestehender Termux-Generator, gemeinsame Build-Lease, CCC1-Verifier und Android-Rückkanal.
Ein strikt versionierter begrenzter Projektplan erweitert die deterministischen Rezepte; Android bleibt primär.

**Tech Stack:** Node.js, Kotlin/JUnit, Gradle 8.11.1, AGP 8.10.0, Kotlin 2.4.0, Android SDK 36.

**Spec:** `docs/superpowers/specs/2026-10-23-continuum-m4-verification-design.md`

## Global Constraints

- Nur `Ullmann27/device-ai`, isolierter Sol-Branch; keine bestehenden Branches überschreiben.
- Keine Besitzer-APK, Main-Merges, Installation, privaten Schlüssel oder Gewichtsänderungen.
- Android-Termux ist Standard; Host-Belege sind gesondert beschriftet.
- Ein CCC1-Verifier und eine Reparaturpipeline; keine Shell aus Modelltext.
- SDK-Compile, Gradle-Tests, APK und Gerät strikt unterscheiden.

## Review Focus

- Zusätzliche Gradle- oder Kotlin-Dateien verändern die geprüfte Vorlage unbemerkt.
- Cache-/APK-Symlinks oder Austausch der Prüfkopie nach dem Signer erlauben falsche Belege.
- Alte Testreports/APKs oder verlorene Callbacks werden als neuer Erfolg gewertet.
- Host-Toolchain wird als Termux/ARM64 ausgewiesen; Projektplan umgeht Rechte-/Pfadgrenzen.
- Benutzerabbruch, fehlende Toolchain oder Ressourcenmangel lässt Lease/Stage/Status inkonsistent zurück.

## Task 1: Baseline, Toolchain und reale Fehler

Files: `scripts/continuum-owned-app*`, bestehende fehlschlagende Regressionstests, Sandbox-Runner.
Interfaces: bestehende `createOwnedApp`, `verifyOwnedApp`, `buildOwnedApp` bleiben standardmäßig kompatibel.

- [x] M4-Baseline: `env -u NODE_TEST_CONTEXT node --test scripts/continuum-owned-app.test.mjs` → 29/29.
- [ ] Volle Node-/Backend-/Kotlin-Baseline protokollieren und jeden Fehler klassifizieren.
- [x] Zusätzliche Quelle/Buildskript als Negativtest ausgeführt: zunächst 7/8 rot, Größen-Leseprobe zusätzlich rot.
- [x] Kleinste Ursachenreparaturen; Node 1049/1049 grün, 0 übersprungen; Backend 60/60; Commit/Push.

## Task 2: Versionierter Projektplan und unabhängig geprüfte Buildbelege

Files: neues `scripts/continuum-project-plan.mjs`, bestehender Generator, `.github/continuum/ccc1-verifier.mjs`,
jeweilige Unit-/Negativtests. Interfaces: `validateProjectPlan(plan)`, feste Plan-IDs; Verifier liefert
`CONFIRMED` ausschließlich bei Plan-/Quell-/APK-/Testbindung, sonst `INCONCLUSIVE`.

- [ ] Failing Tests für ungültige Pläne, fremde Pakete/Rechte, Quellenzusätze, alte Reports und manipulierte APK.
- [ ] Planvalidierung und zweites festes Rezept; kein beliebiger Quellcode.
- [ ] Explizites Host-SDK-Profil, Offline-Gradle, echte JUnit-/Lint-Belege und persistiertes lesendes `status`.
- [ ] CCC1-Verifier-Erweiterung mit echten Dateihashes und Mutationsfällen ausführen.
- [ ] Zwei Rezepte generieren und mindestens ein echtes APK unabhängig prüfen; Commit/Push.

## Task 3: Android-Verbindung und abschließende Übergabe

Files: `ContinuumMobileBuildPlan.kt`, `ContinuumMobileResultPolicy.kt`, Builder-Activity/Receiver und Kotlin-Tests;
`docs/CONTINUUM-SOL-TO-GPT6-HANDOFF-20261023.md`, Belege und Experimentregister.

- [ ] Kotlin-Tests für Planwahl, Callback-Zuordnung, historischen Status und sichere Fehler zuerst ausführen.
- [ ] Feste Android-Aktionen mit bestehender Termux-Route verbinden; keine zweite Workerarchitektur.
- [ ] Node, Backend, Kotlin, verfügbare Android-Gradle/Lint/UI-Prüfungen wirklich ausführen und dokumentieren.
- [ ] Separater Review des Gesamt-Diffs; Befunde beheben; Handoff, Rollback, Draft-PR, letzter Push und Remoteprüfung.

## Fortschritt / Entscheidungen

- Start `b842d471…`; M4 29/29, Backend 60/60 am 23.10.2026 ausgeführt.
- Erste volle Node-Baseline: 1041 Tests, 1025 bestanden, 10 Fehler, 6 übersprungen (Kotlin fehlt).
  Fünf veraltete Quellstrukturtests und fünf Sandbox-Tests werden untersucht; keine neuen Funktionen vor deren Klärung.
- Offizielle SDK-/Gradle-Downloads sind hier erreichbar; echte Android-Tests haben Vorrang vor Stub-Compiles.

- Task 1 Zwischensicherung: Zusatzquellen, Cache-Symlinks, übergroße Quellen und veränderte APK-Prüfkopien blockieren.
  Sandbox-Prozessbudget wird erst nach dem User-Namespace gesetzt; CPU/RAM/Timeout gelten weiterhin ab Start.
  Fünf veraltete statische Tests an vorhandenes M4-Bestätigungsrouting angepasst. SDK-Gradle-Prüfung läuft separat.
