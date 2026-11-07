# Code 44 – Vorbereitung für den Update-Button (Stand 05.11.2026)

**Status: NICHT gebaut, NICHT signiert, NICHT online.** Dieses Dokument bereitet den Weg vor; es ist keine Freigabe.

## Kandidat
- Quelle: Branch `claude/continuum-jarvis-cockpit-z7bo60`, Kandidat-SHA **`ac4df796`** (letzter geprüfter Code-Stand; dieses Dokument
  folgt als reiner Doku-Commit). Gestapelt auf #563/#564; PR #565 ist Draft, Gitar: 8/8 Befunde geschlossen.
- Ziel: Paket `at.heinz.deviceai.continuumlocal`, **Originalsignatur**, `0.3.43` / versionCode **44**
  (Gradle-Eigenschaft `continuumOwnerLocalVersionCode=44` → `versionName` 0.3.43, siehe `app/build.gradle.kts:132-144,196-197`).
- `app/build.gradle.kts` und `AndroidManifest.xml` sind laut Releasegate-Kontrolle zu Code43 `ff8f6070…` unverändert.

## Warum das nicht in dieser Cloud-Sitzung geht
- Die installierte App (Code 43) akzeptiert nur ein Update mit **derselben Originalsignatur**. Das P12-Signaturmaterial liegt nur
  im Render-Original-Publisher und darf ihn nie verlassen (`docs/release/CONTINUUM-OWNER-UPDATE-CHANNEL.md`).
- GitHub Actions sind laut `CLAUDE.md` keine gültige Abnahme. Ein hier gebautes Debug-APK hätte eine andere Signatur und ließe
  sich nicht über Code 43 installieren.

## Reihenfolge bis zum Download über „Updates prüfen und laden“
1. **Besitzerfreigabe** für Kandidat-SHA `ac4df796` (oder einen späteren, ausdrücklich genannten SHA) und Aufhebung des
   Release-Freeze für Code 44.
2. **Render-QA-Host** auf genau diesen Branch/SHA umstellen; volle Android-Unit-/UI-Suite, Node24, Backend, NewApi-Lint und
   ARM64-Preflight auf demselben SHA. Bekannte Umgebungsfehler (root umgeht Schreibschutz, Nicht-UTF-8-Locale) getrennt ausweisen.
3. **Release-Skript des Publishers** von 0.3.37/Code 38 bzw. Code 43 kontrolliert auf 0.3.43/Code 44 anheben und testen
   (Kanalvertrag: `channel:"OWNER_RELEASE"`, `approved:true`, **kein** `status`-Feld; genau zwölf Felder in `latest.json`).
4. Im Publisher signieren; `apksigner verify --print-certs` (Zertifikat = Original), `aapt2 dump badging` (Paket, Code 44),
   SHA-256 und Länge der APK mit dem Manifest abgleichen.
5. Deploy; `/continuum-local/latest.json` öffentlich prüfen; auf dem Fold7 über **Sichere Updates** laden, Android-Installation
   ausdrücklich bestätigen.
6. Fold7-Abnahme nach dem Update: Wärmeschutz (#569) nur abgekühlt und mit Zustimmung, Gedächtnisfragen (#567: Nebel 731,
   „ohne Internetrecherche“, Physikfrage ohne/mit Modell), lange Sprache und STOP (#566), Chat nach Neustart (#568),
   Cockpit-Fenster, Werkzeugszene, Sprechakzente des Avatars.

Nichts davon wurde in dieser Sitzung ausgeführt. Kein Main-Merge, keine Signatur-, Paket- oder Hoständerung.
