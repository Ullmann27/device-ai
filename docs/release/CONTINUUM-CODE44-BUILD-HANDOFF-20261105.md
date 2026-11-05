# Continuum Code44 – Build-Handoff (05.11.2026)

Status: WIP / QA_RED. Noch keine Code44-APK signiert oder veröffentlicht.

## Quellen und vorhandener Kanal

- App-Kandidat: `e9b3922d2c9afadf1e5d3594f294cebea5fab3ef`, PR #565 (Draft).
- Vorheriger Live-Stand: `ff8f6070bb3a6f4dfbb6215ce3e3e269974c1d53`, Version 0.3.42 / Code43.
- Ziel: 0.3.43 / Code44, `at.heinz.deviceai.continuumlocal`.
- Original-Publisher: `srv-dbf1tr2vcj2c73ehpfog`, unveränderter Host `https://continuum-owner-v23-release-preflight.onrender.com`.
- Lauf: `dep-dbmeldon74is73cbjik0`, gestartet 2026-11-05T20:44:39Z, exakte Quelle e9b3922d.
- Publisher-Branch per Fast-forward aktualisiert, kein Force-Push; `main` und PR #565 unverändert.
- Bestehender Einstieg `scripts/render-continuum-m8-owner-release.sh` delegiert bei Code>=39 an den kontinuierlichen QA-/Release-Pfad. Version wird ausschließlich über `CONTINUUM_RELEASE_VERSION_CODE=44` gesetzt; die Originalpaket-Spur nutzt `continuumStandaloneVersionCode`, nicht `continuumOwnerLocalVersionCode`.
- Original-P12 verbleibt ausschließlich im Publisher. Keine Schlüsselabfrage oder Schlüsselübertragung.

## Echte Prüfungen

- Lokale Release-/Katalog-/Negativprüfungen auf e9b3922d: Node 24.22.1, 75 PASS / 0 FAIL / 0 SKIP.
- Render-Toolchain: JDK 17.0.20.1, Gradle 8.11.1, Node 24.22.1.
- Render-Full-Node: 11 Fehler in sieben Verdrahtungs-Vertragsdateien; keine Freigabe übergangen.
- Render-Backend: `QA_GREEN backend-lockfile-check-and-tests` am 05.11.2026 um 20:48:10Z.
- Android/Lint/unsigned Assembly beim Checkpoint noch laufend; Signierung durch QA_RED gesperrt.

## Eng begrenzte Reparatur

Die Fehler in android-avatar-wiring, app-navigation-wiring, continuous-conversation-wiring, fold7-portrait-reference, opus55-preflight-contracts, owner-cockpit-voice-original-orb und continuum-ui-routing einzeln gegen die tatsächlichen Anschlüsse prüfen. Alte Textmuster nur ersetzen, wenn das neue Verhalten und seine Runtime-Tests die ursprüngliche Invariante nachweisen. Keine Tests auslassen und keine Release-Gates abschwächen.

Nach Reparatur: lokale betroffene Tests, dann vollständiger originaler Publisherlauf auf exakt neuer SHA; erst nach grüner QA Signierung und Downloadprüfung. Bestehender Code43 bleibt bis zur erfolgreichen Veröffentlichung erhalten.

## Noch offen

- Vollständiger grüner QA-/Signierlauf des finalen SHA.
- Unabhängiger HTTPS-Download: zwölf Manifestfelder, Quelle/Code/Paket, Dateilänge, SHA-256, v2-Signatur und Originalzertifikat mittels apksigner, Paket/Version mittels aapt2.
- Reale Fold7-Installation, GPU, Audio, Temperatur und Updateknopf: NOT_RUN.
