# Arbeitsfenster-Kamerafahrt – Hostnachweise

Quellstand: `c9984b3faeaa14a9a2fb06614cfc3d3159b18107`,
[Draft-PR #571](https://github.com/Ullmann27/device-ai/pull/571),
Ergänzung zu #565 bis `61ab95e1263c57cb3f68daef2766ef5e2c1e94a9`.
Kein Geräte- oder Release-Nachweis.

**Abschluss: 120/120 Tests bestanden, 0 Fehler, 0 Skips; NewApi PASS,
0 Befunde.** Exit 0, Laufzeit 6 min 21 s, sauberer Checkout beim Start,
App-Quellen während des Laufs unverändert. Danach nur Dokumentation ergänzt.
Quellbindung und Suite-Ergebnisse: [final-receipt.json](final-receipt.json).
Ausgabe (abschließende Leerzeichen entfernt): [final-gradle.log](final-gradle.log),
Lint: [newapi-results.xml](newapi-results.xml).

## Verhalten und Verbindungen

`JarvisWorkWindowHost` meldet nur ein tatsächlich angenommenes Arbeitsfenster
an `ContinuumHologramView`. `AvatarCameraMotion` verwendet dessen bestehende
Frame-Uhr: 520 ms bei Animationsfaktor 1, kubische Ein-/Ausleitung, begrenzte
Zeitschritte, Umkehr aus dem aktuellen Zustand. Schließen führt zurück zur
Ruheprojektion; STOP, sofortiges Schließen, Pause, Fokusverlust, Abhängen und
deaktivierte Bewegung verwerfen die Fahrt ohne späteres Nachholen.

`AvatarCameraFraming` verkleinert die Bildschirmprojektion um höchstens 8 %.
Der seitliche Weg folgt dem Arbeitsfenster und bleibt auf höchstens 2,5 % der
Bühnenbreite begrenzt. Er wird weiter eingeschränkt, wenn Posenreichweite oder
Dialogkarte das erfordern. Die Prüfung umfasst die Zwischenstrecke einschließlich
des Übertritts an der Kartenecke. Ein unmöglicher Fit erzeugt keine zusätzliche
Kamerafahrt. Der obere Weltrand der Ruheprojektion bleibt gleich.

`ContinuumHologramView` reicht genau dieselbe Zwischenprojektion an Aura und
`FilamentAvatarRenderer` weiter. Es gibt keinen zweiten Animationstimer.
Die originale GLB, Pose-/Halsaufteilung, Kugel und Kopfbühne sind unverändert.
Die Fahrt steuert die virtuelle 3D-Ansicht, keine Gerätekamera.

## Prüfgrenzen

- Geometrie: tatsächlich paketierte Original-GLB, unabhängiges Skinning von
  15 Posen, Zwischenpositionen, Fold-Innen-/Außenmaße, Hoch-/Querformat,
  Tablet und Schriftfaktoren 1/2. Bestehendes Schweben separat berücksichtigt.
- Activity: echte `AgentActivity`, echte Systemkachel und echtes Arbeitsfenster;
  die Projektionsfixture injiziert validierte GLB-Metadaten. Sie führt weder
  Filament noch eine native GPU aus.
- Lifecycle: tatsächlicher STOP-Aufrufpfad, Pause/Resume, Animationseinstellung
  und View-Fokus-Callback. Robolectric erhält den Fokus-Callback ausdrücklich
  über `dispatchWindowFocusChanged`; keine Behauptung über Samsung-Fold-Ereignisse.
- Regression: vorhandene Avatar-, Fenster-, Scroll-, Header- und Decktests,
  einschließlich der von #565 übernommenen Touch-/TalkBack-Prüfungen.
- Physischer Fold7, native Filament-Bilder, Bildrate und thermisches Verhalten:
  **NOT_RUN**, kein angeschlossenes ADB-Gerät.

## Verlauf

| Lauf | Ergebnis | Bedeutung |
| --- | --- | --- |
| `camera-red` | Testquelltext kompilierte nicht | Eigener Kotlin-Cast-Fehler; kein App-RED. |
| `camera-red-executed` | 1 Test, 1 erwarteter Assertion-Fehler | Arbeitsfenster bewegte die Kopfprojektion noch nicht. |
| `camera-green` | 19/19 bestanden | Erster Nachweis einschließlich zuvor rotem Activity-Test. |
| `camera-drift-and-corner` | 5/5 bestanden | Original-Schweben und riskante Zwischenstrecke. |
| `camera-final` | 113/120 bestanden, 7 Fehler | Sechs überholte reflektierte Fensteraufrufe; ein nicht zugestellter Fokus-Callback in der neuen Fixture. |
| `camera-fixture-fixes` | 8/8 bestanden | Typisierter Fensteraufruf und tatsächlicher View-Fokus-Dispatch; Prüfungen beibehalten. |
| `camera-final-verified` | 120/120 bestanden, NewApi ohne Befund | Vollständiger Abschluss der betroffenen Suite auf `c9984b3`, keine Fehler oder Skips. |

Die historischen Läufe sind keine addierbare Zahl verschiedener Tests.
`red-receipt.json`, `first-green-receipt.json` und `checkpoint-receipt.json`
halten ihre jeweiligen Quellbindungen und Ergebnisse fest.
Der Abschlusslauf ersetzt keine vollständige Ausführung aller App-Tests.

## Reproduktion

JDK 17, Android SDK 36, Gradle 8.11.1 gemäß Wrapper. Der verwendete Host hatte
die Abhängigkeiten bereits im Cache und lief mit `--offline`, UTF-8-Locale,
zwei Gradle-Workern und einem Test-Fork. `host-tests.init.gradle` ist bytegleich
zum Init-Skript des Abschlusslaufs; es begrenzt Ressourcen und meldet Ergebnisse.

```bash
./gradlew --no-daemon --max-workers=2 --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx1800m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -I docs/evidence/astra-camera-flight-20261106/host-tests.init.gradle \
  -PcontinuumUiWidgetTests=true :app:testDebugUnitTest \
  --tests 'at.heinz.deviceai.avatar3d.*' \
  --tests 'at.heinz.deviceai.ContinuumSystemStatusWorkWindowUiTest' \
  --tests 'at.heinz.deviceai.ContinuumAuraLifecycleUiTest' \
  --tests 'at.heinz.deviceai.ContinuumAvatar3dLifecycleUiTest' \
  --tests 'at.heinz.deviceai.ContinuumHologramMotionUiTest' \
  --tests 'at.heinz.deviceai.JarvisWorkWindowLifecycleUiTest' \
  --tests 'at.heinz.deviceai.JarvisWorkWindowUiTest' \
  --tests 'at.heinz.deviceai.ContinuumWorkWindowScrollUiTest' \
  --tests 'at.heinz.deviceai.ui.JarvisHeaderBrandFitTest' \
  --tests 'at.heinz.deviceai.ui.JarvisDeckClearanceTest' \
  :app:lintDebug
```

Das vorhandene Lint-Gate prüft `NewApi` mit `abortOnError=true`; es ersetzt
keine vollständige Prüfung sämtlicher Lint-Kategorien.

## Nächster Einstiegspunkt: Fold7

Mit separat freigegebenem Gerätebuild desselben Quellstands die native
3D-Ansicht innen und außen prüfen: Systemfenster öffnen/schließen, während
der Fahrt STOP auslösen, Hintergrund/Vordergrund, Falt-/Drehwechsel,
deaktivierte Animationen und große Schrift. Kopf und Aura müssen gemeinsam
fahren; Gesicht und Dialog bleiben getrennt; nach Abbruch kein Nachholen.
Commit, Geräte-/Android-Version und Beobachtungen mit Aufnahme festhalten.
Diese Arbeit enthält keine APK, Signierung, Installation oder Main-Integration.

Rückfall: die fünf Produktionsdatei-Deltas dieses PRs gegen #565 zurücknehmen;
Original-GLB und bestehendes Arbeitsfenster bleiben erhalten. Keine fremden
Commits oder Branches zurücksetzen.
