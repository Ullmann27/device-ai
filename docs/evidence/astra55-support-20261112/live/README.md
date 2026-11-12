# Astra55 – unabhängige Live-Prüfung

Stand: 12.11.2026. Quellzweig `work/astra55-live-20261112`, abschließend geprüfter
Commit **`8a71c46a1aeecc76ab9bb97076a6e6a39c11545b`**; Erstprüfung auf
`5574282e9f2e6a8e726be81ec257073d4c723f77`. Integrationsziel bleibt die bestehende
Cockpit-Linie; dieser Ordner liefert nur Prüfungen und einen Reparaturkandidaten.

**Ergebnis:** Drei Logikfehler reproduziert. Ein kleiner Patch für Fensterbindung
und Journal erreicht **32/32 gezielte Prüfungen**. Der Scroll-Falschpositivfall
bleibt **OPEN / REPRODUCED**. Der Patch wurde ausschließlich in einer temporären
Quellkopie angewendet, nicht im Arbeitsbaum des Implementierers.

**Android-Adapter, MediaProjection, Filament, APK, Fold7: NOT_RUN.** Kein Gradle,
keine Git-Ref-Änderung, kein externer Schreibvorgang durch diesen Prüfer.

## 1. Ausgeführte Prüfungen

| Lauf | Umfang | Ergebnis | Beleg |
|---|---|---|---|
| `red-01` | 6 neue Negativ-/Framefälle; ToolGate-Typen als reine Compile-Fixtures | 3 Fehler, 3 bestanden | `red-01/junit.log`, `manifest.json` |
| `red-neighbors-02` | Dieselben 6 Fälle plus 27 unveränderte Nachbartests; echte Registry-/Receipt-Klassen | 33 Tests, 3 Fehler | `red-neighbors-02/junit.log`, `manifest.json` |
| `candidate-03` | Zwei Reparaturfälle, drei Framefälle, 27 unveränderte Nachbartests | **32 Tests, 0 Fehler** | `candidate-03/junit.log`, `manifest.json` |
| `candidate-03` separat | Unveränderter Scroll-Negativfall, gleiche kompilierte Klassen | **1 Test, 1 Fehler – weiterhin offen** | `candidate-03/open-scroll.log` |
| `candidate-04-final` | Derselbe Kandidat auf eingefrorenem Commit `8a71c46a1` | **32 Tests, 0 Fehler** | `candidate-04-final/junit.log`, `manifest.json` |
| `candidate-04-final` separat | Offener Scrollfall auf derselben Kompilierung | **1 Test, 1 Fehler – weiterhin offen** | `candidate-04-final/open-scroll.log` |

Die 27 Nachbartests: `LiveComputerFramesTest` 11/11,
`LiveComputerActionsTest` 10/10, `VerifiedNodeClickTest` 6/6.
Keine vorhandene Assertion wurde geändert.

Compiler: Kotlin 2.0.20, direkte `K2JVMCompiler`-Ausführung, maximal 512 MB;
JUnit 4.13.2. Die erweiterten Läufe kompilieren unveränderte Produktionsdateien
einschließlich `ToolRegistry`, `ToolExecutionReceiptStore` und `AgentProtocol`.
Lediglich die beiden Enum-Definitionen `Risk` und `ToolExecutionStatus` werden
wortgetreu aus ihren Android-nahen Quelldateien extrahiert. Es werden keine
Android-API-Aufrufe simuliert oder als Geräteerfolg gewertet.

Alle Manifeste enthalten Quell-HEAD, Arbeitsbaumstatus, SHA-256 vor dem Kopieren,
nach dem Kopieren und nach dem Lauf sowie Abhängigkeiten-/Testhashes. Die jeweils
getesteten Originaldateien waren während der ersten drei Läufe unverändert.
Der letzte Lauf liest alle Quellen ausdrücklich über `git show` vom eingefrorenen
Commit. Er bewertet nicht die parallel weiterbearbeitete Arbeitskopie.

Der relevante Unterschied in `LiveComputerActions.kt` zwischen den beiden Commits
ist ausschließlich das neue optionale `heading`-Feld von `ActionNode`. Er behebt
keinen der drei reproduzierten Fehler. Der unveränderte Patch lässt sich auf
`8a71c46a1` anwenden; dessen ursprünglicher Dateihash ist
`708d5554142c447df85f8003043c2ba168ec9bb61e9062902c06b4122d140fb5`.

## 2. Zwei geprüfte Reparaturkandidaten

### LIVE-01: Fensterwechsel bestätigt dieselbe Aktion

**Quelle:** `cognitive/LiveComputerActions.kt:59–69`.
`ActionEffectVerifier` vergleicht Task, Epoche und Paket, aber keine Fenster-ID.
Repro: vorher Fenster 42, danach Fenster 43; gleiche übrige Bindung,
neuere Revision/Zeit, anderer Digest und erfüllte Erwartung. Ergebnis vorher:
`CONFIRMED`; erforderlich: `UNCLEAR_CONTEXT_CHANGED`.

