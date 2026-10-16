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

Der normale lokale Teamleader-Scheduler verarbeitet akzeptierte Owner-Aufträge
jetzt ebenfalls automatisch. Nach der oben gezeigten `backlog accept`-Operation
kann anstelle des manuellen `owner-repair`-Befehls der reguläre Worker gestartet
werden:

    node .github/continuum/teamleader-cli.mjs --once \
      --root . --state "$STATE" --budget-minutes 20

Der reguläre Worker priorisiert eine angenommene Owner-Reparatur vor der
gewöhnlichen einmaligen HEAD-Baseline und verhindert danach doppelte
Baseline-Durchläufe auf demselben SHA. Ein abgestürzter Worker kann nach
Ablauf seiner Backlog-Lease beim nächsten Zyklus erneut bearbeitet werden.
Owner-Aufträge bleiben auf den ursprünglich akzeptierten unveränderlichen
Git-Head beschränkt. Ein erfolgreicher Agentendurchlauf führt niemals
automatisch einen Merge in main aus.

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


## Reproduzierbarer Offline-Nachweis bei blockierten GitHub-Runnern

Die freiwillige lokale Prüfroutine nutzt den **exakten aktuellen Commit**
und führt die vorhandenen festen Node-Prüfprofile in einem abgetrennten
Git-Worktree aus. Sie schreibt einen Bericht mit Testfallzahlen,
Exitcodes, Git-SHA und SHA-256 des Berichts in ein Verzeichnis **außerhalb**
des Source-Checkouts. Jeder Durchlauf bekommt ein eigenes Verzeichnis.

    STATE="$HOME/.local/state/device-ai-continuum/proofs"
    node .github/continuum/m4-local-gate.mjs --root . --out "$STATE"

Die reine Node-Abnahme hat im Bericht stets scope = NODE_ONLY;
sie ist **kein** Nachweis für einen erfolgreichen Android-Build.

Ein vollständigerer Baseline-Durchlauf einschließlich Kotlin/Android-Tests
und Android-Debug-APK kann mit installiertem Android-SDK, Gradle und JDK
wie folgt gestartet werden:

    node .github/continuum/m4-local-gate.mjs --root . \
      --out "$STATE" --android --budget-minutes 90

Die Android-Tests und der Debug-APK-Build werden getrennt aufgezeichnet.
Bei erfolgreichem APK-Build wird die echte app-debug.apk **vor dem
Entfernen des temporären Worktrees** in das externe Beweisverzeichnis kopiert.
SHA-256, Dateigröße und gespeicherter Pfad gehören zum Build-Nachweis.
Ein grüner Gradle-Exit ohne erhaltene APK erfüllt die Build-Abnahme nicht.
Wenn ein Kommando fehlt oder abbricht, erhält die entsprechende Stufe
keinen erfundenen Erfolg. Fehlgeschlagene Tests ergeben
complete = false. Ein bestandener NODE_ONLY-Test bedeutet lediglich,
dass die Node-Baseline auf demselben Git-Commit grün war.

Zusätzliche Regressionstests für das Prüfwerkzeug:

    node --test .github/continuum/m4-local-gate.test.mjs

Ungeprüfte Agentenänderungen werden von diesem Werkzeug weder in den
Source-Checkout zurückgespielt noch in den produktiven Branch gemergt.


## M4.2 Forschung: begrenzte symbolische Kotlin-Code-Synthese

Die erste neue, nicht nur reparierende Code-Operation ist
kotlin-delegate-synthesizer.mjs mit proposeKotlinDelegate.
Sie erzeugt **nur Kandidaten** und verändert weder Checkout noch Backlog.
Sie wird noch **nicht automatisch** vom regulären Teamleader ausgeführt.

Ein expliziter, zuvor als READY angenommener IMPLEMENT-Auftrag mit
source: owner muss folgende flache subject-Daten enthalten:

    {
      "operation": "EXPOSE_DELEGATE",
      "file": "app/src/main/java/p/MathApi.kt",
      "className": "MathApi",
      "methodName": "squareTwice",
      "delegateName": "square",
      "parameterName": "value",
      "type": "Int",
      "depth": 2
    }

Der Operator akzeptiert ausschließlich vorhandene einfache Klassen mit einer
eindeutigen, nicht überladenen Methode fun existing(arg: Type): Type,
wobei Type aus einer begrenzten Menge (Int, Long, Boolean, String)
stammt und depth genau 1 oder 2 beträgt. Er verwendet das
vorhandene Kotlin-Lexing und den Symbolindex für Token-Spans und lässt
den bestehenden repair-critic.mjs entscheiden. Er behauptet **keine**
vollständige Kotlin-Compiler-AST- oder Typanalyse.

