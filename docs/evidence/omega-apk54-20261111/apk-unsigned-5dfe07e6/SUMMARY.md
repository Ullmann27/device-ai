# Code54-Kandidat `5dfe07e64` – UI-Pflichtlauf, Lint, unsignierter APK (HOST)

- Quelle `5dfe07e64` (= `integrate/omega-apk54-20261111`, enthält den Fix der Regression `M3EvalGapsTest`), sauberer Baum.
- UI-Pflichtlauf (3 Klassen): **25/25 PASS**, `suite_end`; NewApi-Lint: **No issues found** → `../android-ui-recheck-5dfe07e6/`.
- APK: `assembleRelease`, versionCode 54 / 0.3.53, unsigniert; Validator-Hash vorab per `sha256sum -c` geprüft (`e70a2470…`),
  Aufruf über `../apk-unsigned-f0120e1b/run-validator54.sh` (Quelle/Baum zur Laufzeit injiziert).
  Ergebnis **PASS**, `UNSIGNED_NOT_INSTALLABLE`, **SHA-256 `7f463d4785466302aa188b8e81b5e75f32393654cf1ebde16c06c1845649127c`**.
  Ersetzt den überholten APK `ae99a1db…` aus `f0120e1b2`.
- Offen: volle Android-Suite auf `5dfe07e64` (läuft), Originalsignatur (nur mit SHA-genauer Besitzerfreigabe), Installation, Fold7.