Der aktuelle Android-Port prüft seine Fenster-ID zusätzlich. Der Fehler liegt
deshalb an der allgemeinen Verifier-Grenze; der Test behauptet keinen ausgeführten
Android-Fensterwechsel. `repair.patch` ergänzt genau den fehlenden ID-Vergleich.

### LIVE-02: Unsichere Aktion wird nach Eviction erneut zugelassen

**Quelle:** `cognitive/LiveComputerActions.kt:111–132`.
Das Journal entfernt bei voller Unsicherheitsmenge den ältesten Fingerprint.
Repro mit Kapazität 2: drei verschiedene unklare Aktionen auf frischen Revisionen;
danach erneut die erste Aktion. Vorher: `ALLOWED` trotz früherem unklarem Ausgang.
Bei der Produktionskapazität 64 entsteht derselbe Fehler nach 65 unterschiedlichen
unklaren Aktionen.

Der Patch behält die begrenzte Menge und setzt bei einem weiteren unklaren Ausgang
eine dauerhafte Sperre für das laufende Journal. Dadurch werden keine unbekannten
Wirkungen vergessen und keine unbeschränkten Daten gesammelt. Bekannte Ergebnisse
und normale Folgeaktionen vor Erreichen dieser Grenze bleiben unverändert.
Eine erschöpfte Sitzung benötigt eine ausdrückliche Neuentscheidung; das Patch
setzt sie nicht automatisch zurück.

### Patchbindung

Datei: `repair.patch`; genaue Ausgangs-/Zielhashes: `repair-source.json`.
Nur `app/src/main/java/at/heinz/deviceai/cognitive/LiveComputerActions.kt` betroffen.
Der Patch verändert keine Scroll-Erfolgsprüfung, keinen Renderer, keinen Capturepfad,
keinen Eventbus und keine Freigabe.

Status: **PATCH_HOST_VERIFIED / NOT_APPLIED_TO_IMPLEMENTER / NOT_PUSHED**.
Nach Übernahme muss der Integrator die Prüfung an seinem tatsächlichen SHA wiederholen.

## 3. Offener Integrationsfehler: Scroll-Falschpositiv

**LIVE-03 – OPEN / REPRODUCED.** Quelle:
`cognitive/LiveComputerActions.kt:339–342` und `ActionEffectVerifier:65–69`
am abschließend geprüften Commit.

Der Negativtest hält den Scrollcontainer unverändert und ändert nur einen
unbeteiligten Badge von `1` auf `2`. Revision, Zeitpunkt und gesamter Baum-Digest
werden neuer; Android hat die Eingabe angenommen. Aktuelles Ergebnis:
`CONFIRMED`, obwohl keine Bewegung des Containers belegt ist.

Kleinster vorhandener Fixpfad: Im bestehenden
`ContinuumScreenObservationService`/`ScreenObservationBus` einen zum konkreten
Zielcontainer gehörenden Scrollnachweis auswerten. Bindung mindestens an Service-
Epoche, Fenster-ID, Containeridentität, Ausgangsrevision und Dispatchzeit;
darauf eine neue Beobachtung desselben Containers prüfen. Je nach App können
Scroll-Delta, Offset-/Indexänderung oder eine belegte Änderung des Containerzustands
den Nachweis liefern. Ein Ereignis aus einem fremden Container oder Fenster
genügt nicht. Fehlt der Beleg, bleibt das Ergebnis unklar.

Die bestehende Scrollfunktion und ihre positiven Tests werden in diesem
Supportpatch nicht pauschal deaktiviert oder umgeschrieben. Der Negativfall bleibt
als Integrationsgate offen und muss bei der Android-Ergänzung grün werden.

## 4. Android-/Capture-Hinweise, nur Quellprüfung

**LIVE-04 – OPEN_SOURCE / Android NOT_RUN:**
`res/xml/continuum_screen_observation.xml:3` registriert ausschließlich
WindowState/Windows/WindowContent. `ContinuumScreenObservationService.kt:18–29`
verarbeitet weder `TYPE_VIEW_SCROLLED` noch `TYPE_VIEW_TEXT_CHANGED`.
`AndroidLiveNodePort.awaitPostcondition():40–44` wartet jedoch auf eine neue
Busrevision. Ein Ziel, das nach der Eingabe nur eines dieser spezifischen
Ereignisse sendet, kann so keine Postcondition auslösen. Empfehlung: beide
Ereignistypen im bestehenden Dienst berücksichtigen und wie den Scrollnachweis
an das tatsächliche Ziel binden. Keine weitere Ereignisverwaltung anlegen.

**LIVE-05 – OPEN_SOURCE / Android NOT_RUN:**
`AndroidLiveComputer.kt:141–165` am geprüften Commit verwirft die vor dem Screenshot gelesene Revision
und beschriftet das Bild mit Callbackzeit und danach gelesener Revision. Eine
zwischen Aufnahme und Callback geänderte Ansicht kann dadurch ältere Pixel mit
neuer Beobachtungsrevision liefern. Empfehlung: Ausgangsrevision festhalten,
im Callback revalidieren und bei Wechsel verwerfen; Aufnahmezeit aus
`ScreenshotResult.timestamp` berücksichtigen. Diese Zeit ist in Uptime-Millis
angegeben, keine unkommentierte Gleichsetzung mit `System.nanoTime()`.