Die abgelehnten Fälle umfassen unangenommene oder fremde Aufträge,
mehrdeutige/überladene Methoden, ungültige Namen, nicht unterstützte
Kotlin-Klassenköpfe, Test- und Governance-Dateien und unzulässige Typen.
Das neue Verhalten ist ein bewusst eng begrenztes, deterministisches
M4.2-Experiment, kein frei generativer lokaler Entwickler.

    node --test .github/continuum/kotlin-delegate-synthesizer.test.mjs

Der Test verlangt sowohl eine *rote* Kotlin-Kompilation ohne die neue
Methode als auch eine *grüne* Kotlin-Kompilation und funktionale
Laufzeitprüfungen nach dem generierten Patch. Dafür müssen kotlinc
und Java im Pfad vorhanden sein; andernfalls ist der entsprechende
Compiler-Test im Node-Test übersprungen und kein Compiler-Nachweis erfolgt.

**Vor Freigabe als autonomer Skill zwingend:** kanonische Owner-Task-
Ausführung, isolierte Worktrees, reproduzierbarer Baseline-/Patch-Test,
unabhängiger zweiter Testlauf, JUnit-Evidenz, SHA-256 des Patches,
Governance-Verbot, Wiederaufnahmesicherheit, fehlertoleranter Backlog,
keine ungeprüfte Main-Änderung. CRE1 darf Operatoren zunächst
nur im Shadow-Modus bewerten; neuronale Gewichtsänderungen benötigen
separat eingefrorene Holdouts, unabhängige Erfolgsevidenz und
reproduzierbare Vergleichsmessungen.


## M4.2: Autonome, reviewpflichtige Node-Contract-Tests

Neben REPAIR bearbeitet der lokale Teamleiter jetzt auch ausdruecklich
angenommene Owner-Auftraege der Art TEST. Dieser erste begrenzte
Code-Synthese-Skill kann **neue** Tests mit konkreten Sollwerten
programmieren. Er ist kein allgemeiner Kotlin- oder JavaScript-Generator.

Ein TEST-Auftrag hat ein kanonisches subject mit ausschliesslich
Skalarwerten (wichtig fuer Kotlin/Node-Paritaet):

    {
      "base": "FULL_40_CHARACTER_COMMIT_SHA",
      "chain": "node",
      "operation": "ASSERT_NODE_EXPORT",
      "modulePath": ".github/continuum/math-utils.mjs",
      "exportName": "triple",
      "casesJson": "[{\"args\":[5],\"expected\":15}]"
    }

casesJson muss ein String mit maximal 300 Zeichen sein, der 1-4
Input/Output-Beispiele als kompaktes JSON kodiert. Argumente und Sollwerte
sind auf primitive JSON-Werte begrenzt. Ohne echte, vom Owner angegebene
Sollwerte entstehen keine zulaessigen Contract-Tests.

Bedienung auf einem **sauberen lokalen Git-Checkout**:

    STATE="$HOME/.local/state/device-ai-continuum/owner-repairs"
    mkdir -p "$STATE"
    node .github/continuum/developer-agent.mjs backlog propose-test \
      --file "$STATE/development-backlog.json" \
      --id owner-triple-contract --module .github/continuum/math-utils.mjs \
      --export triple --cases-json '[{"args":[5],"expected":15}]'
    node .github/continuum/developer-agent.mjs backlog show \
      --file "$STATE/development-backlog.json"
    node .github/continuum/developer-agent.mjs backlog accept \
      --file "$STATE/development-backlog.json" --id owner-triple-contract
    node .github/continuum/teamleader-cli.mjs --once \
      --root . --state "$STATE" --budget-minutes 20

Der Beispielexport muss im eigenen Checkout tatsaechlich existieren; der
Befehl demonstriert nur die Syntax und garantiert keinen fertigen Patch.

Alternativ ist eine direkte, jedoch weiterhin explizit autorisierte
Ausfuehrung moeglich:

    node .github/continuum/developer-agent.mjs owner-test \
      --file "$STATE/development-backlog.json" \
      --out "$STATE/reports/owner-test" \
      --id owner-triple-contract --minutes 20

Sicherheits-/Qualitaetskette:

1. Unveraenderlicher Git-HEAD, sauberes Original, Owner-Task in READY.
2. Vorher gruene reale Node-Baseline mit mindestens einem ausgefuehrten Test.
3. Neue Contract-Testdatei ausschliesslich im ersten Worktree, Quelle byte-
   genau aus einer festen, von Repair-Critic ebenfalls validierten Vorlage.
4. Syntaxpruefung, reale Node-Tests, **streng mehr ausgefuehrte Tests**
   als in der Baseline, keine zusaetzlichen Skips.
5. Patch aus neuer Datei mit git add -N + git diff --binary exportieren,
   SHA-256 speichern und auf einem zweiten unabhaengigen Worktree mit
   git apply --check und demselben festen Node-Testprofil pruefen.
6. Nur beim zweiten erfolgreichen Beleg: Backlog submit und complete
   atomar zu DONE; Ergebnis VERIFIED_PATCH_PENDING_REVIEW, niemals Auto-Merge.
7. Bei Fehlern: FAILED-Versuch, eigener Diagnosebericht, kein DONE;
   Retry nur innerhalb der Backlog-Versuchsgrenze und Lease-Recovery.

Die Worktrees sind Git-Isolation, **keine Betriebssystem-Sandbox**.
Vor dem produktiven PC-Dauerbetrieb muss die Runner-Hardware gegen
boesartige Testprozesse zusaetzlich isoliert werden.

Regressionstests im Repository:

    node --test .github/continuum/contract-test-template.test.mjs
    node --test .github/continuum/node-contract-synthesizer.test.mjs
    node --test .github/continuum/owner-contract-worker.test.mjs
    node --test .github/continuum/autonomous-teamleader.test.mjs

Der Git-Fixture-E2E-Test prueft sowohl einen echten erfolgreichen
TEST-Auftrag als auch falsch vorgegebene Sollwerte, bereits rote
Baselines, nicht angenommene Auftraege und Symlink-Ausweichversuche.
Erst ein tatsaechlich ausgefuehrter Testbericht ist ein Meilenstein-
Abnahmebeleg. Dateien allein sind keine bestandenen Tests.


## M4.3: Kotlin IMPLEMENT – isolierter Owner-Patch mit RED/GREEN-Oracle

Der vorhandene kotlin-delegate-synthesizer.mjs kann eine eng begrenzte
Methode als Delegation auf eine vorhandene Methode erzeugen. Die neue
kotlin-delegate-acceptance.mjs kompiliert den Zustand VORHER und
NACHHER mit lokalem kotlinc und prueft ausdruecklich vorgegebene
Owner-Sollwerte durch einen echten JVM-Prozess. Ein beliebig roter
Ausgangscode gilt nicht als Beweis: der Compilerfehler muss auf die
beabsichtigte fehlende Methode verweisen. Ein fachlich falscher
Sollwert erzeugt einen roten Lauf, auch wenn Kotlin erfolgreich baut.

owner-kotlin-preview.mjs akzeptiert nur im persistierten v2-Backlog
angenommene IMPLEMENT-Auftraege (source: owner, state: READY,
Operation EXPOSE_DELEGATE, exakt gepinnter Git-HEAD) und fuehrt
folgende Schritte aus: Codegenerierung und RED/GREEN-Oracle im ersten
isolierten Worktree, Ausgabe eines SHA-256-gehashten Git-Patches und
zweite Anwendung des Patches in einem separaten Worktree mit
unabhaengigem RED/GREEN-Oracle. Die Originaldateien bleiben unberuehrt.

Beispiel: Einfache, eigenstaendig kompilierbare Kotlin-Klasse
app/src/main/java/p/MathApi.kt, die
fun square(n: Int): Int = n * n bereits deklariert:

    STATE="$HOME/.local/state/device-ai-continuum/kotlin-implement"
    mkdir -p "$STATE"
    node .github/continuum/developer-agent.mjs backlog propose-implement \
      --file "$STATE/development-backlog.json" \
      --id owner-square-twice --path app/src/main/java/p/MathApi.kt \
      --class-name MathApi --method-name squareTwice \
      --delegate-name square --parameter-name value \
      --type Int --depth 2 \
      --cases-json '[{"input":2,"expected":16},{"input":3,"expected":81}]'
    node .github/continuum/developer-agent.mjs backlog accept \
      --file "$STATE/development-backlog.json" --id owner-square-twice
    node .github/continuum/developer-agent.mjs owner-implement-preview \
      --file "$STATE/development-backlog.json" \
      --out "$STATE/reports" --id owner-square-twice

