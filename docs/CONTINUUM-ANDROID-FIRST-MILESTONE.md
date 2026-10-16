# Continuum Android-first Teamleiter – nächster Meilenstein (2026-10-16)

## Ziel

Ein Continuum-Teamleiter mit **einem gemeinsamen Aufgabenformat** statt zwei
unterschiedlichen KIs. Android ist sofort nutzbar, ein optionaler lokaler
Git/Node-Worker kann jetzt in Termux und später unverändert auf einem PC arbeiten.
Es werden weder kostenpflichtige KI-APIs noch GitHub Actions vorausgesetzt.

## In der Android-App implementiert

- `ContinuumDeviceTeamleaderScheduler` registriert bei App-Start einen
  JobScheduler-Auftrag ungefähr stündlich, batterie- und speicherschonend, ohne
  Internet- oder Ladekabelpflicht. **JobScheduler garantiert keinen exakten
  Stundenrhythmus und Android darf Prozesse stoppen.**
- `DeviceTeamleader` liest den persistenten
  `DeviceDevelopmentBacklog`, gibt abgelaufene Leases gemäß den vorhandenen
  Regeln frei, zeigt echte Bereit-/Fehlerzahlen und prüft die zugänglichen
  SWB1-/CRE1-Stände. Ein CHECK_RUNNING ist keine erledigte Reparatur.
- Im Entwicklungszentrum stehen `TEAMLEITER · STATUS` und
  `TEAMLEITER · JETZT PRÜFEN` zur Verfügung.
- Im Chat sind `Teamleiter prüfen`, `Teamleiter Status`,
  `Continuum Teamleiter prüfen` und `Continuum Teamleiter Status`
  direkte, lokale Befehle. Sie haben keine Berechtigung, Nutzerdaten an
  Dienste zu senden oder Quellcode auszuführen.
- Ein globales Prozess-Lock verhindert doppelte Läufe bei gleichzeitigem
  Android-Job und manuellem Chat-Aufruf. Die zweite Anfrage erhält
  `WORKER_BUSY`, ohne den tatsächlichen dauerhaften Status zu überschreiben.
- Der Status wird mit SHA-256-Integrität und einem begrenzten Format
  gespeichert. Beschädigte Backlogs und Modellstände werden nicht still
  durch neue Daten ersetzt.
- Der Job verwendet pro Ausführung eine eigene Abbruchvariable. Ein
  von Android gestoppter Lauf kann nicht durch den Start des nächsten Jobs
  versehentlich wieder freigegeben werden.

## Echte Entwicklungsarbeit auf demselben Android-Gerät (optional, Termux)

Auf deinem Smartphone ist Termux bereits als Werkzeug möglich. Android
isoliert jedoch die Daten der installierten Continuum-App von Termux.

1. Sorge in Termux für Node.js 20+ und Git. Wenn nicht vorhanden und du
   die kostenlosen Pakete beziehen möchtest: `pkg install nodejs git`.
2. Hole das Repository einmal in die private Termux-Dateiumgebung:
   `git clone https://github.com/Ullmann27/device-ai.git` (öffentliches
   Repository oder eigene Git-Anmeldung, falls privat).
3. `cd device-ai && git switch copilot/pr-12-working-first-vertical-slice`.
4. Prüfe die Voraussetzungen und führe eine einmalige Entwicklungsrunde aus:
   `bash scripts/continuum-teamleader-termux.sh once`.
5. Lies die echten gespeicherten Ergebnisse:
   `bash scripts/continuum-teamleader-termux.sh status`.

Das verwendet **genau** denselben `.github/continuum/teamleader-cli.mjs`,
denselben Node-Repair-Loop, denselben Kritiker, dieselben überprüfbaren
Testprofile und dieselben isolierten Git-Worktrees wie auf einem PC.
Termux führt keine Befehle aus einem ungeprüften LLM-Chattext aus.

Für einen **freiwilligen, sichtbaren** wiederholten Vordergrundbetrieb ist
`bash scripts/continuum-teamleader-termux.sh loop` verfügbar. Ohne eine
funktionierende Termux:API-Akkuabfrage wird diese Dauerschleife bewusst
verweigert; unter 26 Prozent Akku oder bei mindestens 42 Grad Celsius
überspringt sie die Runde. Die Schleife startet **keinen unsichtbaren Dienst**
und besitzt keine Umgehung von Android-Hintergrundbegrenzungen.

### Wichtig: Noch kein automatischer Android ↔ Termux ↔ PC-Abgleich

Die Android-App und Termux verwenden bislang **denselben kanonischen
Backlog-Dokumentstandard**, aber nicht denselben physischen Speicher.
Androids App-Sandbox verhindert einen heimlichen gemeinsamen Zugriff.
Bereits vorhanden sind expliziter SAF-Export/-Import des Backlogs und
ein exportierbares Operationslog im Android-Entwicklungszentrum.
Ein geprüfter Import/Export bzw. authentifizierter Rückkanal muss
zunächst diesen Stand verbinden. Bis dahin bearbeitet Termux nur seine
eigenen lokalen Aufgaben und Repository-Prüfungen.

## Was geprüft wurde

- 14/14 vorhandene source-level Android-Verkabelungsregressionen konnten
  anhand der auf GitHub gespeicherten Dateien ausgeführt werden.
- Ein falsch erwarteter `File.readLines()`-Zeilenzähler wurde mit dem
  Kotlin-Skript-Compiler reproduziert und berichtigt (zwei statt drei).
- Neuer JVM-Test: zwei parallele Android-Prüfungen überschreiben nicht
  gegenseitig ihren persistierten Status.
- Neuer Node-Test: Bash-Syntax, korrekte unveränderte Node-Agenten-Anbindung,
  keine implizite Paketinstallation und Akkuschutz in Termux.

**Noch nicht verifiziert**: vollständiger Android-APK-Build dieses Heads,
alle Kotlin-Unit-Tests und ein echter End-to-End-Test in der installierten App.
GitHub Actions beendet die aktuellen Jobs ohne ausführbare Schritte, sodass
es dafür derzeit keinen grünen CI-Nachweis gibt.

## Abnahme des nächsten Meilensteins

1. Kotlin-/Android-Kompilierung und die neuen Android-Teamleiter-Tests grün.
2. App starten, `Teamleiter prüfen` schreiben und tatsächliche Statuszahlen
   im Chat erhalten.
3. In den Android-Einstellungen einen normalen Hintergrundzyklus abwarten;
   nach einem App-Neustart den letzten Laufstatus wiedersehen.
4. Optional in Termux einen echten Node-Testzyklus ausführen und den
   lokalen Report überprüfen.
5. Anschließend erst: Sichere, nachvollziehbare Übernahme von Aufgaben und
   Testresultaten zwischen Android-App und Termux/PC.

**Keine Verwechslung:** Der Teamleiter ist heute ein begrenzter,
regelbasierter Entwicklungsworker. Selbstständige allgemeine Codegenerierung
wie Claude Opus oder ChatGPT ist damit nicht nachgewiesen.
