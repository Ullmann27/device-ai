# Continuum – M1: Aktuellen Integrationsstand direkt auf Android bauen

Stand: 21.10.2026 | Bezug: EXP-00 / EXP-08 / EXP-12 | PR #48 (Draft)
Status: **QUELLEN GEHÄRTET, GERAETEABNAHME AKTUELLER BUILD OFFEN**.
Dies ist kein Nachweis, dass der neue Code bereits auf dem Samsung gebaut wurde.

## Gesicherte Basis

- Android-Gerät: Samsung / Android 16 / ARM64 mit bestehender Termux-Anbindung.
- Termux-DOCTOR aus Besitzer-Screenshot: Git, Java 17, Gradle 8.11.1, ARM64-AAPT2, SDK 36 und apksigner jeweils true.
- Älterer realer Buildbericht auf dem Gerät: Quellcommit `91291cb201d140bc301bcb341b49a674d8d08165`, `EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED`, Paket `at.heinz.deviceai.preview`, APK im privaten Termux-State und owner Download-Kopie. Dieser Bericht ist **nicht der aktuelle Integrations-Build**.
- Aktuelle Samsung-App: `at.heinz.deviceai.mentorpaired`, Version 0.3.10, unabhängig über signierten Render-Build installiert; Lern-/Checkpoints im privaten App-Speicher bleiben geschützt.
- Für M1 zu testender **geprüfter Code-Checkpoint**: `55338344654d3c5925b067a6dec82c21e745b408`. Der danach ggf. neuere Branch-HEAD durch Dokumentation ist nicht automatisch derselbe Code-Checkpoint.
- `scripts/continuum-mobile-builder.mjs`: Git-HEAD/clean-Status vor und nach Gradle, gemeinsame Build-Lease, echte `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`-Gradle-Gates, native AAPT2-SDK-36, Signatur/Signer-Digest, `aapt2 dump badging` erwartet `at.heinz.deviceai.preview`, SHA-256, privates signiertes Artefakt und optional kontrollierte Download-Kopie. Keine App-Installation und keine Git-Mutation im Builder.
- Neue Code-Sicherheitsreparatur: Publikation mit `fs.copyFileSync(...,fs.constants.COPYFILE_EXCL)` statt `renameSync` auf bestehenden owner Downloadpfad, damit andere Dateien bei Rennbedingungen niemals ersetzt werden. Bei abweichendem Endhash wird nicht die möglicherweise fremde Datei gelöscht.
- Regressionen für konkurrierenden Dateinamen und unvollständige Download-Kopie sind eingecheckt. **Nach diesen zwei Commits noch kein Testlauf in dieser Sitzung unabhängig ausgeführt.**

## Ausführungsfolge auf genau diesem Samsung

**Keine Git-/Dateiänderungen aus dem Chat automatisch durchführen. Besitzer führt diese Kommandos selbst in der bestehenden Termux-Sitzung aus. Keine zweite Termux-Installation.**

### 1. Nur lesende Quellenprüfung (vor Fetch oder Checkoutwechsel)

```sh
(
  set -e
  cd "$HOME/device-ai"
  git status --short --untracked-files=normal
  git remote get-url origin
  git rev-parse HEAD
)
```

Abbruch, wenn `git status` unbekannte/uncommittete Dateien meldet; Besitzerzustand weder resetten noch staschen. `origin` muss das bekannte private Repository `Ullmann27/device-ai` auf GitHub sein.

### 2. Exakt bekannte Quelle holen, ohne alten Commit oder Besitzerarbeit zu verwerfen

```sh
(
  set -e
  cd "$HOME/device-ai"
  case "$(git remote get-url origin)" in
    https://github.com/Ullmann27/device-ai|https://github.com/Ullmann27/device-ai.git|git@github.com:Ullmann27/device-ai.git|ssh://git@github.com/Ullmann27/device-ai.git) ;;
    *) echo "STOP: unerwartetes origin-Repository"; exit 1 ;;
  esac
  test -z "$(git status --porcelain --untracked-files=normal)" || {
    echo "STOP: zuerst lokale Änderungen sichern und prüfen"; exit 1;
  }
  git fetch --no-tags origin refs/heads/integration/continuum-astra-20261021
  test "$(git cat-file -t 55338344654d3c5925b067a6dec82c21e745b408)" = commit
)
```

Erwartete Ausgabe von `cat-file`: `commit`. Der Fetch verändert keine bestehenden App-Gewichte. **Bevor** `git switch` ausgeführt wird: sauberen Arbeitsstand und erwartete Remote-URL prüfen.

```sh
(
  set -e
  cd "$HOME/device-ai"
  test -z "$(git status --porcelain --untracked-files=normal)" || {
    echo "STOP: lokale Änderungen schützen"; exit 1;
  }
  git switch --detach 55338344654d3c5925b067a6dec82c21e745b408
  test "$(git rev-parse HEAD)" = 55338344654d3c5925b067a6dec82c21e745b408
  echo "M1-Quellstand stimmt mit dem geprüften Code-Checkpoint überein"
)
```

