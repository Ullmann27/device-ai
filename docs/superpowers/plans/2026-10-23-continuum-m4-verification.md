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
- [x] Volle Node-/Backend-/Kotlin-Baseline protokollieren und jeden Fehler klassifizieren.
- [x] Zusätzliche Quelle/Buildskript als Negativtest ausgeführt: zunächst 7/8 rot, Größen-Leseprobe zusätzlich rot.
- [x] Kleinste Ursachenreparaturen; Node 1049/1049 grün, 0 übersprungen; Backend 60/60; Commit/Push.

## Task 2: Versionierter Projektplan und unabhängig geprüfte Buildbelege

Files: neues `scripts/continuum-project-plan.mjs`, bestehender Generator, `.github/continuum/ccc1-verifier.mjs`,
jeweilige Unit-/Negativtests. Interfaces: `validateProjectPlan(plan)`, feste Plan-IDs; Verifier liefert
`CONFIRMED` ausschließlich bei Plan-/Quell-/APK-/Testbindung, sonst `INCONCLUSIVE`.

- [x] Failing Tests für ungültige Pläne, fremde Pakete/Rechte, Quellenzusätze, alte Reports und manipulierte APK.
- [x] Planvalidierung und zweites festes Rezept; kein beliebiger Quellcode.
- [x] Explizites Host-SDK-Profil, Offline-Gradle, echte JUnit-/Lint-Belege und persistiertes lesendes `status`.
- [x] CCC1-Verifier-Erweiterung mit echten Dateihashes und Mutationsfällen ausführen.
- [x] Zwei Rezepte generieren und mindestens ein echtes APK unabhängig prüfen; Commit/Push.

## Task 3: Android-Verbindung und abschließende Übergabe

Files: `ContinuumMobileBuildPlan.kt`, `ContinuumMobileResultPolicy.kt`, Builder-Activity/Receiver und Kotlin-Tests;
`docs/CONTINUUM-SOL-TO-GPT6-HANDOFF-20261023.md`, Belege und Experimentregister.

- [x] Kotlin-Tests für Planwahl, Callback-Zuordnung, historischen Status und sichere Fehler zuerst ausführen.
- [x] Feste Android-Aktionen mit bestehender Termux-Route verbinden; keine zweite Workerarchitektur.
- [x] Node, Backend, Kotlin, verfügbare Android-Gradle/Lint/UI-Prüfungen wirklich ausführen und dokumentieren.
- [x] Separater Review des Gesamt-Diffs; Befunde beheben; Handoff, Rollback, Draft-PR, letzter Push und Remoteprüfung.

## Fortschritt / Entscheidungen

- Start `b842d471…`; M4 29/29, Backend 60/60 am 23.10.2026 ausgeführt.
- Erste volle Node-Baseline: 1041 Tests, 1025 bestanden, 10 Fehler, 6 übersprungen (Kotlin fehlt).
  Fünf veraltete Quellstrukturtests und fünf Sandbox-Tests werden untersucht; keine neuen Funktionen vor deren Klärung.
- Offizielle SDK-/Gradle-Downloads sind hier erreichbar; echte Android-Tests haben Vorrang vor Stub-Compiles.

- Task 1 Zwischensicherung: Zusatzquellen, Cache-Symlinks, übergroße Quellen und veränderte APK-Prüfkopien blockieren.
  Sandbox-Prozessbudget wird erst nach dem User-Namespace gesetzt; CPU/RAM/Timeout gelten weiterhin ab Start.
  Fünf veraltete statische Tests an vorhandenes M4-Bestätigungsrouting angepasst. SDK-Gradle-Prüfung läuft separat.

- Task 2 Teilabschluss: versionierter Plan-Katalog (Notizen/Zähler) mit strikter Vollidentität und Planhash;
  gemeinsamer Generator, getrennte Verzeichnisse/Pakete. CLI akzeptiert nur die beiden festen Plan-IDs.
  Expliziter Host-SDK-Doctor ersetzt keine Termux-Abnahme. Zieltests: 72/72 bestanden.
- Tatsächlicher erster SDK-Lauf der ursprünglichen M4-Vorlage: 3 Unit-Tests, Lint und assembleDebug erfolgreich.
  Die spätere finale APK muss noch durch den erweiterten kanonischen Generator samt unabhängigen Belegen laufen.