**MediaProjection-Stand:** Der geprüfte Commit enthält den neuen
API-34-Fenster-Screenshotpfad. Nach diesem Commit entstehen im aktiven Arbeitsbaum
bereits weitere MediaProjection-/GPU-Crop-Anschlüsse. Diese laufenden Änderungen
sind ausdrücklich nicht Gegenstand des eingefrorenen Tests. Es gibt keinen
hier nachgewiesenen MediaProjection-Start-/Stop-/Freigabenachweis.

## 5. Bereits vom Live-Implementierer korrigierte Punkte

| Frühbefund | Aktuell beobachteter Stand | Nachweisgrenze |
|---|---|---|
| Rate-Limit überspringt Secure-/Quellensperre | Quellprüfung vor Rate-Limit in `LiveComputerFrames.kt` | Eigener Negativtest + vorhandene Tests bestanden |
| Pause vergisst bisherige Frame-Reihenfolge | Separater `lastFrameTimestamp` bleibt über Pause erhalten | Eigener Negativtest bestanden |
| Leased Frame darf bei Stop nicht doppelt/früh freigegeben werden | Bestehendes Lease-Modell korrekt in geprüftem Fall | Eigener Stop-/Freigabetest bestanden |
| Rechteentzug lässt alte Sitzung weiterpolling | `onInterrupt`/`onDestroy` rufen `observationRevoked` auf | `FIXED_SOURCE`, Android NOT_RUN |
| Langtext/Zeilenumbruch bricht gesamten Tree ab | `8a71c46a1` normalisiert/begrenzt Labels statt globalem Fehler | `FIXED_SOURCE`, Android NOT_RUN |

Nicht erneut parallel reparieren. Capture, Fensterwahl und Frame-Vertrag wurden
nach dem geprüften Commit bereits weiterbearbeitet; neue GPU-/Projection-Dateien
waren ebenfalls in Arbeit. Der bei Abschluss gelesene Status und die Dateihashes
stehen in `source-review-final.json`; sie sind kein zusätzlicher Testbeleg.

## 6. Reproduktion

`ASTRA_LIVE_SOURCE` auf den zu prüfenden Checkout und `ASTRA_KOTLIN_LIB` auf das
Verzeichnis mit den im Runner benannten Kotlin-/JUnit-JARs setzen. Aus der
Repositorywurzel mit frischem Evidence-Ziel ausführen:

```sh
python docs/evidence/astra55-support-20261112/live/run_review.py \
  --source "$ASTRA_LIVE_SOURCE" \
  --revision 8a71c46a1aeecc76ab9bb97076a6e6a39c11545b \
  --compiler-lib "$ASTRA_KOTLIN_LIB" \
  --evidence /tmp/astra55-live-review-red \
  --neighbors

python docs/evidence/astra55-support-20261112/live/run_review.py \
  --source "$ASTRA_LIVE_SOURCE" \
  --revision 8a71c46a1aeecc76ab9bb97076a6e6a39c11545b \
  --compiler-lib "$ASTRA_KOTLIN_LIB" \
  --evidence /tmp/astra55-live-review-candidate \
  --neighbors --suite focused \
  --patch docs/evidence/astra55-support-20261112/live/repair.patch
```

Der fokussierte Lauf führt den offenen Scrollfall anschließend getrennt aus und
schreibt dessen Fehler in `open-scroll.log`/`open_scroll_exit`. Ein grüner
fokussierter Lauf erklärt den Scrollfehler nicht für geschlossen.

## 7. Quellen und Grenzen

Gelesen: `AGENTS.md`, `CODE55-UEBERGABE-CHATGPT-ASTRA-20261112.md`,
`LIVE-COMPUTER-AUDIT-20261112.md`, tatsächliche Originalquellen und Diffs.
Eine gesonderte Datei `CODE55-AGENT-C-UEBERGABE.md` wurde im verfügbaren Live-Baum
nicht gefunden. Issue #41 wurde in diesem lokalen Supportlauf nicht neu abgerufen.
Betroffene bestehende Experimente: EXP-05, EXP-06, EXP-11, EXP-12; keine neue Linie.

Offizielle Android-Referenzen, am 12.11.2026 geprüft:

- [ScreenshotResult: Aufnahmezeit und HardwareBuffer-Freigabe](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.ScreenshotResult)
- [AccessibilityEvent: Scrollereignis, Quellknoten und Bewegungsdaten](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent#TYPE_VIEW_SCROLLED)

Diese API-Belege ersetzen keine Ausführung auf Android/Fold7.
Rücknahme: `repair.patch` nicht übernehmen; der Produktionsbaum wurde durch den
Prüfer nicht verändert.
