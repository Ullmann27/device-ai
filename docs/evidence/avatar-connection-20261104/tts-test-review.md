# Unabhängige Gegenprüfung der TTS-Anschlusstests

Datum: 2026-11-04. Reviewer: `avatar_patch_review`.

**Aktueller Stand: statischer REVIEW-PASS für den korrigierten Fixture-Stand.**
Im abschließenden Diff bestehen keine verbleibenden Critical- oder
Important-Befunde. Der frühere statische PASS hatte die unterschiedliche
Uhrdomäne der Fixture übersehen; der anschließende tatsächliche Lauf war rot.
Dieser Bericht hält den Befund und die notwendige Korrektur ausdrücklich fest.
Er behauptet keinen Ausführungs-PASS des korrigierten Tests.

Geprüfte Datei:
`app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt`

| Stand | Originaler Autor-Commit | Testblob | SHA-256 |
| --- | --- | --- | --- |
| Erster statischer Review, vor Ausführung | `c1ecd19` | `4e3d8c77b252c68872d7d4b7037e7c7f3ea8a77c` | `76cbfe2756f99a4294f8ec9d3f59c11b70a66845b33f263176bb5183d9d76b6f` |
| Abschließend geprüfter Clock-Fix | `2a9c0e404d5b1cd09e060136befdb64ce88577fe` | `932f92c6f2eabfc7c60a7617d132b72a399ec817` | `f067041dfbb12e29abf4c5b004acd7de2bb94d2a70cb3f96403549e6d02a96b6` |

Diese SHAs sind die ursprünglichen lokalen Autorenstände. Root dokumentiert
seine integrierten beziehungsweise veröffentlichten SHAs gesondert. Der
Reviewer hat keine Produktionsquellen geändert und keine eigenen Builds oder
Tests gestartet.

## Tatsächlich gemessener Fixture-Fehler

Root führte die Diagnose auf sauberem Quellstand
`f6c3bdbcc1b9b1c5830c0c72763dd1e2bd727283`, Tree
`6ca6989d688065ffa9c4deaad2a18ff6bf1c58c4`, aus: **3 Tests, 3 Fehler, 0 Errors,
0 Skips**. Die archivierten Originaldaten stehen unter
`clock-diagnostic-run/summary.json`, `gradle.log` und dem JUnit-XML. Dessen
`TTS_CLOCK`-Zeilen zeigen:

| Callbackabschnitt | JVM-Wandzeit fortgeschritten | Android elapsed/uptime fortgeschritten | Gemeldete Energie danach |
| --- | --- | --- | --- |
| Erster aktueller Auftrag | 59 ms | 171 ms | 0,32 |
| Vorheriger Auftrag | 24 ms | 171 ms | 0,32 |
| Neuer aktueller Auftrag | 12 ms | 171 ms | 0,20 |
| Später verschobene Oberfläche | 10 ms | 171 ms | 0,32 |

Die Frame-Planung verwendete somit virtuelle Android-Zeit, während die
Default-Uhr des uninstrumentierten `AvatarDirector` und der bisherige
Test-Pose-Aufruf echte JVM-Zeit lasen. Mehrere virtuelle Frames erhielten dadurch
gleiche oder beinahe gleiche Audiozeitstempel. Für die um 160 ms versetzten
Viseme war zu wenig Ereigniszeit vergangen. Die gemessenen Folgen waren die
fehlende PCM-Energieschwelle beziehungsweise SIL statt AA/O. Das ist ein
nachgewiesener Fehler der neuen Fixture; der Lauf belegt damit keinen neuen
Produktionsfehler der Android-Leitung.

## Notwendige und gegengeprüfte Uhrkorrektur

Commit `2a9c0e404d5b1cd09e060136befdb64ce88577fe` ändert ausschließlich die
Testdatei. Die Fixture instrumentiert gezielt die Präfixe
`at.heinz.deviceai.avatar.AvatarDirector` und
`at.heinz.deviceai.ContinuumHologramView`; der Test-Pose-Aufruf liest ausdrücklich
`ShadowSystem.currentTimeMillis()`.

Die Begründung wurde zusätzlich an den tatsächlich verwendeten
Robolectric-4.14.1-JARs und den kompilierten Appklassen mit `javap` überprüft:

- `InstrumentationConfiguration.isInInstrumentedPackage` prüft `startsWith`.
  Damit wird auch `AvatarDirector$1`, die vorhandene Default-Clockfunktion,
  erfasst. Ihr ursprüngliches Bytecode-`invoke` liest `System.currentTimeMillis`.
- `SystemTimeInterceptor` leitet instrumentierte Zeitaufrufe an `ShadowSystem`
  weiter. Dessen `currentTimeMillis` verwendet im PAUSED-Modus die virtuelle
  `SystemClock.uptimeMillis`-Zeit; `ShadowPausedLooper.idleFor` bewegt diese Uhr.
