# Fold7-Abnahme JARVIS-Cockpit – Prüfanleitung (Stand 06.11.2026, Branch `claude/continuum-jarvis-cockpit-z7bo60`)

**Status: REAL_DEVICE_NOT_RUN.** In dieser Umgebung ist kein Fold7 per ADB erreichbar. Alle bisherigen Nachweise sind
HOST (Robolectric/JVM). Diese Anleitung macht die Geräteabnahme ohne Vorwissen ausführbar.

## Voraussetzung (Blocker vor dem ersten Schritt)

1. Eine APK **aus genau diesem Branch-Stand** (Commit-SHA notieren). Laut `CLAUDE.md` nur über den vorhandenen
   Render-Android-QA-Host bauen und prüfen; GitHub Actions werden nicht als Abnahme gewertet. Der QA-Dienst steht
   derzeit auf einem älteren Branch und muss kontrolliert umgestellt werden. **Keine Signatur-/Paketänderung,
   keine Veröffentlichung.** Ein Release braucht eine gesonderte Besitzerfreigabe.
2. Ein geladenes lokales Modell (Modellverwaltung → Status BEREIT), eine echte PDF mit Textebene, Mikrofon- und
   (optional) Kamerafreigabe.
3. Optional für Messwerte: USB-Debugging und ein Rechner mit `adb`.

## Ablauf – jeweils Ergebnis eintragen (PASS / FAIL / Beobachtung, mit Uhrzeit)

| # | Aktion auf dem Fold7 | Erwartung (nur echte Zustände) | Ergebnis |
|---|---|---|---|
| 1 | Innen, quer. App öffnen. | Original-Filament-Kopf und Originalkugel bewegen sich ruhig; kein Text über Gesicht/Kugel | |
| 2 | Sagen: „JARVIS, was haben wir gestern bearbeitet?“ | Antwort aus dem Arbeitsverlauf mit Uhrzeit und Stand, Herkunft „Erinnerung, kein Modelllernen“; Kugel leuchtet nur bei echten Treffern | |
| 3 | „Öffne die letzte PDF und erstelle mir einen Entwurf.“ | Richtige Datei (oder Rückfrage/Auswahl), Seite fliegt auf der Lichtbahn ein, Deck räumt, Fenster entsteht, Kopf/Augen wenden sich zum Fenster, Kamera lehnt sich | |
| 4 | Während der Entwurf läuft | Statusleiste im Fensterkopf „Auftrag angenommen · Bearbeitung läuft“ (je einmal), Gesicht/Kugel frei, Augen fahren Zeilen ab, ENTWURF ERSTELLEN gesperrt | |
| 5 | Dazwischen etwas sagen (Unterbrechung) | JARVIS wendet sich zu; danach zurück zum Lesen, solange der Entwurf läuft | |
| 6 | Ergebnis | Entwurf entfaltet sich aus dem Dokumentfenster, Quellenkarte (1. Seite), gesprochene Antwort mit Mundbewegung, danach Cockpit vollständig zurück | |
| 7 | Neuer Entwurf, dann STOP | Sofort: Fenster weg, Blick frei, Chat „Entwurf mit STOP abgebrochen – nichts gespeichert.“, START bleibt; „Wo waren wir heute?“ zeigt „abgebrochen“ | |
| 8 | Während eines Entwurfs App wischen/beenden, neu öffnen | Verlauf „unterbrochen (App wurde beendet)“; „Aktueller Ablauf“: „Offen · …: Entwurf neu starten“ | |
| 9 | Gewählte PDF im Dateimanager verschieben, dann „Nimm die letzte PDF …“ | Datei wird genannt, nichts verändert, Auswahl öffnet sich, Cockpit bleibt | |
| 10 | „Öffne den Kalender“ (Werkzeugfreigabe) | Echtes Kalendersymbol im Kristall auf der Lichtbahn | |
| 11 | Falten innen ↔ außen und Drehen mit offenem Fenster | Fenster plant neu (nie falsche Position), Blick folgt | |
| 12 | Außendisplay, Einstellungen → Schriftgröße maximal (200 %) | Alle Fensteraktionen ganz sichtbar/tippbar; Titel ggf. einzeilig; Statusleiste weicht, statt zu verdecken | |
| 13 | TalkBack an, Schritte 3–4 | Fenstertitel als Überschrift, Statusleiste wird höflich angesagt, SCHLIESSEN erreichbar | |
| 14 | 10 min Dauerbetrieb mit Kopf | Keine sichtbaren Ruckler; bei Wärme reduzierte Blüte statt Absturz | |

## Messbefehle (optional, mit adb)

```
adb shell dumpsys gfxinfo at.heinz.deviceai.continuumlocal reset
# … Schritte 3–6 ausführen …
adb shell dumpsys gfxinfo at.heinz.deviceai.continuumlocal framestats > gfx.txt
adb shell dumpsys meminfo at.heinz.deviceai.continuumlocal > mem.txt
adb shell dumpsys thermalservice > thermal.txt
adb logcat -d --pid=$(adb shell pidof at.heinz.deviceai.continuumlocal) > log.txt
```

Bei den Messwerten keine Temperaturen schätzen: nur übernehmen, was `thermalservice` meldet.

## Was danach in die Übergabe gehört

Commit-SHA der getesteten APK, Ergebnis je Zeile, Video/Screenshots (Gesicht frei, Statusleiste, Kristall,
Rückkehr), gfx/meminfo/thermal-Dateien und alle Abweichungen mit Uhrzeit. Ohne diese Belege bleibt jede Zeile
REAL_DEVICE_NOT_RUN.
