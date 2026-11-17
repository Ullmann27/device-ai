# Continuum Avatar und Nutzeroberfläche – Arbeitscheckpoint

Stand: 03.11.2026, Europe/Vienna. Autorisiert durch Heinz: den vorhandenen Avatar verfeinern, Bilder speichern und für Codex bereitstellen; nach Prüfung des aktuellen Codes eine holografische Nutzeroberfläche mit erhaltener Originalkugel gestalten. Bestehende Entwicklerfunktionen sollen erhalten und für spätere Nutzer nur mit echter Eigentümerberechtigung zugänglich sein.

## Exakte Prüfbasis
- Repository: `Ullmann27/device-ai`.
- PR #390, Branch `fix/continuum-code40-router-race-review-20261103`.
- Source SHA: `b4f61ae9765e26dd401db928b76fa9f233c0742a`.
- Separater Designbranch: `design/continuum-avatar-user-ui-20261103`.
- PR #378 enthält nur `docs/avatar-live/TEST_RESULTS.md`; diese Datei ist bereits in der Prüfbasis vorhanden. Sie ist kein Gesichtsrenderer.

## Inhalt dieses Arbeitszweigs
Geplant sind verfeinerter Avatar, echte PNG-Freistellung, Renderings für Telefon/Fold, lokale bedienbare Layoutvorschau, Dateimanifest und ein an die vorhandenen Kotlin-Schnittstellen gebundenes Codex-Briefing. Es wird keine zusätzliche Sprach-/Gedächtnis-/Werkzeugpipeline angelegt.

Die Nutzeransicht erhält vier Hauptziele: **Chat · Aufträge · Assistenten · Mehr**. Die Originalkugel bleibt als eigener sichtbarer Kern beim Avatar. Agenten und Bots werden im Nutzermenü unter Assistenten zusammengeführt. Der vorhandene Entwicklungsbereich bleibt die Ausgangsbasis für die spätere geschützte Expertenansicht.

## Nachgewiesene Anschlussstellen
- `AgentActivity.buildUi/buildConversationPane/navigateWorkArea/buildComposer`.
- `avatar/AvatarDirector.kt`, `avatar/AvatarContract.kt`.
- `ContinuumHologramView.kt`, `ui/ContinuumHologramRenderer.kt`.
- `ui/ContinuumHomeLayout.kt`, `ui/ContinuumTheme.kt`.
- `docs/CONTINUUM_AVATAR_CONTRACT.md`.

## Integrationsgrenzen
- Die PNGs sind Bildressourcen, kein GLB, Mesh oder Rig.
- Der aktuelle Director liefert bereits Laufzeit-/TTS-Ereignisse. Ein neuer Renderer muss dieselben Ereignisse verwenden.
- Zukünftige `VisemeCue.atMillis` zeitgerecht verarbeiten; Activity-/Fold-Neuaufbau und Sprachende berücksichtigen.
- Im überprüften Entwicklungsnavigation-Pfad ist keine personenbezogene Heinz-Berechtigung implementiert. Ein ausgeblendeter Menüpunkt erfüllt diese Anforderung nicht.
- Die Vorschau simuliert keine App-Abnahme. Native Integration, Android-Build, Zielgerät und Eigentümerberechtigung werden separat gekennzeichnet.

## Aktueller Nachweis
Repository-Quellen gelesen; Bilderstellung begonnen. Noch keine neue native Laufzeitänderung, kein neuer Android-Test, kein APK-Build, keine Signierung oder Veröffentlichung. Bestehende Entwickler-UI, Paket, Render-Publisher und Hauptzweig werden durch diesen Designcheckpoint nicht geändert.

Fortsetzung: `docs/design_targets/continuum-avatar-v2-20261103/` und das endgültige Integrationsbriefing in diesem Zweig.
