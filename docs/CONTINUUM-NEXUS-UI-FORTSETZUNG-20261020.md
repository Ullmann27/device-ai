# Continuum Nexus – wiederaufgenommene UI-Arbeit und nächste Geräteabnahme

**Stand:** 20.10.2026, Experimentbranch `astra/continuum-experiment-charter-20261019`, Draft-PR #42.

## Herkunft und tatsächlich integrierter Zustand

Der unterbrochene Codex-Cloud-Lauf des Benutzers meldete die Teilaufgaben
„Nexus ui aktualisiert“ und „Personal knowledge aktualisiert“; zugleich
erschien „Der letzte Remote-Durchlauf ist fehlgeschlagen“.

Auf GitHub wurde aus dieser Runde zunächst nur `e58d32f2e53e5dfc4880199ec5795a6febf62da4`
mit ANSK-Transfer-Implementierung und Tests nachgewiesen. Eine separat
veröffentlichte, neue Nexus-/Wissens-Änderung dieser Runde war nicht
im gemeinsamen Experimentbranch auffindbar. **Aus dieser Beobachtung folgt
nicht, dass private Codex-Worktree-Dateien verloren sind.** Ohne Zugriff auf
den unterbrochenen Cloud-Arbeitsstand können sie hier nicht ausgelesen
oder als integriert bezeichnet werden.

Vorhanden und übernommen: `ContinuumHologramView.kt`,
`HologramAudio.kt`, `TtsHologramSignal.kt`, reale
`AgentActivity.kt`-Navigation, Termux-Builder-Routen, die bestehenden
Anmelde-/Lern-/Gedächtnis- und Android-Sicherheitsmechanismen.
Kein neuer Renderer oder konkurrierendes UI-Framework wurde eingeführt.

## Neu integriert – Anfängeroberfläche „Continuum Nexus“

- `ContinuumNexusHomeSpec.kt`: fünf stabile, deutschsprachige Ziele,
  eine klar erkennbare Voice-Hauptaktion und die vier darunterliegenden
  Aktionen „App erstellen“, „Meine Dateien & Wissen“, „KI personalisieren“
  und „Meine Aufgaben“.
- `AgentActivity.kt`: Der vorhandene 3D-Canvas-/Shader-Orb bleibt im
  Mittelpunkt. Sprachaktion öffnet den realen Android-Speech-Recognizer
  (`startVoiceInput`), nicht einen simulierten Aufnahmezustand.
- Die vier Aktionen verweisen **nur** auf bereits vorhandene Screens:
  Entwicklungsdialog, Gedächtniszentrum, personalisierendes Auswahlmenü
  (Gedächtnis/Lernen/Modelle), Entwicklungs-Backlog.
- Für ein schmaleres nutzbares Display oder große Systemschrift wird eine
  einspaltige, sonst eine zweispaltige Kachelanordnung gewählt.
  Kartenhöhe bleibt inhaltsabhängig. Der vorhandene Fold-Sidebar-Ansatz,
  IME-Inset-Schutz und ScrollView bleiben intakt.
- Vorhandene Expertenfunktionen „Chat, Forschung, Lernen, Modelle,
  Entwicklung, Speicher“ bleiben unverändert erreichbar, erscheinen
  aber zunächst nur nach expliziter Auswahl „Weitere KI-Funktionen“.
- Die vier primären Kacheln verwenden die Referenzfarben
  Cyan, Violett, Türkis und Gold. Bestehende Expertentiles
  behalten ihr Farb-/Verhaltensschema.
- Es werden keine erfundenen Testzahlen, Builds, KI-Modellzustände,
  automatische Installationen oder Dokumenttraining eingeführt.
  Die Dokumentenkachel beschreibt ausdrücklich *gespeicherte Fakten
  und Quellen*, keine noch unbewiesene PDF-RAG-Pipeline.

## Prüfungen und Grenzen

Gezielte quellgebundene V8-Prüfungen: 13/13 durchgelaufen auf dem
aktuellen GitHub-HEAD. Die Quelldateien enthalten außerdem:

- `.github/continuum/nexus-beginner-ui.test.mjs` (7 Node-Regressionen)
- `app/src/test/java/at/heinz/deviceai/ContinuumNexusHomeSpecTest.kt`
  (3 Kotlin/JUnit-Tests).

Die 13/13 V8-Prüfungen sind **kein Kotlin-Compiler-,
Android-Instrumentierungs- oder Geräte-Renderingbeweis**.
Diese Abnahmen bleiben offen.

Zu beachten: Der Android-Spracherkenner liefert Text, nicht zwingend
rawes Mikrofonsignal an den Canvas. Das bestehende Orb-Modul nutzt
synchronisierte TTS-PCM-Metadaten und statusabhängige Animationen.
Eine pulsierende Zuhören-Anzeige darf daher nicht als gemessene
Mikrofon-Wellenform vermarktet werden.

Die langfristigen Referenzansichten für geschlossenes Smartphone und
geöffnetes Fold/Tablets sind damit **ein visuelles Ziel**.
Weder Pixelparität noch vollständige persönliche PDF-Wissenssuche
oder der fertige Prompt-to-APK-Kreislauf sind hier bestätigt.

## Nächste Abnahme

1. Im sauberen Experimentbranch auf Samsung auf den veröffentlichten
   GitHub-HEAD fast-forward aktualisieren, konkurrierende Prozesse
   ausschließen.
2. Gezielt testen:
   `node --test .github/continuum/android-holographic-home.test.mjs .github/continuum/nexus-beginner-ui.test.mjs`
3. Anschließend `gradle :app:compileDebugKotlin :app:testDebugUnitTest --console=plain -Pkotlin.incremental=false`
   mit der tatsächlich unterstützten Termux-Toolchain und Build-Lease.
   Anders als ein reiner Node-Test kann dies Android-Compilerfehler
   und Gradle-Abhängigkeiten aufdecken.
4. Erst nach erfolgreicher APK-Erzeugung und ausdrücklicher
   Eigentümerentscheidung installieren. Danach Portrait, geschlossenes
   Fold, geöffnetes Fold, 150-%-Schrift, Tastatur, Screenreader,
   Sprachaktion und alle 5 Ziele gegen die zwei Referenzbilder testen.
5. Eine eventuell wiederhergestellte Codex-UI-Version anhand der
   veröffentlichten Datei-Diffs mit diesem HEAD **vergleichen**,
   niemals ungeprüft darüberlegen.

## Weitere Umsetzung

PDF-/Dokumentenimport samt sicheren Dateiberechtigungen und
quellenbelegter RAG-Suche ist ein nachfolgender Integrationsmeilenstein,
nicht Ergebnis dieses UI-Commits. Ebenso benötigt der komplette
Laien-Wunsch → signierte APK → Android-Installationsdialog eine echte
Ende-zu-Ende-Abnahme mit expliziter Freigabe.

Alle hier beschriebenen Änderungen liegen auf dem gemeinsamen
Experimentbranch. `main` wurde nicht verändert.
