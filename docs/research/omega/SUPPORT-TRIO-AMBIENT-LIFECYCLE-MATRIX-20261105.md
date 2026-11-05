# SupportTrio — Ambient-Lifecycle: unabhängige Abnahmematrix für #546

Stand: 5. November 2026. **SOURCE_REVIEW_ONLY / RUNTIME_NOT_RUN / FOLD7_NOT_RUN.**

Dies ist genau ein ergänzender Prüfbeitrag. Keine Produktdatei wurde geändert;
es wurden keine neuen Testklassen geschrieben oder ausgeführt. Der Koordinator sichert diese Matrix mit dem Nachtrag zu #546 in Support-PR #547.
Die Quellenbefunde in Abschnitt 1 beziehen sich auf die ursprüngliche Basis;
der aktualisierte Kandidatenbefund steht in Abschnitt 6.

## 1. Ursprünglicher Prüfgegenstand und belegter Basisbefund

- Gemeinsame Basis: [PR #543](https://github.com/Ullmann27/device-ai/pull/543),
  Commit `89ae402d88b80ca5c48ab241e20c0369b7db517d`.
- Ziel der Reparatur: [PR #546](https://github.com/Ullmann27/device-ai/pull/546).
  Letzter eigener Connector-Abruf: Head
  `f6929127476db685fe1396f550d4d4586c2e6410`, ein Commit/eine Datei;
  PR-Beschreibung bezeichnet ihn weiterhin als Startcheckpoint. Ein reparierter
  Produkt-HEAD ist damit noch nicht verifiziert.
- Die vollständige
  [JarvisShellView.kt auf #543](https://github.com/Ullmann27/device-ai/blob/89ae402d88b80ca5c48ab241e20c0369b7db517d/app/src/main/java/at/heinz/deviceai/ui/JarvisShellView.kt)
  wurde über den GitHub-Connector gelesen. Git-Blob:
  `7d1ae5b619775194c6bbd7d7423363510fc5b664`.
- Die lokal nur gelesene Datei im Hauptteam-Checkout auf
  `7ecdafac328700295da393ce44bbe22eb8b21aee` hat mit `git hash-object` denselben
  Blob geliefert. Diese Prüfung bestätigt Dateigleichheit, keinen Laufzeiterfolg.

**Im Quelltext belegt:** Die Ambient-Fortsetzung entsteht am Ende von `onDraw`
über `postInvalidateDelayed(66L)`. Voraussetzung sind `ambientMotionEnabled`,
Animator-Skala größer null, `isShown`, sichtbares Fenster, Fensterfokus und kein
Stromsparmodus. `ANIMATOR_DURATION_SCALE` wird nur während `onDraw` gelesen.
Die Klasse enthält keinen `ContentObserver` für diese Einstellung, keinen
Power-Save-Receiver und keine eigenen Attach-/Detach-, Fokus- oder
Visibility-Wiederanlauf-Callbacks. Der Setter von `ambientMotionEnabled` ruft
dagegen bereits `invalidate()` auf.

**Noch nicht laufend reproduziert:** Dass die Shell in einer konkreten App- oder
Gerätekonstellation dauerhaft stehen bleibt. Fremde Statusänderungen und
Framework-Traversals können zusätzliche Draws auslösen und die Anschlusslücke
verdecken. Insbesondere darf aus dem fehlenden Fokus-Override allein kein
nachgewiesener Fokusfehler abgeleitet werden.

**Zusätzlicher Prüfpunkt zur gemeldeten Power-Save-Lücke:** Wenn ein Draw die
Skala `0` sieht, plant er keine weitere Ambient-Invalidierung. Der spätere
Wechsel `0 → 1` hat innerhalb dieser Klasse keinen eigenen Wiederanlauf-Auslöser.
Das ist als fehlender Anschluss belegt; das sichtbare Einfrieren bleibt
`NOT_RUN`. Die Matrix prüft beide Richtungen der Einstellung ausdrücklich.

Als vorhandenes Vergleichsmuster wurde
[ContinuumHologramView.kt auf dem gelesenen Hauptteam-HEAD](https://github.com/Ullmann27/device-ai/blob/7ecdafac328700295da393ce44bbe22eb8b21aee/app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt)
herangezogen: Attach registriert Observer/Receiver, Detach räumt sie und den
Frame-Callback auf; Ereignisse bewerten die Bewegung erneut und stoßen die
Frameplanung an. Dies ist ein Codevergleich, kein Nachweis gleicher
Fokusregeln oder bereits bestandener Shell-Tests.

## 2. Gemeinsame, gegen Fehlmessungen abgesicherte Fixture

Eine spätere Testklasse kann `SupportTrioShellAmbientLifecycleUiTest` heißen.
Sie muss die echte `JarvisShellView` an einem echten Activity-Fenster verwenden.
Ein nachgebauter Scheduler oder ein Source-Regex-Test ersetzt diesen Nachweis
nicht.

1. Für den isolierten Scheduler-Test nur die Shell in einem `FrameLayout`
   montieren. Status-/Content-Updates und andere animierte Views während der
   Messung ausschließen. Mindestgröße der Shell beachten: mindestens
   `240 × 160 dp`, etwa `412 × 915 dp` als synthetische Fixture.
2. Vor jedem Szenario tatsächliche Zustände prüfen: attached, shown,
   `windowVisibility == VISIBLE`, `hasWindowFocus() == true`, Power-Save aus,
   Skala `1`, `ambientMotionEnabled == true`.
3. **`frameLogging = false` lassen und `playEntrance()` nicht aufrufen.**
   Der Debug-`Choreographer` ruft selbst fortlaufend `invalidate()` auf und
   könnte den zu prüfenden Wiederanlauffehler vollständig maskieren. Eine
   Entrance-Animation besitzt ebenfalls einen separaten Fortsetzungszweig.
4. Den vorhandenen `onFrameDrawn`-Hook ausschließlich für Zähler und monotone
   Zeitstempel nutzen. Der Beobachter darf weder invalidieren noch Zustand
   setzen. Bei Beobachtung der echten Home-Ansicht deren bestehende
   Foreground-Weiterleitung erhalten; sie nicht durch den Zähler ersetzen.
5. Ein realer erster Layout-/Draw-Durchlauf darf die Startphase auslösen.
   Anschließend muss die Fixture ohne manuelle `draw()`-/`invalidate()`-Aufrufe
   mindestens drei weitere Draws in einem begrenzten 500-ms-Fenster liefern.
   Gelingt das schon im aktiven Ausgangszustand nicht, ist der Host-Harness
   ungeeignet oder unvollständig: `HARNESS_UNAVAILABLE`, kein Produkt-FAIL.
6. Im pausierten Robolectric-Looper die Zeit in begrenzten 16-ms-Schritten
   fortschalten, entsprechend der vorhandenen
   [ContinuumAvatar3dLifecycleUiTest-Fixture](https://github.com/Ullmann27/device-ai/blob/7ecdafac328700295da393ce44bbe22eb8b21aee/app/src/uiTest/java/at/heinz/deviceai/ContinuumAvatar3dLifecycleUiTest.kt).
   Kein unbegrenztes Abarbeiten einer selbst fortsetzenden Queue.
7. Nach einem Stopp-Ereignis zunächst bereits fällige Traversals auslaufen
   lassen, dann über ein weiteres 500-ms-Fenster ein Zählerplateau prüfen.
   Ein abschließender statischer Draw ist erlaubt. Nach Freigabe müssen
   mindestens drei automatisch fortgesetzte Draws im nächsten 500-ms-Fenster
   beobachtbar sein. Ein einziger Recovery-Draw reicht nicht.
8. Nach dem Recovery-Ereignis nicht tippen, scrollen, Größe ändern, Content
   neu zuweisen oder `draw()`, `invalidate()` beziehungsweise `playEntrance()`
   als Hilfestellung aufrufen. Solche Eingriffe würden die entscheidende
   Wiederaufnahme künstlich erzeugen.

Die 500-ms-Fenster sind begrenzte Kriterien für die virtuelle Host-Uhr, keine
zugesicherte Fold7-Latenz. Draw-Zähler belegen die Canvas-Fortsetzung, keine
GPU-Bildqualität, sichtbare Phasenänderung oder neue Leistung des Modells.

## 3. Ereignis- und Beobachtungsmatrix

**Alle folgenden Fälle haben den Ausführungsstatus `NOT_RUN`.** Die erwarteten
Beobachtungen beschreiben die Abnahme des künftigen #546-Fixes. Sie sind keine
vorweggenommenen Testergebnisse für #543.

| ID | Reales Ereignis / genaue Folge | Erwartete Beobachtung am reparierten Stand | Beleglage der Basis / wichtige Kontrolle |
| --- | --- | --- | --- |
| A0 | Shell sichtbar und fokussiert an Activity hängen; Skala 1, Power-Save aus. | Nach dem ersten Draw laufen weitere Ambient-Draws eigenständig. | Positivkontrolle der Fixture; ohne sie keine Lifecycle-Aussage. |
| F1 | Fensterfokus verlieren und zurückerlangen; Fenster und Eltern bleiben sichtbar. Im Host den tatsächlichen Window-Zustand ändern, nicht nur einen Callback von Hand aufrufen. | Bei `hasWindowFocus() == false` stoppt die Kette; nach tatsächlich wiederhergestelltem Fokus setzt sie sich fort. | Eigener Fokus-Callback fehlt. Framework kann selbst zeichnen; dauerhaftes Stehenbleiben noch nicht reproduziert. |
| W1 | Fenster über echte Activity-/Window-Lifecycle-Ereignisse unsichtbar machen und wieder sichtbar fortsetzen. Vor der Recovery-Messung sichtbares Fenster und Fokus bestätigen. | Kein fortlaufendes Ambient-Redraw bei unsichtbarem Fenster; automatische Fortsetzung nach sichtbarer, fokussierter Rückkehr. | Eigener Window-Visibility-Callback fehlt. Ein bloßer `onWindowVisibilityChanged(VISIBLE)`-Aufruf ohne echten Fensterzustand ist keine valide Fixture. |
| V1 | Übergeordnetes `FrameLayout` auf `INVISIBLE`, dann `VISIBLE` setzen. Shell selbst bleibt `VISIBLE`. Separater Durchlauf mit Eltern-`GONE → VISIBLE`. | `isShown` folgt der Eltern-Visibility; Stoppen und Wiederanlaufen ohne Änderung der Shell-Daten. Nach `GONE` zunächst reale Wiederherstellung gültiger Bounds bestätigen. | Eigene View-/Aggregated-Visibility-Callbacks fehlen; Eltern-Visibility darf nicht durch ein ausschließlich direktes Shell-Visibility-Szenario ersetzt werden. |
| V2 | Shell selbst auf `INVISIBLE → VISIBLE` setzen, danach gleichen Fall mit `GONE → VISIBLE`. | Wie V1; ein einzelner Draw nach Sichtbarkeitswechsel genügt nicht als Wiederanlauf. | Kontrolliert direkten View-Pfad separat vom Elternpfad. |
| P1 | Bei konstant sichtbarem, fokussiertem Fenster Power-Save einschalten; echten Zustand setzen und `ACTION_POWER_SAVE_MODE_CHANGED` zustellen. Nach Plateau Power-Save ausschalten und erneut zustellen. | Ein: Plateau. Aus: automatisch fortlaufende Draws ohne Fokus-/Layoutwechsel. | Power-Save wird nur beim Draw abgefragt; Receiver fehlt. Ein bloßer Broadcast ohne passenden PowerManager-Zustand testet den Fall nicht. Quick-Settings am Gerät verändern zusätzlich den Fokus und sind deshalb kein isolierter P1-Nachweis. |
| S1 | Android-Animator-Dauerskala über den Settings-Provider `1 → 0`, später `0 → 1` ändern, einschließlich Benachrichtigung der zugehörigen Settings-URI. Fenster, Fokus und Power-Save konstant halten. | `0` bedeutet reduzierte/ausgeschaltete Systemanimation: Plateau. Rückkehr zu `1` startet die Kette selbstständig. | Kein `ContentObserver`; nur Lesen in `onDraw`. Wert nach jedem Schritt über denselben ContentResolver zurücklesen. Nur einen Mock-Wert ohne Provider-Benachrichtigung zu ändern prüft keinen Observer. |
| S2 | Öffentliche Shell-Eigenschaft `ambientMotionEnabled: true → false → true` ändern, alle Systemgates offen lassen. | Aus: Plateau. An: selbstständige Fortsetzung. | Beide Setter-Aufrufe invalidieren bereits in #543. Das ist eine sinnvolle positive Wiederanlauf-Kontrolle; kein Beleg, dass ein sichtbarer App-Schalter diese Eigenschaft bereits ansteuert. |
| D1 | Dieselbe Shell über `host.removeView(shell)` entfernen, während sie aktiv ist. Im abgetrennten Zustand Power-Save und Skala ändern; danach bei erlaubtem Zustand dieselbe Instanz erneut anhängen. | Keine fortlaufenden Ambient-Callbacks beim Detach; keine verwaisten Observer/Receiver. Beim Reattach genau eine aktive Taktquelle, automatische Draw-Folge, keine Registrierungsfehler. | Eigene Attach-/Detach-Behandlung fehlt. Aus dem fehlenden Override allein folgt noch kein nachgewiesenes Framework-Leak. |
| G1 | Zwei Sperren gleichzeitig setzen, z. B. Power-Save ein und Skala 0. Nur Power-Save aufheben, dann erst Skala auf 1 setzen. Reihenfolge in zweitem Lauf umkehren. | Erstes Recovery-Ereignis darf die verbleibende Sperre nicht umgehen. Erst wenn alle Gates offen sind, setzt sich die Kette fort. | Verhindert einen Fix, der bei jedem Broadcast blind weiterläuft. Ebenso Fokusverlust plus System-Setting-Recovery prüfen. |
| R1 | Fokus-/Visibility-Recovery und gleichwertige Power-/Settings-Benachrichtigungen mehrfach eng nacheinander zustellen; zusätzlich 20 Attach-/Detach-Zyklen. | Kein wachsender Draw-Takt, keine mehrfach registrierten Observer/Receiver, nach letztem Detach keine eigene wiederkehrende Aufgabe. | Identische virtuelle Messfenster vor/nach Zyklen vergleichen. Draw-Zähler allein schließen durch das Framework zusammengefasste doppelte Scheduling-Aufträge nicht aus; dafür Queue-/Registrierungsbeobachtung ergänzen und diese Beleggrenze ausdrücklich berichten. |
| C1 | Gleiche Lifecycle-Fälle mit `compactPresentation = false` und `true`; synthetische Viewports `412 × 915 dp`, `884 × 760 dp`, außerdem erhöhte Schrift `1.5`. Danach Compact wechseln und Größenwechsel durch echten Layout-Durchlauf ausführen. | Für jede unveränderte Konfiguration bleiben Shell-Bounds, Scrollhöhe und Hit-Target-Zuordnung vor/nach den Lifecycle-Ereignissen identisch. Nach tatsächlichem Konfigurationswechsel wird passende C1-Geometrie neu berechnet; keine alten Bounds weiterverwenden. | `layoutCompact`, stabiler `viewportHeightDp` und dessen Trennung von scrollbarer Contenthöhe müssen erhalten bleiben. Die Zahlen sind Test-Fixtures, keine gemessenen Fold7-Displaymaße. |
| C2 | Auf dem tatsächlichen Fold7 offen und geschlossen: Systemgates wechseln, Hintergrund/Vordergrund, Compact umschalten und bis zu den unteren Controls scrollen. | Ambient-Effekt läuft nach erlaubter Rückkehr sichtbar wieder; Kopf und Originalkugel bleiben vorhanden, Controls erreichbar, Foreground/Hit-Targets deckungsgleich, keine neue Überlagerung durch den Lifecycle-Fix. | Separates Geräteprotokoll mit exaktem APK-/Quellstand. Host-Canvas und Layoutzahlen ersetzen weder Filament-/GPU- noch Fold7-Abnahme. |

## 4. Kompakte Reproduktionsfolge für den stärksten Recovery-Test

S1 isoliert die zusätzliche Skalenlücke ohne absichtlichen Fokuswechsel:

1. A0 erfolgreich herstellen; Zähler und Zustände dokumentieren.
2. Animator-Skala über den Provider auf `0` setzen und die Benachrichtigung
   zustellen. Nach auslaufender Invalidation Plateau dokumentieren.
3. Nur die Skala auf `1` zurückstellen und die Benachrichtigung zustellen.
   Keine weitere UI-Aktion. Über 500 ms automatische Draws zählen.
4. Falls keine Folge entsteht, **erst nach Abschluss der fehlgeschlagenen
   Messung** einmal extern invalidieren. Beginnt dann die normale Kette,
   stützt das die Hypothese eines fehlenden Wiederanlauf-Auslösers. Dieser
   diagnostische Zusatz darf die ursprüngliche Recovery-Prüfung nicht grün
   machen.
5. Dieselbe Sequenz unverändert auf dem exakt bezeichneten Produkt-Fix-Commit
   von #546 wiederholen. `RED → GREEN` erst berichten, wenn beide Läufe
   tatsächlich ausgeführt wurden. Sollte bereits #543 bestehen, nicht
   nachträglich einen Fehler erfinden: auslösenden Framework-/Fixture-Draw
   ermitteln und die Hypothese neu bewerten.

## 5. Ergebnisformat und Übergabe

Je Szenario festhalten: exakter Commit und Shell-Blob, Testname, SDK/Robolectric
beziehungsweise Gerät/APK, Konfiguration, tatsächliche Gate-Zustände,
Ereigniszeitpunkte, automatische Draw-Zeitstempel, ausstehende eigene
Callbacks/Registrierungen soweit beobachtbar, Status und Raw-Log. Private
Dialoginhalte sind dafür nicht erforderlich.

Aktueller tatsächlicher Ergebnisstand:

| Nachweis | Status |
| --- | --- |
| Vollständige Remote-Quelle der Shell auf #543 gelesen | Erledigt; Blob oben angegeben |
| Lokaler Shell-Blob gegen Remote-Blob verglichen | Gleich; kein Laufzeittest |
| Fehlende Power-/Settings-/Lifecycle-Anschlüsse in der Klasse geprüft | Im Quelltext belegt |
| Dauerhaftes Einfrieren in echter View reproduziert | NOT_RUN |
| Neue Robolectric-/JUnit-Testdatei | Nicht erstellt |
| Baseline- oder Fix-Testlauf | NOT_RUN |
| Screenshot, APK-, Filament- oder Fold7-Abnahme | NOT_RUN |
| Produktänderungen dieses Unterstützers | Keine |
| Publikation dieses Handoffs | In Support-PR #547 durch den Koordinator gesichert |

Der nächste konkrete Schritt ist S1 und P1 am tatsächlichen #546-Produktfix,
mit A0 als vorgeschalteter Fixture-Kontrolle; anschließend F1/W1/V1/D1/G1.
Geometrie-/Fold-Abnahme bleibt ein eigenes Gate. Historische Node-/JUnit-Zahlen
anderer Zweige sind kein Ergebnis dieser Matrix.

## 6. Nachtrag vom 5. November 2026 — Quellreview des veröffentlichten #546-Fixes

**SOURCE_REVIEW_ONLY; keine eigene Testausführung.** Der oben dokumentierte
fehlende Anschluss beschreibt weiterhin die Basis #543; er darf nicht als
unveränderter Befund über den folgenden neuen Stand übernommen werden.

Erneut vollständig über den Connector gelesen, auf exakt
`cc4075f2e6f9831ddbea0bba5fb518a1c9c3a373`:

- [JarvisShellView.kt](https://github.com/Ullmann27/device-ai/blob/cc4075f2e6f9831ddbea0bba5fb518a1c9c3a373/app/src/main/java/at/heinz/deviceai/ui/JarvisShellView.kt),
  Blob `9167d92e4cd641ed643a7acd0e132dc114f4238e`.
- [JarvisShellMotionUiTest.kt](https://github.com/Ullmann27/device-ai/blob/cc4075f2e6f9831ddbea0bba5fb518a1c9c3a373/app/src/uiTest/java/at/heinz/deviceai/JarvisShellMotionUiTest.kt),
  Blob `26cbc22f27b1acb5175b8c373b38291e42a337b4`.

### Geschlossene Quellenanschlüsse

Die Shell registriert jetzt beim Attach den Settings-`ContentObserver` und
den Power-Save-Receiver. Beide führen zu `refreshMotion()`. Dasselbe gilt für
Fokus sowie Window-, View- und aggregierte Visibility. Detach entfernt den
Ambient-Callback und meldet die registrierten Beobachter ab. Die beiden
Registrierungsflags verhindern im normalen Pfad wiederholtes Registrieren.

Der benannte `ambientFrame`-Runnable kann abgebrochen werden. Vor Neuplanung
und bei einer Policy-Aktualisierung werden bisherige Ambient-Callbacks entfernt.
Der Runnable prüft die tatsächliche Berechtigung erneut; `motionAllowed()`
enthält jetzt auch `isAttachedToWindow`. Die Entrance-Fortsetzung wird ebenfalls
durch diese Policy begrenzt. Bei gesperrter Ambient-Bewegung erhält der Renderer
die konstante Szenenzeit `0L`. Die Shell behält ihre C1-Cachebedingungen für
`compactPresentation` und den getrennten `viewportHeightDp` bei; andere
Layoutdateien waren nicht Gegenstand dieses engen Reviews.

### Was die fünf vorhandenen Tests tatsächlich prüfen

| Test | Abdeckung gegenüber der Matrix | Grenze |
| --- | --- | --- |
| `focusReturnRestartsAmbientFrames` | F1: echte `ActivityController.windowFocusChanged`-Folge, Stopp und Fortsetzung | Kein zusätzlicher Window-Visibility-Fall |
| `visibilityReturnRestartsAmbientFrames` | V2: Shell `INVISIBLE → VISIBLE` | Kein Eltern-Visibility- oder GONE-Fall |
| `powerSaveExitRestartsAmbientFrames` | P1: PowerManager-Shadow ändern und passenden Broadcast zustellen | Nur SDK 28, nicht der Receiver-Registrierungszweig ab API 33 |
| `restoredAnimatorScaleRestartsAmbientFrames` | S1: Settings `1 → 0 → 1` mit `notifyChange` der richtigen URI | Keine Kombination mit einer weiterhin geschlossenen zweiten Sperre |
| `reattachmentRestartsWithoutRetainedCallbacks` | D1: dieselbe View entfernen und wieder anhängen, Draw-Plateau und Fortsetzung | Beobachtet keine ausstehenden Callbacks oder Observer-/Receiver-Zahlen; der Testname allein belegt deren vollständige Bereinigung nicht |

Die Fixture erfüllt wesentliche Voraussetzungen der Matrix: echte Activity und
angehängte Shell, Native Graphics auf dem Host, pausierter Looper mit begrenzten
16-ms-VSync-Schritten, reiner `onFrameDrawn`-Zähler, kein manueller Recovery-Draw,
keine Entrance-Animation und unverändert ausgeschalteter Debug-Frameclock.
`assertRunning` wartet zunächst 240 ms und verlangt im nächsten 240-ms-Fenster
mindestens zwei weitere Draws; damit wird mehr als ein einmaliger Recovery-Draw
geprüft. `assertStopped` prüft nach gleichem Auslaufen ein Draw-Plateau.

**Konkrete verbleibende Abnahmegrenzen:** W1, V1, GONE-Varianten, S2,
G1 (nur eine von zwei Sperren freigeben), R1 (wiederholte Ereignisse und
Registrierungs-/Callback-Beobachtung), C1 und C2 werden von diesen fünf Fällen
nicht abgedeckt. Die Fixture prüft `isAttachedToWindow` ausdrücklich; weitere
Gate-Zustände werden überwiegend durch die erfolgreiche aktive Startkontrolle
abgesichert, nicht einzeln protokolliert. Native Robolectric Graphics mit
SDK 28 ist kein Fold7-, Filament- oder aktueller Android-Gerätenachweis.

**Fazit dieses Nachtrags:** Die zuvor benannten Recovery-Anschlüsse sind auf
diesem Produkt-HEAD im Quelltext vorhanden, und fünf sinnvolle verhaltensbezogene
Tests liegen vor. Eigene Laufzeit-, RED→GREEN- oder Geräteergebnisse werden
hiermit nicht behauptet. Keine Builds, Produktänderungen oder Teständerungen
durch diesen Unterstützer.