Erwartetes HEAD exakt `55338344654d3c5925b067a6dec82c21e745b408`. `git switch --detach` verändert nicht `main` und erstellt keinen neuen Branch, arbeitet aber am Termux-Checkout; bei lokalen Veränderungen darf der Wechsel **nicht** erfolgen. Der Buildvorgang selbst bleibt git-lesend.

### 3. Lokale Tests zuerst (mit echtem Exitcode)

```sh
(
  set -e
  cd "$HOME/device-ai"
  node --test scripts/continuum-mobile-builder.test.mjs
  node scripts/continuum-mobile-builder.mjs doctor
)
```

Die neuen Rennbedingungs-/Integritätstests müssen ohne Fehlschlag durchlaufen. Doctor muss `READY_FOR_EXPERIMENTAL_BUILD` liefern. Bei Problemen **nicht** APK-BUILD drücken, sondern Ausgabe sichern.

### 4. Den aktuellen Build AUS DER APP anstoßen (dieser Schritt ist noch offen)

Bestehende **Device AI Mentor Pair 0.3.10** öffnen → Handy-Builder / Termux → **APK auf dem Handy bauen** → explizit mit „Bauen“ zustimmen. Keine zweite parallele App-/CLI-Ausführung starten; Termux kann bis 35 Minuten brauchen. Android-Bereitschaft, Bildschirm/Batterie/Temperatur beachten. Wenn der Callback ankommt, in der App `Ergebnis in der App aktualisieren` wählen.

Nach dem Callback darf `BUILD_REPORTED_UNVERIFIED_ON_DEVICE` nur erscheinen, wenn Exitcode 0, gültige Builder-Status-/Hashdaten und zusammenhängender Rückkanal vorhanden sind. Bei `INVALID_RESULT`, Wartezustand oder `FAILED_*` **nicht** M1 als erreicht markieren.

### 5. Unabhängige lesende Kontrolle im Termux-Checkout

```sh
(
  set -e
  cd "$HOME/device-ai"
  git rev-parse HEAD
  node scripts/continuum-mobile-builder.mjs status
)
```

Der Bericht muss `commit: 55338344654d3c5925b067a6dec82c21e745b408`,
`status: EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED`,
einen vollständigen `apkSha256`, `signerSha256`,
`packageName: at.heinz.deviceai.preview`,
und `ownerInstallCopy.status: COPIED` (oder klar benannten sicheren Fehler) enthalten. Die Quelle muss weiterhin clean sein, der Hash mit dem privaten Buildartefakt übereinstimmen.

**Wichtig:** `at.heinz.deviceai.preview` ist eine gesonderte experimentelle Test-App und **kein** direktes Update der installierten `at.heinz.deviceai.mentorpaired` 0.3.10. Preview NICHT automatisch installieren und nie eine Deinstallation zum Update erzwingen. Auch ein korrektes gebautes APK beweist noch keine In-App-Gerätefunktion.

## M1-Gates und Abschlussregel

1. *DEVICE_TOOLCHAIN_VERIFIED*: durch Benutzerbeleg für Termux Doctor erfüllt.
2. *REAL_APK_BUILT_ALT*: lokaler signaturgeprüfter Build für Commit `91291cb2...` vom Besitzer belegt; als HISTORISCH markieren.
3. *CURRENT_SOURCE_SAFE*: aktueller Quellcode im Reviewbranch mit Rennbedingungsfix und Negativtests; Node-Ausführung auf Samsung noch ausstehend.
4. *CURRENT_APP_TO_TERMUX_BUILD*: **OFFEN**, nur mit realem App-Button-BUILD und Callback vom Samsung abschließbar.
5. *SOURCE_HASH_SIGNER_MATCH*: **OFFEN**, muss aus tatsächlichem, auf den aktuellen HEAD gebundenem Bericht kommen.
6. *DEVICE_INSTALL_TEST*: getrennte spätere Eigentümerfreigabe, für M1 ist eine Vorschau-APK ohne automatische Installation zulässig.

**M1 ist erst abgeschlossen**, wenn 3 bis 5 belegbar erfüllt sind. Ein grüner Render-APK-Build oder der ältere Termux-Commit ist kein Ersatz.

## Rollback / Grenzen

- `main` wird nicht verändert. Review bleibt Draft-PR #48.
- Keine fremden Modellbefehle/Shell-Ausdrücke direkt ausführen. Kein generatives Modell muss beim M1-Builder installiert sein.
- Kein `git reset --hard`, `git clean -fd`, `git stash`, Löschen/Zurücksetzen von SWB1-/CDX1-Daten oder automatisches APK-Installieren.
- Zurück zum vorherigen Checkout nur nach Dokumentation der ursprünglichen HEAD- und Branch-Angaben (`git switch` oder `git switch --detach OLD_HEAD` bei clean Status). Bereits erstellte APK-Berichte bleiben im Termux-State.