casesJson ist ein einzelner Backlog-Skalar (maximal 300 Zeichen)
mit 1 bis 4 (input, expected)-Integerfaellen. Nur type=Int und
depth=1/2 werden fuer den Akzeptanzpfad angeboten; die reine
Kotlin-Synthese unterstuetzt intern weitere Typen, die hier aber
nicht automatisch zur Preview zugelassen sind. Datei und Klasse
muessen sich ohne zusaetzliche Android-Abhaengigkeiten isoliert
kompilieren lassen; ansonsten gibt es keinen positiven Nachweis.

**Vertrauensgrenze:** Der Preview-Status ist
VERIFIED_KOTLIN_PREVIEW, androidBuildVerified=false,
approvedForMerge=false, backlogDone=false. Ein erfolgreicher
Stand-alone-Kotlin-JVM-Test beweist noch **keine** vollstaendige
APK-/Android-Integration. Es erfolgt KEINE automatische Ausfuehrung
im zyklischen Teamleiter und keine selbststaendige Backlog-Promotion
zu DONE; dafuer fehlen noch volle Android-Compiler-/Unit-Test-Nachweise
und sichere Sandboxing-Grenzen. Die Stand-alone-Testdatei wird nur
im systemeigenen temporaeren Ordner erzeugt, nicht im Git-Repo.

    node --test .github/continuum/kotlin-delegate-acceptance.test.mjs
    node --test .github/continuum/owner-kotlin-preview.test.mjs

Vor einer produktiven Freigabe muessen obige Tests, die
vollstaendige Node-Regression, die Android-Unit-Tests und
assembleDebug auf demselben unveraenderlichen HEAD real gruen sein.


## Lokaler PC-Dauerbetrieb: Stundenplanung ohne GitHub Actions

Das Node-Kommando teamleader-cli.mjs unterstuetzt neben --once und
--status jetzt --loop. Der Prozess bleibt auf einem eingeschalteten PC
aktiv und fuehrt jeweils einen begrenzten Teamleiter-Zyklus aus. Im
Normalfall wartet er danach 60 Minuten. Nach fehlgeschlagenen
Zyklen gilt ein gedeckeltes, exponentielles Retry-Backoff von
maximal einer Stunde. Fehler werden im Heartbeat NICHT als Erfolg
gezaehlt. SIGINT/SIGTERM stoppen nach der gerade ausgefuehrten
Arbeit beziehungsweise waehrend des Wartens.

    STATE="$HOME/.local/state/device-ai-continuum/hourly-worker"
    node .github/continuum/teamleader-cli.mjs --loop \
      --root . --state "$STATE" \
      --interval-minutes 60 --budget-minutes 20

In einem zweiten Terminal:

    node .github/continuum/teamleader-cli.mjs --status \
      --root . --state "$STATE"

Der Zustand wird aussenhalb des Checkouts gespeichert. Zusaetzlich
enthaelt worker-heartbeat.json einen Zyklenzaehler, Fehlversuche,
den tatsaechlichen letzten Outcome und den naechsten geplanten Versuch.
Die Datei ist ein lokaler Lebenszeichenstatus, **kein** Compiler-
oder Testevidenzbeleg. Bei einem Absturz koennen Heartbeats veralten;
das Alter muss vor der Anzeige als "laeuft" geprueft werden.

Fuer einen endlichen Testlauf ohne laengeres Warten:

    node .github/continuum/teamleader-cli.mjs --loop \
      --root . --state "$STATE" \
      --interval-minutes 60 --budget-minutes 20 --max-cycles 1

    node --test .github/continuum/local-teamleader-loop.test.mjs

Die maximale Zyklusfrequenz ist konfigurierbar (1 bis 1440 Minuten);
dennoch muessen das PC-Betriebssystem, dessen Benutzerrechte,
der Arbeitsspeicher und ein ggf. vorhandenes Android-SDK tatsaechlich
fuer Compiler und Testketten bereitstehen. Ein abgestellter oder
schlafender PC fuehrt keine Zyklen aus. Ein abgeschlossener Chat
startet diesen lokalen Betrieb ebenfalls nicht von selbst.

Der Loop setzt ausschliesslich die vorhandenen M4-Auftragstypen um.
Kotlin IMPLEMENT bleibt noch eine separat ausloesbare Preview,
bis vollstaendige Android-/Gradle-/Unit-Evidenz den Patch deckt.