- Android-Gesamtbaseline mit echtem SDK: 861 Tests, 2 Fehler, 3 übersprungen. Reproduzierte Fehler:
  veralteter BuildPlan-Test (nur 3 statt 6 Operationen), fehlender nackter Imperativ „installier“ im Sicherheitsfilter.
  Reparatur und vollständiger Wiederholungslauf in Arbeit; SDK-/JDK-/Proxy-Blocker inzwischen aufgelöst.

- Task 2: kanonischer Generator hat Notizen UND Zähler mit echtem SDK/Gradle offline gebaut und die neuen
  CCC1-Buildbelege bestätigt. Echte Signer-/aapt2-Aufrufe auf derselben privaten APK-Prüfkopie; lesende Recovery
  bestätigte beide historischen Belege. Finale, nach dem Commit wiederholte APK-/Byteprüfung und Git-Belegsicherung folgen.
- Frische JUnit-/Lint-/Logdateien werden privat gesichert und beim Status erneut gehasht. Alte Reports/APKs
  werden vor --rerun-tasks archiviert. Zusatzquellen, sämtliche vorhandene Cache-/Output-Symlinks und Hardlinks
  blockieren. Asynchroner Abbruch/Timeout/Outputlimit; auch Gradles abgetrennte Daemon-Kinder werden über
  Linux-PID-Startidentität beendet und ihr Ende geprüft. Unbestätigte Beendigung hält die Lease zurück.
- Node-Gesamtregression nach lazy CCC1-Profil-Import: 1067/1067, keine Fehler/Skips. Separater M4-Zieltestlauf
  nach den letzten Abbruchänderungen im Log /workspace/scratch/node-m4-checkpoint.tap (wird in die Übergabe kopiert).
- SDK-Hauptregression nach den beiden ursprünglichen Kotlin-Reparaturen: BUILD SUCCESSFUL, 861 Tests,
  keine Fehler, drei Paritäts-Fixtures noch übersprungen. Fixtures anschließend mit vorhandenen Node-Programmen
  erzeugt; vollständiger Wiederholungslauf mit neuen Android-Plan-/Status-Tests läuft.
- Task 3 in Arbeit: Kotlin-Plan-Pins, Auswahl und feste CLI-Argumente, projektgebundener Rückkanal,
  historischer Projektstatus, Host/Termux-Trennung; Robolectric-UI-Test ergänzt, noch nicht als bestanden gezählt.
- SDK-/UI-Zwischenstand: 872 JUnit-Tests, 0 Fehler, 0 Skips; alle sieben damaligen Robolectric-Widgettests
  enthalten. SWB2/CRE1-Paritätsdaten anschließend erfolgreich unabhängig mit Node abgeglichen.
- Frischer Gesamt-Diff-Review fand fünf konkrete Fehler: Android-Prozessplattform, schnell verwaiste
  Daemon-Kinder, Lease-Freigabe bei ENOSPC im Log, widersprüchliche Herkunft/erfundener Signer-Fingerprint
  und durch MAIN blockierte Recovery. Alle durch neue Negativtests reproduziert und korrigiert.
  Ein weiterer Reviewfall (unlesbares proc/stat) wurde ebenfalls zuerst rot und danach grün ausgeführt.
- Prozesskontrolle nutzt für feste vertrauenswürdige Tools eine geerbte zufällige Lauf-ID plus PID-Startzeit
  auf Linux UND Android. Fehlende /proc-Prüfbarkeit liefert keinen positiven Abschluss und hält die Lease;
  die direkte ChildProcess-Instanz wird trotzdem beendet. Das ist keine OS-Sandbox gegen bösartige Tools.
- Neuer Recovery-Widgettest: 2/2 Builder-UI-Tests grün, ursprüngliche Build-Identität bleibt während
  separater lesender Statusabfrage erhalten. Abschließende SDK-/Node-Gesamtläufe laufen vor der Belegsicherung.
- Backend-Endregression erneut 60/60; gezielte Prozessregression 10/10 nach Reviewkorrekturen.

- Abschluss A/B: Implementierung c787c098… eingefroren/gepusht, volle Node1074/1074, Backend60/60, SDK-JUnit873/873 (acht UI-Fälle), vier Checks-Programme und Lint grün. Keine Skips.
- Beide getrennten APKs final offline mit dem kanonischen Generator gebaut, nach Export mit echten Signer-/aapt2-Werkzeugen und CCC1 erneut geprüft; Quellen, Artefakte und wirkliche Logs dauerhaft in docs/evidence/sol-m4-20261023.
- C/D und S1–S5 nicht implementiert oder als neue Sprachforschung ausgegeben; konkrete Anschlussabgrenzung folgt in der vollständigen Übergabe.
