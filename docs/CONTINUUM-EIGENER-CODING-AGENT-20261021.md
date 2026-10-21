# Device AI Continuum – eigener Coding-Agent, kein Fremdbot

Stand: 21.10.2026. Ziel: Android-first, selbst implementierter Coding-Agent. Experimentbezug: EXP-00, EXP-01, EXP-02, EXP-05, EXP-07, EXP-08, EXP-11, EXP-12. Verbindlich bleiben `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md` und Issue #41.

## Klare Trennung: Agent vs. Sprachmodell

**Continuum Coding-Agent** bezeichnet eigene, auditable Software für sprachliche Auftragsaufnahme, kanonischen Backlog, Planung, begrenzte Tool-/Git-Operationen, Kandidaten, reproduzierende Tests, Kritiker, Rücknahme und Besitzerfreigaben. Ein importiertes Sprachmodell ist lediglich eine austauschbare, untrusted **Kandidatenerzeugungs-Komponente**, weder Agent noch Betreiber der privilegierten Tools.

Keinen fremden vorgefertigten Coding-Agenten klonen oder als unser Produkt ausgeben. Dokumentation, Paper und Open-Source-Quellen dürfen Architekturentscheidungen informieren; eine Übernahme fremden Codes erfordert eigene Lizenz-, Sicherheits- und Wartungsprüfung. Existierende Fachbibliotheken wie Kotlin, Gradle, Android SDK und LiteRT-LM bleiben zulässige, explizite technische Abhängigkeiten. Ein leistungsfähiges Coding-LLM komplett ab Null auf dem Telefon zu trainieren ist kein bestehender Produktnachweis.

## Gegenwärtig nachgewiesene Bausteine (source-level)

* Deterministische Kotlin-/Node-Reparaturengine: `.github/continuum/repair-loop.mjs`, `repair-diagnostics.mjs`, `repair-strategies.mjs`, `repair-critic.mjs`; nur fehlerbehaftete Aufgaben innerhalb eines isolierten Worktree, fester Testketten und Review-Grenzen. Keine freie Neuprogrammierung.
* Modellbasierte, toolfreie vierstufige Entwickler-/Kritiker-/Testentwurf-/Richterpipeline: `app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentCoordinator.kt`. Kandidaten sind ausdrücklich nicht ausgeführt; dafür muss ein kompatibles lokales generatives Modell geladen sein.
* Spracheingabe: `AgentActivity.kt`; Diktat führt keine Befehle aus. Import über `LocalModelCatalog.kt`, derzeit LiteRT-LM/CPU-basiert. SWB1/CDX1 sind experimentelle eigene Lernkerne, kein bestätigter Kotlin-LLM-Ersatz.
* Lokaler Handy-Builder: `ContinuumMobileBuilderActivity.kt`, `ContinuumMobileBuildPlan.kt`, `scripts/continuum-mobile-builder.mjs`; echte Toolchain-/Termux-Brücke teilweise auf Nutzergerät bestätigt, vollständige neue Android-App als eigenständiger Coding-Agent-Zyklus noch nicht nachgewiesen.

## Kleine Integration dieser Entwicklungsrunde

Im vorhandenen Entwicklerdialog erscheint **EIGENEN CODING-WORKER PRÜFEN**, der auf die bereits vorhandene sichere `openMobileBuilder()`-Route verweist. Das ist eine diagnostische Verbindung zum **modellfreien** Worker und erfordert nicht erst den Import eines LLM. Neue Modellkomponenten und generative Entwürfe bleiben ein separater, freiwilliger Weg. Kein zweites Backlog, kein zusätzlicher privilegierter Toolexecutor, kein heimlicher APK-Installationspfad.

Diese Verbindung wird zusammen mit der nächsten gebündelten APK durch die bestehende Node-/Android-/Signatur-Testpipeline geprüft. **Bis dahin SOURCE_INTEGRATED, nicht DEVICE_VERIFIED.** Bestehende APK 0.3.8 enthält diese neuere Änderung nicht.

## Nächster echter vertikaler Durchstich

