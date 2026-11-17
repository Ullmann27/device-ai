# Continuum – autonome Forschung, 1.704 Übungsbeispiele und Wissensarchiv
Stand: 20.10.2026. Experimentbranch und Draft PR #42, kein Eingriff in Main.

## Funktionstrennung

1. **SWB1**: bestehender trainierbarer Subword-Lernkern mit 4.113 Parametern
   (Gruß-/Frage-Klassifikation und einfache Satzähnlichkeit). Eine Erweiterung
   der Gewichts**werte** ist möglich, die Architektur/Parameter**anzahl**
   bleibt unverändert.
2. **Großes öffentliches Übungsarchiv**: 1.152 neue Intent-Beispiele
   (576+576), 480 Paare (240+240), 64 Intent-Audits (32+32),
   8 semantische Audits (4+4). Insgesamt **1.704 Datensatzeinträge**
   zusätzlich zum früheren 140-Beispiel-Paket. Die 1.632 Trainingsbeispiele
   werden ohne Doppelnutzung in 12 Stufen aufgeteilt. Jede Stufe nutzt
   96 Intent- und 40 Semantik-Trainingsfälle, mit maximal 750+500 Updates.
   Audit- und Replay-Gates schützen alte und neue Holdouts sowie
   bestätigte persönliche Lektionen. Ein Abschluss wird idempotent markiert,
   auch wenn kein Kandidat akzeptiert wird. Abbrüche schreiben keinen
   Run-once-Marker und keine Modellgewichte.
3. **Öffentliche Forschung**: ausdrücklich freizugebender eigener
   JobScheduler-Dienst, Standard AUS. Er greift über HTTPS ausschließlich
   auf die vordefinierte deutschsprachige Wikipedia-API mit 48
   kuratierten Themen zu. Je Lauf eine Quelle, maximal 96 KiB und 15 s,
   Quelltext als SOURCE_OBSERVATION, nie als bestätigte Tatsache oder
   Geräteanweisung. Jede neue Revision wird nach SHA-256 unterschieden
   und kann nur nach Replay-/Holdout-Tests SWB1-Werte ändern.
   Eine unabhängige SWB1-Übungsstufe wird ebenfalls nach Möglichkeit
   durchgeführt. Nur bei Netzverbindung **und beim Laden des Gerätes**,
   auch über Mobilfunk. Android darf Ausführungen verschieben; dies ist
   keine ununterbrochene CPU- oder Netzwerkschleife.
4. **Wissensarchiv**: lokale, quellengebundene Speicherung bis
   4.096 Revisionen, je Auszug maximal 6.000 Zeichen, eigene SHA-256-ID
   für Quelle+Text, Zeitstempel, Herkunft und optionale Dokumentdaten.
   Lokale lexikalische Suche liefert Fundstellen, nicht generierte
   verifizierte Antworten. Die Begrenzung verhindert unkontrolliertes
   Füllen des Gerätespeichers.
5. **RIS Österreich**: eigener manueller Import für offizielle öffentlich
   abrufbare RIS-URLs und Textauszüge; getrennte Kategorie für
   Gesetzesnormen und Rechtsprechung. **Noch keine automatische komplette
   RIS-OGD-Synchronisierung**, kein rechtlicher Verbindlichkeitsnachweis.
   Bei jeder juristischen Schlussfolgerung müssen aktueller Stand,
   maßgeblicher Stichtag, Änderungshistorie, Fundstelle und die
   authentische Kundmachung überprüft werden.
6. **Backups**: ZIP-Export und format-/hash-validierter Import über
   Androids Storage Access Framework. Nutzer wählt den Zielordner
   (lokaler Speicher oder ein bereits angebundener Cloud-Dokumentanbieter).
   Keine versteckte Cloud-Anmeldung, kein automatisches Hochladen.
   ZIP-Import begrenzt Dateinamen, Anzahlen und Datenmenge, keine
   Pfadtransformation aus ZIP-Namen. Persönliche Quellenauszüge
   grundsätzlich nicht in öffentlichen Cloud-Ordnern ablegen.

