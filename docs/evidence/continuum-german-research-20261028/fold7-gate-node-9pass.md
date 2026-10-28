# Fold7-Haupt-App Gate – nachweislich ausgeführte Tests

Datum 28.10.2026 · Ausgeführt **in einem isolierten Linux-Container**, nicht in Termux oder auf dem Fold7.

## Exakte getestete Original-Dateien

Die Dateien wurden aus den neu angelegten GitHub-Quellen in den lokalen Prüfcontainer übertragen und per `git hash-object` bytegleich zu den mit `fetch_file` unabhängig gemeldeten GitHub-Blobs abgeglichen.

- `scripts/continuum-fold7-owner-update-gate.sh`: `1ea7643fe0a030de455f86fffdea1bf465031b57`
- `scripts/continuum-fold7-owner-update-gate.test.mjs`: `d1abc9cd0379a0455d1d1fbd681244920c151fbe`

Toolchain: Bash, Git und Node v22.16.0 (Linux x86_64). Diese Toolchain ist **keine** Android-/Termux-Gradle-Toolchain.

## Tatsächliche Befehle, Exitcodes und Ergebnis

```text
git hash-object continuum-fold7-owner-update-gate.sh
1ea7643fe0a030de455f86fffdea1bf465031b57
git hash-object continuum-fold7-owner-update-gate.test.mjs
d1abc9cd0379a0455d1d1fbd681244920c151fbe
bash -n continuum-fold7-owner-update-gate.sh
EXIT_CODE=0; BASH_SYNTAX_PASS
node --test continuum-fold7-owner-update-gate.test.mjs
TAP version 13
# tests 9
# suites 0
# pass 9
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 379.164316
EXIT_CODE=0
```

Die neun **tatsächlich ausgeführten** Node-Tests decken ab: Bash-Syntax, unbekannte Befehle/ungültige SHA, fehlenden privaten Checkout, falschen HEAD, fremden Git-Remote, schmutzigen Git-Stand, reinen Doctor-Aufruf, das Entfernen der Besitzer-Signierumgebung für QA, sowie Sperren von Original-Signierung ohne explizite Freigabe, Versionscode, private P12 und Kennwort.

Die isolierten Tests verwenden einen **temporären simulierten Git-Checkout mit ersetzten Builder-Skripten**, damit kein Besitzer-Key gelesen, keine APK erzeugt und keine echte Installation verändert wird. Sie beweisen nur die Ein-/Ausgangs- und Sicherheitslogik dieses Shell-Orchestrators.

## Nicht ausgeführt – ausdrücklich nicht beansprucht

- Vollständige `:app:testDebugUnitTest`-Regression auf dem PR-#205-HEAD: **NOT_RUN**
- Gradle/Kotlin Android-Haupt-App Build, Lint und `:app:assembleDebug`: **NOT_RUN**
- Termux ARM64 / reales Samsung Fold7: **NOT_RUN**
- Gemma-Inferenz, realer Online-Webabruf und Vorher-Nachher-Qualitätsvergleich: **NOT_RUN**
- Besitzer-PKCS12/Originalsignatur und installierbare Haupt-App: **BLOCKED / NOT_BUILT**

Grund: aktuelle Sitzung besitzt keinen authentifizierten Geräte-Shell-Zugang, keinen privaten Original-P12-Schlüssel und keine Android SDK36/JDK17/Gradle8.11.1-Buildumgebung. Screenshot des Besitzers zeigte Termux ohne aktiven Git-Checkout. Der zulässige nächste Schritt ist die einmalige private GitHub-Anmeldung und der offizielle Fold7-Checkout, dann `doctor`, vollständige QA und erst nach Signier-/Versionsprüfung ein Besitzerbuild.

**Keine Debug-APK installieren, keine bisherige App deinstallieren.** Keine Änderung an `main` oder dem installierten Besitzerpaket.
