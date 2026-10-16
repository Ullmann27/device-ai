# Continuum Teamleader v1 – autonomer, kostenloser Entwicklungsworker

Stand: 16. Oktober 2026. Quellcode: .github/continuum/autonomous-teamleader.mjs,
teamleader-cli.mjs, autonomous-teamleader.test.mjs.

## Was die erste Version tatsächlich tut

Der Teamleader ist ein eigenständiger, deterministischer Node-Worker, kein
fremder Chatbot und keine kostenpflichtige API. Er koordiniert bereits vorhandene
Komponenten der Continuum-Repair-Pipeline:

1. Er liest einen echten unveränderlichen Git-HEAD.
2. Er prüft jeden neuen Commit nur einmal mit der fest vorgegebenen Node-Testkette
   im isolierten Git-Worktree. Kein Rückschluss aus grünen Exitcodes ohne Testbelege.
3. Bei reproduzierbaren Fehlern verwendet er den vorhandenen Diagnostiker,
   Reparaturkandidaten, Kritiker und maximal drei Kandidatenversuche.
   Nur bei bestandenem Check entsteht ein PATCH ZUR PRÜFUNG; nie ein direkter
   Merge oder eine Änderung am produktiven Checkout.
4. Nach einer geprüften Version untersucht er strukturelle Codebefunde und
   nimmt höchstens einen sicheren, rein lesenden INVESTIGATE-Auftrag je Runde
   aus dem persistenten Entwicklungs-Backlog an.
5. Erfolgreiche und verworfene Kandidaten werden im bestehenden
   repair-ledger.json festgehalten, damit schlechte Reparaturstrategien nicht
   endlos erneut ausprobiert werden.
6. Eine separat gespeicherte Teamleader-Historie hat Schema, Integritäts-Hash,
   Größenlimit, Prozess-Sperre, begrenzte Ereignisse und Wiederanlauf nach
   unterbrochener Runde. Fehler werden NIE als Erfolge ausgegeben.

## Ausprobieren (kein Cloudzugang erforderlich)

In einem vorhandenen lokalen Git-Checkout mit Node.js 20+, Git und genug
Speicherplatz:

    node .github/continuum/teamleader-cli.mjs --once --root .
    node .github/continuum/teamleader-cli.mjs --status --root .

Ein einziges ausdrücklich gewünschtes erneutes Prüfen desselben Commits:

    node .github/continuum/teamleader-cli.mjs --once --retry --root .

Zustand und Berichte liegen standardmäßig außerhalb des Repositories unter
~/.local/state/device-ai-continuum/<Checkout-Kennung>. Es werden keine
Nutzererinnerungen, Chat-Antworten oder Zugangsdaten protokolliert.

## Stundenweise weiterarbeiten ohne GitHub Actions

Auf einem bereits vorhandenen Linux-Rechner mit systemd-User-Session
kann der Eigentümer den Scheduler nach Durchsicht einmalig installieren:

    bash scripts/install-continuum-teamleader.sh
    systemctl --user list-timers continuum-teamleader.timer
    journalctl --user -u continuum-teamleader.service

Zum Abschalten:

    systemctl --user disable --now continuum-teamleader.timer

Der Timer braucht einen laufenden Rechner und einen verfügbaren Benutzerkontext.
Für wirklich unbeaufsichtigten Dauerbetrieb nach Abmeldung ist ein explizit
aktiviertes User-Lingering oder eine vergleichbare eigene lokale Infrastruktur
notwendig. Kein Programm kann ohne laufende Hardware einen Monat lang Code
entwickeln. Android kann Hintergrundprogramme abbrechen; der hier implementierte
Node-Worker ist NICHT bereits ein ständig laufender Dienst auf dem Smartphone.

GitHub Actions scheitert derzeit an Runner-/Abrechnungsbedingungen. Dieser lokale
Weg benutzt kein GitHub Actions und braucht keine neue kostenpflichtige Plattform.
Strom und bestehende Hardware sind natürlich reale Ressourcen.

## Tests und Grenzen

    node --test .github/continuum/autonomous-teamleader.test.mjs

Die Tests umfassen Wiederholungsschutz je Commit, ausschließlich prüfbare
Patchvorschläge, persistente Historie, eingeschränkte Untersuchungen,
Abbruch-/Fehlerfälle, beschädigten Zustand, konkurrierende Läufe und
Argumentvalidierung.