## Benutzermenüs (erst nach frischem experimentellen APK-Build)

- `LERNEN` → `GROSSES ÜBUNGSARCHIV · NÄCHSTE STUFE`:
  eine Stufe mit expliziter Bestätigung, Fortschritt bis 12.
- `LERNEN` → `AUTONOME INTERNETFORSCHUNG · EIN/AUS`:
  Grenzen lesen, `FORSCHUNG FREIGEBEN` wählen; zum Abstellen
  `FORSCHUNG AUS`. Dadurch keine Freigabe für unbegrenzte Websuche,
  Shell-, Installations- oder Entwicklungstools.
- `LERNEN` → `WISSENSARCHIV · QUELLEN SUCHEN`:
  lokaler Suchbegriff, URLs und Auszüge im Chat.
- `LERNEN` → `RIS · AMTLICHE QUELLE ABLEGEN`:
  Quelletyp, offizieller URL, Titel und öffentlicher Textauszug
  eingeben. Dieser Vorgang ist derzeit **manuell**.
- `LERNEN` → `WISSENSARCHIV · BACKUP / WIEDERHERSTELLEN`:
  ZIP über den Android-Dateidialog exportieren/importieren.
- `LERNKERN · DETAILS` → `GEWICHTE / LABOR`: dieselben Optionen
  einschließlich importierbarer SWB1-Gewichte und dem 140er-Paket.
- `MODELLE` → `MODELL LADEN`: eigenständiges Qwen-LiteRT-LM. Der
  zuletzt beobachtete Fehler `Local generation cancelled` ist **nicht**
  durch das zusätzliche SWB1-Training behoben. `Continuum Modelltest`
  isoliert nach einem neuen APK-Build die native Textgenerierung.

## Sicherheits- und Annahmekriterien

- Jede Datenquelle bleibt mit Original-URL und Prüfsumme abrufbar.
  SOURCE_OBSERVATION wird nie OWNER_CONFIRMED.
- Unveränderte alte/auditierte Beispiele bleiben vom Training ausgeschlossen.
- Es gibt keine automatische Aktivierung neuer Qwen- oder CDX1-Gewichte.
- Kein externer bezahlter KI-API-Zugriff für Hintergrundrecherche nötig.
- Der bestehende GROQ-Mentor bleibt optional; sein Backend muss
  nicht deinstalliert oder für normale lokale Abläufe verwendet werden.
- Gradle/Android-Lint und Samsung-End-to-End-Tests sind **noch offen**.
- Android-App **nicht deinstallieren**; vorhandene Preview per Update
  ersetzen, damit lokale Qwen-Datei, Learning Core und Besitzer-KeyStore
  möglichst erhalten bleiben. Bei Signaturkonflikt **abbrechen** und
  Build-Signer untersuchen, nicht den lokalen Speicher löschen.

## Termux-Build (gleicher bestehender Experimentbranch)

```sh
cd "$HOME/device-ai" &&
test "$(git branch --show-current)" = "astra/continuum-experiment-charter-20261019" &&
test -z "$(git status --porcelain)" &&
git pull --ff-only &&
node --test .github/continuum/public-curriculum-lab.test.mjs &&
node --test .github/continuum/continuum-growth-and-archive.test.mjs &&
node --test .github/continuum/local-model-smoke.test.mjs &&
node scripts/continuum-mobile-builder.mjs doctor &&
node scripts/continuum-mobile-builder.mjs build &&
node scripts/continuum-preview-chrome-download.mjs
```

Wenn alles erfolgreich war, auf **demselben** Samsung
`http://127.0.0.1:8765/` in Chrome öffnen. Preview-APK
herunterladen und als **Update** installieren; nichts vorher löschen.
Die Builder-Pipeline prüft den Android-Build, die Signatur und
die APK-Integrität. Chrome-Download ist nur lokal erreichbar.

Nach dem Update: **erst ohne Internet** den Befehl
`Continuum Modelltest` prüfen, dann öffentliches Offline-Lernpaket
und eine Stufe des großen Korpus starten, Ausgangswerte notieren,
erst danach autonome Recherche explizit aktivieren.
