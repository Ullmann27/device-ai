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

Isolierte Kotlin/JVM-Statusspeicherprüfung (Kotlin 1.9, kopierter
Statusspeicher-Quellcode mit echten Dateizugriffen): **bestanden**.
Geprüft wurden atomarer Schreib-/Leserundlauf, Zweizeilenformat sowie
Manipulationserkennung nach einem Bitflip. Kein vollständiger Android-Build.


## Realer Entwicklungsaufgaben-Test und Worktree-Sicherheit

Ein zusätzlicher Test auf dem Entwicklungsbranch ist
`.github/continuum/teamleader-end-to-end.test.mjs`. Er erzeugt lokal ein
temporäres Git-Repository mit einem absichtlich kaputten JavaScript-Test und
prüft den kompletten Weg:

- Reproduzierter Testfehler und eigenständige Diagnose.
- Reparatur im isolierten Git-Worktree durch vorhandene Strategien.
- Unabhängig ausgeführte Tests, belegter Patch-Hash und validierte
  Analyst/Kritiker/Patcher/Tester-Übergaben.
- Persistenter Teamleiterstatus und Reparatur-Experience-Ledger.
- Kein Schreibzugriff auf die Quelldateien des ursprünglichen Checkouts.
- Ein grüner Baseline-Lauf darf keinen künstlichen Reparaturpatch erzeugen.
- Ein realer Backlog-INVESTIGATE-Auftrag wird mit überprüfbarer TEST_RUN-Evidenz
  abgeschlossen und erst anschließend als DONE geführt.

**Sicherheitsverbesserung:** Der Reparatur-Worker prüft jetzt vor jedem
Schreiben sämtliche Zielpfade, lehnt Symlinks, Symlink-Verzeichnisse,
Path-Traversal, Nullbytes und doppelte Trennzeichen ab. Erst wenn alle
Editziele gültig sind, beginnt das Schreiben. Ein unabhängiger lokaler
Node-Dateisystem-Smoke-Test mit sieben gefährlichen Eingaben war erfolgreich.
Die passende Repository-Regression liegt in repair-loop.test.mjs.

Für einen echten Integrationslauf mit Git und Node 20+:

```sh
node --test .github/continuum/teamleader-end-to-end.test.mjs
node --test .github/continuum/repair-loop.test.mjs
node --test .github/continuum/autonomous-teamleader.test.mjs
```

**Wichtig:** Die neuen Git-End-to-End-Tests sind implementiert und wurden auf
Syntax geprüft, aber auf dem GitHub-Head bislang **nicht vollständig
ausgeführt**. GitHub Actions startet weiterhin keine normalen Test-Runner.
Eine bestehende Termux- oder PC-Node-Installation kann diese Tests ohne
zusätzlichen bezahlten Service ausführen. Eine Android-APK-Freigabe lässt sich
daraus noch nicht ableiten.


## M4: Von einem expliziten Owner-Auftrag zur unabhängig geprüften Reparatur

Die lokale CLI bietet nun einen begrenzten **Owner-REPAIR**-Durchlauf mit dem
kanonischen v2-Entwicklungs-Backlog. Das ist **kein allgemeiner Codegenerator**.
Der Worker verwendet ausschließlich die bestehenden, geprüften Reparaturstrategien
und die festen Testketten aus repair-loop.mjs. Die optionale Quelle
source: owner und der separierte Status READY sind die lokale
Arbeitsvereinbarung; der Backlog-Hash ist keine Signatur und ersetzt keine
Betriebssystem-Dateiberechtigungen.

Alle Zustandsdateien und Prüfberichte müssen **außerhalb** des Git-Checkouts
liegen. Beispiel auf einem Rechner mit Node.js und Git:

    STATE="$HOME/.local/state/device-ai-continuum/owner-repairs"
    mkdir -p "$STATE"
    node .github/continuum/developer-agent.mjs backlog propose-repair \
      --file "$STATE/development-backlog.json" \
      --id repair-owner-one --chain node --title "Node-Regression beheben"
    node .github/continuum/developer-agent.mjs backlog show \
      --file "$STATE/development-backlog.json"
    node .github/continuum/developer-agent.mjs backlog accept \
      --file "$STATE/development-backlog.json" --id repair-owner-one
    node .github/continuum/developer-agent.mjs owner-repair \
      --file "$STATE/development-backlog.json" \
      --out "$STATE/reports" --id repair-owner-one --minutes 20

Die CLI pinnt bei der Auftragserfassung den vollständigen aktuellen Git-HEAD.
Vor der Ausführung müssen Checkout und SHA noch genau dazu passen; ein
verschmutzter Checkout wird nicht bearbeitet. Ein erfolgreicher Durchlauf
setzt voraus, dass die Basis-Tests den Defekt tatsächlich reproduzieren,
die vorhandene Repair-Pipeline einen überprüften Patch erzeugt und ein
**zweiter unabhängiger Git-Worktree** denselben Patch anwenden und die feste
Testkette erfolgreich mit realen Testfallzahlen ausführen kann. Die Patch-Bytes
werden gegen den SHA-256 im Repair-Bericht geprüft. Erst danach wird der
Auftrag über die vorhandenen submit- und complete-Operationen als DONE
erfasst. Ein grüner Ausgangszustand führt **nicht** zu einem fiktiven Patch.
Automatisch erzeugte Reparaturvorhaben überlagern keine source: owner-Aufträge.

Fehlgeschlagene Versuche bleiben als FAILED und in eigenständigen
Verzeichnissen dokumentiert; bestehende Backlog-Leases und der
Reparatur-Ledger werden wiederverwendet. Es gibt keine ungeprüfte Änderung
am Original-Checkout und keinen automatischen Merge.

Verifikation:

    node --test .github/continuum/backlog-repair-worker.test.mjs
    node --test .github/continuum/teamleader-end-to-end.test.mjs
    node --test .github/continuum/*.test.mjs

Bei der ersten Einführung ist der Code auf dem Entwicklungsbranch
implementiert; ein vollständiger lokaler Node-/Android-Baseline-Lauf auf dem
jeweils neuesten Commit muss **separat** nachgewiesen werden. Automatisch rot
markierte GitHub-Runs ohne ausgeführte Job-Schritte sind hierfür kein Ersatz.