Die 20-Minuten-Option begrenzt den Reparatur-Kandidatenprozess. Ein einzelner
Kompilier-/Testbefehl besitzt eigene Timeouts; das systemd-Service begrenzt den
Gesamtlauf zusätzlich auf 35 Minuten.

Die erste Version kann bekannte Fehlerklassen reparieren, aber noch keinen
beliebigen neuen Algorithmus entwickeln. Ein optionaler eigener neuronaler
Programmier-Decoder darf später Vorschläge liefern; unabhängig davon müssen
Kritiker, Rechteprüfung und fest kodierte echte Tests entscheiden.

**Noch offen:** authentifizierte Status-/Aufgaben-Synchronisierung mit der
Android-App; freie Codegenerierung; ein vollwertiges Team aus selbstständig
trainierbaren spezialisierten Agenten; verifizierter Android-End-to-End-Build.

Nächste priorisierte Ausbaustufen:
- Teamleiterstatus ist im Android-Chat implementiert; APK-Build und Gerätetest bleiben offen.
- Eigene freigegebene Entwickler-Aufgaben zwischen Android und lokaler Maschine
  synchronisieren, ohne ungeschützte Ports oder externe Kosten.
- Optional CRE1/Decoder für Planungsideen verwenden, aber keine Modellbehauptung
  als geprüfte Codeänderung behandeln.


## Android-first-Meilenstein und ehrlicher Prüfstand (16. Oktober 2026)

**Auf PR #19 im Quellcode integriert:**

- Android DeviceTeamleader liest gemeinsamen Entwicklungs-Backlog sowie echte
  SWB1-/CRE1-Zustände, führt Lease-Recovery aus und speichert einen überprüfbaren
  Status. Er behauptet keine Codekompilierung auf einem Gerät ohne Git/Gradle.
- Android JobScheduler registriert eine stündliche, persistente Prüfung ohne
  Netzwerk- oder Ladekabelpflicht. Android kann den tatsächlichen Zeitpunkt
  verschieben und laufende Jobs abbrechen.
- Prozessweiter Lock und pro Jobgeneration separater Abbruchzustand verhindern
  gleichzeitige Arbeit und verhindern Eingriffe alter Stoppanforderungen.
- Die Android-Chatbefehle "Teamleiter status" und "Teamleiter prüfen" sowie
  Entwicklung/Teamleiter-Buttons rufen die wirkliche Status- und Prüfroutine.
- Manipulierte Statusberichte werden beim nächsten Lauf nicht heimlich
  überschrieben; der Fehler ist sichtbar und die Daten bleiben zur Diagnose.
- Ein nicht reparierbarer Zustand löst keine sofortige JobScheduler-Retry-
  Dauerschleife aus. Der nächste reguläre periodische Termin bleibt möglich.
- Ein späterer lokaler PC kann denselben Teamleiter und den kanonischen
  Backlog-Export/import nutzen; Termux ist als freiwilliger Android-Node-
  Ausführungsweg beschrieben, aber nicht Teil der APK-Voraussetzungen.

**Prüfungen:** 11/11 Teamleiter-Steuerungsszenarien bestanden in einer isolierten
V8-Ausführung mit nachgebildetem Dateisystem/Treiber. Die Android-/Node-
Verkabelung wurde anhand aktueller GitHub-Quellen gesondert geprüft.
Ein lokaler Kotlin-Test bestätigte das Zeilenende-Verhalten, das zuvor einen
Unit-Testfehler verursacht hatte.

**Nicht bestätigt:** Keine vollständige Kotlin-/Gradle-Kompilierung, keine
Android-APK des neuen Heads, kein echter Worktree-Lauf auf dem Entwickler-PC
und kein Smartphone-Langzeittest. GitHub Actions startet derzeit nicht mit
normalen Runner-Testschritten. Diese Prüfungen sind für die produktive
Freigabe weiterhin erforderlich.

**Nächster Meilenstein:** APK bauen und auf dem echten Android-Smartphone
prüfen (automatischer Start, Abbruch, Neustart, Speicherpersistenz). Danach
kanonischen Backlog-Export Android zu einem lokalen PC/Termux-Worker tatsächlich
testen und den ersten realen Patch zur menschlichen Kontrolle übergeben.
