# Code54 – unsignierter APK-Kandidat aus `f0120e1b2` (HOST-Bau)

- Quelle `f0120e1b231efc31237a0a13aa59f9cbcc3c490a`, sauberer Arbeitsbaum.
- Bau: `./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true
  -PcontinuumOwnerArm64=true -PcontinuumStandaloneVersionCode=54 -PcontinuumLocalRenderUnsigned=true :app:assembleRelease`
  (Signiervariablen vorher entfernt) → BUILD SUCCESSFUL.
- Validierung: Code53-Validator (`67bf048a`, SHA-256 `2a0aeaef…`), davon abgeleitete Code54-Fassung, in der **nur** die
  Versionskonstanten 53/0.3.52 → 54/0.3.53 geändert sind (`validator54-vs-53.diff`). Quelle und Baum (`f0120e1b…`/`24cf0c3c…`)
  werden – wie im Code53-Workflow – zur Laufzeit injiziert (`run-validator54.sh`); die Konstanten `a980a19e…`/`a9d3da18…` in der
  Datei sind nur Vorgaben und werden überschrieben. `validatorSha256` in `RESULT.json` ist der Hash der unveränderten Datei. Ergebnis **PASS**,
  `artifactStatus: UNSIGNED_NOT_INSTALLABLE`.
- **APK SHA-256: `ae99a1dba263a034c886381e0c8a1e0618a378a31a719562d3b2fa7ceb2d79fe`** (Datei nicht im Repository).
- **Nicht installierbar** (unsigniert). Originalsignierung nur über den geschützten Weg mit SHA-genauer Besitzerfreigabe – nicht erfolgt.
- Offen vor einer Release-Aussage: volle Android-Suite auf derselben Quelle, strikter nativer PDF-Test (auf dem Host
  unmöglich: `FileDescriptor.getOwnerId$`), Originalsignatur, In-Place-Installation, Fold7-Abnahme.