- Mit `@RunWith` markierte Testklassen werden ausdrücklich nicht instrumentiert.
  Der explizite ShadowSystem-Aufruf im Test ist deshalb notwendig. Die View ist
  ebenfalls erfasst, damit ihr Produktions-Renderzeitleser derselben Uhr folgt.

Die Fixture liest die tatsächliche bestehende Director-Clock nur per Reflection.
Sie prüft ihre Gleichheit mit der kontrollierten Uhr vor und nach `idleFor(33)`
sowie den erwarteten Fortschritt um 33 ms. Es werden weder Clockfeld noch
Directorinstanz, Sink oder Produktionsleitung ersetzt. Alle ursprünglichen
PCM-, AA/O-, Oberflächen-, ID- und Lifecycle-Assertions bleiben unverändert;
die vorübergehenden Diagnoseausgaben wurden entfernt.

## Geprüfte Callback-Fälle

1. **PCM, Lautform und Ende:** Die Fixture nimmt die von `AgentActivity`
   erstellte `TextToSpeech`-Instanz. Deren tatsächlicher Initialisierungslistener
   durchläuft `onLocalVoiceInitialized` und installiert den Produktions-
   `UtteranceProgressListener`. Synthetische PCM-Callbacks werden über diesen
   Listener zugestellt. `onStart` muss den Produktions-`voiceFrame` selbst posten;
   die Handler-Warteschlange führt die positiven Frames aus. Vor jedem Range-
   Callback verlangt der Test SPEAKING, SIL, offene Kiefer und Energie über 0,35.
   Damit kann Wortbereichsbewegung den fehlenden PCM-Anschluss nicht verdecken.
   Erst danach prüft er den Range-Callback und schließlich `onDone`.
2. **Fremde und abgeschlossene IDs:** Alte Start-, Synthese-, Audio-, Range-,
   Ende-, Stop- und Fehlercallbacks dürfen die aktive Pose nicht verändern.
   Nach dem Ende dürfen verspätete Callbacks die Mundbewegung nicht wieder starten.
3. **Reparenting und Pause:** Dieselbe sichtbare `ContinuumHologramView` wird in
   den Chat verschoben. Frische Callbacks müssen ihren bestehenden Reducer weiter
   erreichen. Pause und verspätete Callbacks werden auf Stille geprüft. Der einzige
   direkte `voiceFrame.run()` erfolgt nach der Pause als Negativreiz.

Im positiven Pfad ersetzt kein direkter `AvatarDirector`-Aufruf und kein direkt
eingespeistes View-Ereignis die zu prüfende Leitung. Die Fixture prüft außerdem
Oberflächenidentität, Sichtbarkeit und genau eine Hologramm-View.

## Begründete Leitungs-Mutation

Nur in einem isolierten Mutationsbestand soll ausschließlich
`avatarDirector.audioLevel(it, level)` aus dem Produktions-`voiceFrame` entfernt
werden. `hologramView?.setAudioLevel(level)` bleibt erhalten. Das PCM erreicht
dann weiterhin die separate Orb-Hüllkurve, aber keine Audioenergie den
Avatar-Reducer. Der erste neue Test muss deshalb an seiner Kiefer- oder
Energieprüfung scheitern. Der vorgelagerte SIL-Nachweis schließt einen Ersatz
durch die Wortbereichsannäherung aus.

Die Uhrkorrektur verändert die Aussagekraft dieser Mutation nicht: Sie liefert
keine Audioenergie und keine Ersatzereignisse. Der erste Test verlangt weiterhin
PCM vor jedem Range-Callback, SIL, positive Kieferöffnung und Energie über 0,35.
Die entfernte Leitung kann daher auch mit der korrigierten Uhr nicht durch einen
Visem oder einen künstlich gesetzten Reducerzustand verdeckt werden.

Diese Mutation und die Tests wurden vom Reviewer nicht ausgeführt. Root führt
die Negativkontrolle nach dem unveränderten GREEN-Lauf aus und stellt die
Originalquellen anschließend bytegleich wieder her. Tatsächliche Ergebnisse
müssen den gesonderten Ausführungsprotokollen entnommen werden.

## Abnahmegrenzen

Die Fixture setzt einen bereits angenommenen Syntheseauftrag als Zustand voraus.
Sie prüft nicht den vollständigen `speakBootstrap`-Aufruf, die Entscheidung über
die Sprachfreigabe oder einen hörbaren TTS-Dienst. Der Nachweisgegenstand reicht
vom installierten Callback über die geplanten Frames bis zum Reducer der
tatsächlichen sichtbaren View. Native GPU-Pixel, Geräteausführung und hörbare
Ausgabe werden damit nicht behauptet. Die kontrollierte gemeinsame Uhr ist eine
Host-Testkonfiguration und keine Änderung der Zeitquelle auf Android.
Kompilierung, GREEN des korrigierten Stands und die anschließende rote
Leitungs-Mutation sind zum Zeitpunkt dieses fortgeschriebenen statischen Reviews
noch von Root anhand eigener Ausführungsprotokolle zu bestätigen.
