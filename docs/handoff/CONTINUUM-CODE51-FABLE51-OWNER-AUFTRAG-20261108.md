# Code 51+ — nach signiertem Code 50: Besitzer-Kanon und Fable-5.1-Hochmodus-Handoff

**Quelle:** Echte Besitzerabnahme vom 08.11.2026 am Samsung Galaxy Z Fold7, signierte Code50-APK (0.3.49). **Status: dokumentierter Auftrag, keine reparierte APK.**

## Sofort lesen
- Verbindliche Projektvision (dauerhaft): https://github.com/Ullmann27/device-ai/issues/642
- **Direkt kopierbarer Ausführungsauftrag für Fable 5.1 High / Opus 5.5:** https://github.com/Ullmann27/device-ai/issues/643
- P0/P1 offene Gerätefehler: https://github.com/Ullmann27/device-ai/issues/641 ; https://github.com/Ullmann27/device-ai/issues/639 ; https://github.com/Ullmann27/device-ai/issues/640 ; https://github.com/Ullmann27/device-ai/issues/638
- Originalsignatur für künftig weniger manuelle Freigaben: https://github.com/Ullmann27/device-ai/issues/637
- Forschungsgrundlage: https://github.com/Ullmann27/device-ai/pull/574 (Fable), https://github.com/Ullmann27/device-ai/pull/614 (Hollywood-FUI), https://github.com/Ullmann27/device-ai/pull/635 (ARGON4).
- Source-frozen Code 50: SHA 473f79727e4b52509cc7d1af71e59b80ab9b6be5; GitHub Actions originalsignierter Release 2026-11-08.

## Vom Eigentümer bestätigtes IST (keine Generalisierung)
App startet. Der **Original-3D-Kopf** und die Continuum-Energiekugel werden gezeigt, der Kopf bewegt/blinzelt, Arbeitsfenster öffnen/drehen sich, Blick bewegt sich zur Workscene. Klares **ROHFASSUNGSURTEIL**, visuelles SOLL nicht erreicht. Manuell öffnen sich Gedächtniszentrum und „Logik und Planung“, aber „Jarvis öffne ...“ leitet in falsches App-/Dateirouting. Zwei bestätigte Gedächtnisfakten werden richtig abgerufen, eine Wiedergabe enthält Zusatzinstruktion. Gemma4-Ladestatus und ein 21.167-ms-Direkttest wurden auf dem Fold7 angezeigt. Der Gesprächsmodus ist inzwischen nach wiederholten „Spracherkenner belegt“-Meldungen unzuverlässig oder zeitweise unbenutzbar. Zusätzlich „Das lokale Sprachmodell ist gerade nicht geladen“ und „Unvollständige Antwort zurückgehalten: Die lokale Qualitätsprüfung wurde nicht bestanden“; Fehlerursachen **nicht** bewiesen. 20 gespeicherte Bot-Profile, aber **keine** nachgewiesene reale Bot-Worker-Ausführung.

## Ausführungsreihenfolge
1. **P0 Speech/Runtime-Stabilität**: ASR-BUSY, Hand-off, langer Gesprächslauf, TTS/Voice, Modell-Status, Quality-Gate, thermische Last; RED→GREEN mit 10–20 Turns und simulierten Fehlern. Kein Modellwechsel aus Stilabweichung behaupten.
2. **P1 Intent und Web aus Avatar/Chat**: natürliche de-AT-/Tippfehlerbefehle auf echte interne Screens; echte öffentlich belegte Recherche im vorhandenen Toolsystem unter OFF/ASK/ALWAYS_ON (keine private Datenfreigabe und kein neuer Internet-Provider).
3. **P1 kognitive Leistungsfähigkeit**: Langzeitgedächtnis, Konflikterkennung, Rollen/Pronomen, Schlussfolgerungen aus langen Geschichten, zeitlich gestaffelte Korrekturen, Gegenfaktik; unabhängige Holdouts.
4. **P1 filmische Referenztreue**: vorhandener Original-3D-Avatar und Continuum-Kugel, Hollywood-FUI-5-Phasen-Materialisierung, tatsächliche Arbeitswelten, dynamische Energieringe, Glas/Partikel/Depth, responsive Fold7-Grafik, **keine** grauen Dialoge im Endziel. Vergleich anhand echter Referenzbilder: docs/design-screenshots/ref5-fold7-offen.png, jarvis-master-20261104.jpg, phone-einflug-250ms.png und den drei vom Besitzer im Chat gezeigten Bildern **nur wenn tatsächlich verfügbar**.
5. **P1 echte Agenten**: Ein ausgeführter, durch Tests belegter, unterbrechbarer Werkzeugsatz, nicht 20 fiktiv „aktive“ Bots; nutzerfreundlicher Consumer-Modus zusätzlich zum Entwickler-Cockpit.
6. **P2 lange Forschung**: ARGON-4-Kreislauf, lokales Compute ohne Datacenter-Zwang für geeignete Workloads, bis zu 16 Mio eigene trainierbare Parameter in kleinen getrennten Experimenten; Sicherheitsspezialist, echte Negativkontrollen/Leistung/Joule/Metriken.

## Prüfsystem und Freigaben
Für jeden Slice: unabhängiger RED-Fall, geprüfter Patch, GREEN in Node/JVM/Robolectric/Android-Gradle, **sofern tatsächlich verfügbar**; negatives Routing/Web-/Privacy-Gate. Für Performance/Animation echte Geräteclips und Frame-/Temperaturdaten, keine erfundenen Prozentanzeigen. Erstelle isolierte Draft-PRs und Handoff SHA/Testausgaben; weder main noch fremde aktive Branches automatisch überschreiben/mergen. Original-P12-Secrets bleiben unangetastet. Nur kostenbewusst nötige Modelle verwenden. Fable-5.1-High-Task ist hier **dokumentiert**, nicht extern durch diesen Commit gestartet.

## Nächster Entwickler
Lies zuerst #643 und #642 und nenne exakten HEAD; fange bei #641 an. Der Eigentümer verlangt echte Reparaturen/Experimente, nicht nur Statusprosa.
