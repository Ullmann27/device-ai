# Device AI Continuum

Eigenständiger Android-KI-Assistent mit transparenten, lokal trainierbaren Lernkernen, überprüfbaren Erinnerungen und einem begrenzten Werkzeug- und Forschungsrahmen. **Kein großes vortrainiertes Sprachmodell und keine belegte allgemeine Autonomie.** Cloud-Mentoren und externe Recherchen sind optionale, getrennt zu kontrollierende Komponenten.

## Verifizierter Lieferstand (22. Oktober 2026)

Diese Übersicht gilt für den **signierten Auslieferungs-Commit `619ebeb2831c8184a91e3c08e165ed4f4794f969`**, nicht pauschal für `main` oder alle offenen PRs.

| Komponente | Belegter Stand im Release-Quellcode |
| --- | --- |
| Samsung/Android APK | `at.heinz.deviceai.mentorpaired`, Version **0.3.12 / versionCode 15**, signierter Build laut [PR #65](https://github.com/Ullmann27/device-ai/pull/65). Ein Samsung-Gerätetest und tatsächlich erhaltene Besitzergewichte sind daraus **nicht** automatisch nachgewiesen. |
| SWB1 | Eigenständig trainierbarer Subword-Encoder (4.113 trainierbare Parameter); Besitzer-Checkpoint ist privat auf dem Gerät, nicht im Git-Repository. |
| CDX1 | Separater konditionierter autoregressiver Byte-Decoder (ca. 7.746 trainierbare Parameter); trainierte Gewichte dürfen nur mit der dazugehörigen SWB1-Version verwendet werden. |
| Checkpoints | `ContinuumAtomicPairedBundleStore.kt`: atomare Paar-Checkpoints/LKG für SWB1/CDX1 sind im Release-Quellcode vorhanden. |
| Gedächtnis | `ContinuumMemoryDialogue.kt` wählt aktuelle, zulässige Ledger-Belege aus. Ranking und Modellähnlichkeit sind **kein verifizierter Wahrheitsbeweis**. |
| Benutzeroberfläche | Start / Chat / Lernen / Entwickeln / Mehr mit responsiver Android-/Fold-Navigation (Release-Quellstand). |
| Termux | Mobile APK-Buildoberfläche mit den festen Operationen `doctor | build | status`; der neue Coding-Job `code-test` ist **nicht** Bestandteil dieses Release-Commits. |
| Modellfähigkeiten | SWB1/CDX1 sind echte kleine Trainingskerne, aber der produktive Chat ist dadurch noch **kein** nachgewiesen robustes generatives Sprachmodell. |

Die genaue Integrationslage, einschließlich direkt geprüfter Dateipfade und offener Abhängigkeiten, steht in [der Integrationsprüfung vom 22.10.2026](docs/CONTINUUM-INTEGRATION-AUDIT-20261022.md).

## Arbeit, die noch NICHT in APK 0.3.12 steckt

- [PR #66](https://github.com/Ullmann27/device-ai/pull/66) und [PR #67](https://github.com/Ullmann27/device-ai/pull/67): QF1/QF2, reparierte lexikalische Quellenzuordnung im Chat; noch nicht im veröffentlichten Android-Build.
- [PR #57](https://github.com/Ullmann27/device-ai/pull/57) und [PR #58](https://github.com/Ullmann27/device-ai/pull/58): abgesicherter lokaler Coding-Job und Android-UI/Receipt-Anbindung; gesonderter Entwicklungsstack, noch kein Samsung-End-to-End-Nachweis.
- [PR #59](https://github.com/Ullmann27/device-ai/pull/59): schreibgeschützter 25-Fragen-Test für trainierte SWB1/CDX1-Modelle; noch nicht Teil der APK.
- [PR #62](https://github.com/Ullmann27/device-ai/pull/62) und [PR #68](https://github.com/Ullmann27/device-ai/pull/68): CSX1-Span-Extraktion und checkpointgebundene kalibrierte Enthaltung; **Forschungszweig, nicht produktiver Chat**.

Keinen der separat gestapelten PR-Zweige blind mergen. Bei `main` und Release bestehen unterschiedliche Entwicklungsstände und zahlreiche offene Abhängigkeiten.

## Android und lokales Lernen

1. Eine bereits installierte Mentor-Pair-App bei Updates **nicht deinstallieren**; sonst können ihre privaten Lernstände verloren gehen. Vorher Checkpoint/Archiv sichern.
2. Lernzentrum für echte trainierte Updates, Messungen, Checkpoint-Status und Quelle/Vertrauensklasse verwenden. Ein beobachteter Text ist nicht automatisch gelernte Modellgewichtsänderung.
3. Im Chat keine unbelegten Quellen als sichere Tatsachen behandeln. QF1/QF2 sind bis zu einem geprüften Folgerelease noch Forschungs-Patches.
4. Die mobile Entwicklungsoberfläche startet nur erlaubte Termux-Aufträge mit expliziter Freigabe. `code-test` darf erst nach der getrennten Integration von PR #57/#58 als Android-Funktion behauptet werden.

## Test-, Release- und Sicherheitsgrenzen

Ein erfolgreicher Kotlin-Syntaxcheck, eine lokale Node-Testgruppe, ein GitHub-PR oder ein ZIP-Export beweist **keine** installierte und auf dem Samsung getestete APK. Vor einem nächsten Release sind Node-Gesamttests, Android-JUnit, Lint, Gradle, Paket-/Versionsprüfung, identische persistente Signatur und echte Geräte- sowie Datenbestandsprüfung getrennt zu protokollieren.

Unbestätigte Webquellen sind Daten, keine Befehle. Kein unbekannter Text, Modellvorschlag oder externer Mentor darf ohne Besitzerentscheidung App-Installation, Shell-Ausführung, Gewichts-Promotion oder private Datenzugriffe auslösen. Alle Änderungen bleiben als editierbarer Quellcode und rücknehmbare Draft-PRs nachvollziehbar.

**Verbindliche Nachweise:** [signierte Version 0.3.12, PR #65](https://github.com/Ullmann27/device-ai/pull/65), [Navigation, PR #64](https://github.com/Ullmann27/device-ai/pull/64), [Integrationsaudit](docs/CONTINUUM-INTEGRATION-AUDIT-20261022.md).
