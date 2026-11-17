# Code54-Kandidat `9713854bf` – UI-Pflichtlauf, Lint, unsignierter APK (HOST)

- Quelle `9713854bf` (= `integrate/omega-apk54-20261111`), Baum `b815b5f2`, sauber vor und nach dem Lauf.
  Enthält gegenüber `5dfe07e64` nur die Sicherheitsfixes der Vorschlagsregel in `OwnerDeviceRequestSafety`
  (`7b81e46e8` ich/man-Nebensatz, `676adf440` kein `single()`-Absturz, `9713854bf` Fragewort < Subjekt < Verb).
- UI-Pflichtlauf (3 Klassen): **25/25 PASS**, `suite_end`, `sourceUnchanged: true`; Lint: **No issues found** → `../android-ui-recheck-9713854b/`.
- APK: `assembleRelease`, versionCode 54 / 0.3.53, `at.heinz.deviceai.continuumlocal`, nur arm64-v8a, unsigniert
  (keine Signaturdateien, kein Signaturblock); 16-KiB-zipalign PASS; Validator-Hash vorab per `sha256sum -c` geprüft (`e70a2470…`),
  Aufruf über `../apk-unsigned-f0120e1b/run-validator54.sh` (Quelle/Baum zur Laufzeit injiziert).
  Ergebnis **PASS**, `UNSIGNED_NOT_INSTALLABLE`, **SHA-256 `eda9d67a56d3e1bdc934eee8eda7a5ff9d2ea649f5b767587ef078d8faf840ab`**,
  102 864 175 Bytes. Originalavatar-Blob `3498c003…` unverändert.
- **Ersetzt** den APK `7f463d47…` aus `5dfe07e64`.
- Offen: volle Android-Suite auf `9713854bf` (läuft), nativer PDF-Test (Android-Laufzeit nötig),
  Originalsignatur (nur mit SHA-genauer Besitzerfreigabe), Installation, Fold7, GPU: NOT_RUN.
