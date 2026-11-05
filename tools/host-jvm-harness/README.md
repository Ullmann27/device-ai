# Host-JVM-Prüfumgebung (ohne Android-SDK)

Zweck: Den vollständigen App-Hauptcode (`app/src/main/java`, 436 Kotlin/Java-Dateien) und die gesamte Unit-Suite
(`app/src/test/java`, 502 Dateien, 2.639 Tests) dort kompilieren und ausführen, wo `dl.google.com` gesperrt ist
und daher weder Android-SDK noch AGP verfügbar sind.

Das ist **kein Ersatz** für `./gradlew testDebugUnitTest`, Lint, APK-Bau oder Geräteprüfung. Es ist ein reproduzierbarer
Vorab-Nachweis, dass eine Änderung kompiliert und im Vergleich zur unveränderten Basis keine Unit-Tests verschlechtert.

## Was ersetzt wird

| Original | Host-Ersatz | Grenze |
|---|---|---|
| `android.jar` (SDK 36) | `org.robolectric:android-all:15-robolectric-12650502` nur `compileOnly` | Keine Android-Laufzeit in Tests; Aufrufe ins Framework scheitern wie bei nicht gemockten Stubs |
| generiertes `R`, `BuildConfig` | `stubs/at/heinz/deviceai/HostStubs.kt` | Nur benutzte IDs, Werte bedeutungslos |
| AIDL `IDeviceShell` | `stubs/at/heinz/deviceai/IDeviceShell.java` | Kein Binder-Verhalten |
| `androidx.annotation` | zwei Annotation-Stubs | — |
| LiteRT-LM-AAR (maven.google.com → dl.google.com gesperrt) | `stubs/com/google/ai/...` und `LiteRtLmHostRuntime.kt` | Echte Laufzeit fehlt; zwei Reload-Tests scheitern deshalb immer |

Die Plattform-Nullbarkeitsannotationen von `android-all` werden ignoriert
(`-Xnullability-annotations=@android.annotation:ignore`), weil `android.jar` des SDK sie anders ausweist.

## Ablauf

```bash
H=tools/host-jvm-harness; L=/pfad/zu/libs   # android-all.jar, filament.jar, gltfio.jar, shizuku-*.jar, okhttp.jar, okio.jar
find app/src/main/java -name '*.kt' -o -name '*.java' | grep -v localmodel/LiteRtLmLocalModelRuntime.kt > /tmp/main.txt
find app/src/test/java -name '*.kt' > /tmp/test.txt
(cd $H && gradle --no-daemon test -Prepo=$PWD/../.. -Plibs=$L -PmainList=/tmp/main.txt -PtestList=/tmp/test.txt)
```

Bezugsquelle der Jars: `https://maven-central.storage-download.googleapis.com/maven2/` (Spiegel von Maven Central;
`repo1.maven.org` drosselt große Dateien mit 429). AARs werden entpackt (`classes.jar`).

## Ergebnis am 05.11.2026

Basis #526 `5a18f74` und Kandidat mit deutscher Oberfläche: je **2.639 Tests, 17 übersprungen, dieselben 9 Fehler**.
Alle 9 sind Umgebungsgrenzen (LiteRT-Stub, fehlende Android-Laufzeit, root ignoriert Schreibschutz,
Standardzeichensatz, fehlender Benchmark-Fixturepfad). Details:
`docs/evidence/design-de-20261105/host-tests/host-unit-baseline-vs-candidate.json`.