Ein ausdrücklich freigegebener Android-Text-/Sprachauftrag muss zuerst als kanonischer Backlog-Eintrag mit Lease erfasst werden. Der ausführende lokale Worker nimmt ausschließlich zulässige, typisierte Aufträge entgegen und überprüft den exakten Git-HEAD. Für bekannte Fehler verwendet er deterministische Strategien; für neue Aufgaben darf später ein lokales Coding-Modell Kandidaten liefern. Kein roher Modelltext als Shell, keine Git-Schreibrechte zum Produktivbranch. Der Worker legt Codeänderungen nur im isolierten Arbeitsstand an, führt unabhängige Tests aus, prüft die tatsächliche APK-/Signatur-Evidenz und stellt einen Besitzer-reviewbaren Kandidaten bereit. Nach Prozessabbruch muss die Aufgabe sicher wiederaufgenommen oder nachvollziehbar gestoppt werden.

**Gate:** ein auf dem Samsung nachgewiesener Mini-Auftrag über die bestehende App mit tatsächlichem Termux/Android-Rückkanal, Test-Exitcodes, APK-SHA, Signer, Benutzerfreigabe und geprüfter Installation. Ein erfolgreicher Cloud-Build alleine erfüllt dieses Gate nicht.

## Offline-Coding-Modell als austauschbares Werkzeug

Das aktuell fest implementierte Android-Laufzeitprofil benötigt `.litertlm`-Modelle mit CPU-Fähigkeit. Offizielle LiteRT-LM-Modellfamilien sind kein Ersatz für einen eigenen Coding-Agenten. Auswahl nur nach tatsächlichem Modellformat, Speicher-/Thermikbudget, Lizenz, SHA-256 und **blindem Kotlin-/JavaScript-Coding-Benchmark auf genau dem Samsung**. Kein stiller Download oder automatischer Ersatz der eigenen SWB1-/CDX1-Gewichte.

## Recherchebasis für lokale Coding-Modelle, keine Bot-Übernahme

Offizielle LiteRT-LM-Modellübersicht: https://developers.google.cn/edge/litert-lm/overview . Der gegenwärtige Continuum-Runtimecode verwendet `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, ausschließlich `Backend.CPU()`, und akzeptiert nach Inspector-Prüfung nur Textmodelle mit CPU-Unterstützung. Kompatibilität jeder konkret ausgewählten Datei, thermische Last, Ausgabequalität und Speichernutzung auf dem Samsung sind **noch nicht gemessen**.

Konkrete nach außen überprüfbare Kandidaten für **einen austauschbaren Sprachmodell-Adapter, nicht für den Agenten selbst**:

1. `litert-community/Qwen2.5-Coder-3B-Instruct`, Datei `Qwen2.5_Coder_3B_It.litertlm`, rund 3,43 GB. Veröffentlichter Modell-Datei-SHA-256: `78d23da074383f52f852b945b8090870e6c9dded02a842f535ee3ccb9e2874f3`; Quelle: https://huggingface.co/litert-community/Qwen2.5-Coder-3B-Instruct . **Mehr Parameter bedeutet nicht automatisch bessere gemessene Codierleistung auf dem konkreten Gerät**. Die Community-Konvertierung ist keine Google-Laufzeitgarantie.
2. `litert-community/Qwen2.5-Coder-1.5B-Instruct`, Datei `Qwen2.5-Coder-1.5B-Instruct_int4.litertlm`, etwa 1,12 GB, Apache-2.0. Quelle: https://huggingface.co/litert-community/Qwen2.5-Coder-1.5B-Instruct . Kandidat für geringere Speicher-/Temperaturbelastung; spezifische Samsung-Fold-Abnahme fehlt.

Keiner dieser extern erzeugten Gewichtssätze darf Besitzerrechte, Quellcodeänderungen, Installationen oder verifiziertes Wissen selbst autorisieren. Kein Modelldownload ohne ausdrückliche Besitzerfreigabe, keine automatische Gewichtspromotion und keine vermeintlichen Code-Benchmarks aus einem bloßen Modellstatus ableiten. Bei künftiger Auswahl: erlaubte Lizenz, Repo-/Dateiidentität, SHA-256, CPU-Inspektion auf Android und **tatsächlich ausgeführte** Kotlin/JS-Tests mit zuvor versiegelten Aufgaben; bei mehreren Kandidaten Accuracy, Latenz, Speicher und Akku gegeneinander prüfen.

## Rollback und Integrität

Diese Änderung erweitert lediglich die Entwickler-UI und fügt den Prüfvertrag hinzu. Bei Fehlern kann der einzelne UI-Commit auf dem isolierten Experimentbranch rückgängig gemacht werden; gespeicherte Aufgaben, Checkpoints, APK-Signer und `main` bleiben unangetastet. Keine neue APK veranlasst, bevor der gebündelte Build mit vollständigen Tests erfolgreich ist.
