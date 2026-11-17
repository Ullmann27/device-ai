# Code55: unsignierte APK prüfen

Strenger, ausschließlich lesender Prüfer aus
[`67bf048a7d50a78b027da27a6b9814cbd2457372`](https://github.com/Ullmann27/device-ai/blob/67bf048a7d50a78b027da27a6b9814cbd2457372/docs/qa/apk53-a980/validate-unsigned-apk.py).
Erwartet fest: Paket `at.heinz.deviceai.continuumlocal`, Code **55**, Version
**0.3.54**, minSdk **26**, targetSdk **36**, ausschließlich **ARM64**.

```bash
python3 tools/code55-apk/validator.py "$CODE55_APK" "$CODE55_REPORT" \
  --repo "$CODE55_REPO" \
  --build-tools "$CODE55_BUILD_TOOLS" \
  --java-home "$CODE55_JAVA_HOME" \
  --expected-source "$CODE55_APPROVED_SOURCE_SHA" \
  --expected-tree "$CODE55_APPROVED_TREE_SHA"
```

Quellcommit und Tree sind Pflichtargumente mit jeweils vollständigen,
kleingeschriebenen 40-stelligen Git-IDs. Sie müssen vorab unabhängig als
Buildgrundlage festgelegt werden. Der Prüfer leitet keine Erwartung aus `HEAD`
ab und akzeptiert weder Branch-/Tag-Namen noch verkürzte IDs. Das Repository
muss vor und nach der Prüfung exakt dazu passen und sauber sein, einschließlich
nicht ignorierter neuer Dateien. Den Prüfer vor dem finalen Lauf committen.

Alle Pfade explizit übergeben. `CODE55_REPORT` muss ein neues Verzeichnis
außerhalb des Repositorys sein; sein Elternverzeichnis muss vorhanden sein.
Vorhandene Berichte werden nicht überschrieben. Python 3.9+, Git, JDK und
Android-Build-Tools mit `aapt2`, `zipalign` und `apksigner` müssen bereits
vorhanden sein. Der Prüfer baut, installiert und signiert nichts.

## Unveränderte Gates

- ZIP: eindeutige sichere Einträge, keine Symlinks, CRC, Manifest/Ressourcen/DEX.
- Exakte fünf ARM64-Bibliotheken; ELF64 LE, AArch64, ET_DYN, gültige LOAD-Segmente
  und mindestens 16-KiB-Ausrichtung; zusätzlich `zipalign -c -P 16 -v 4`.
- Jede Quelldatei aus beiden Assetwurzeln muss bytegenau in der APK liegen.
  **Keine Asset-Ausnahmen oder neuen Ausschlussmuster.** Die aus dem Lockfile
  festgelegten YuNet-/SFace-Dateien werden zusätzlich auf Größe und SHA geprüft.
- Originalavatar bleibt derselbe Git-Blob wie in
  `17491f4d8103f0e55836369333fab07e80e6e9c2`; außerdem Assetvergleich in der APK.
- Exakte Paket-/Versions-/SDK-/ABI-Werte und nicht debuggable.
- Kein JAR-Signaturmaterial, kein APK-Signing-Block; `apksigner verify` muss
  ausschließlich die ausdrücklich zugelassenen Unsigned-Diagnosen liefern.
- Unveränderte APK-Datei und identischer sauberer Quellzustand vor/nach dem Lauf.

Erfolg: Exitcode 0 und `RESULT.json` mit `PASS` sowie
`UNSIGNED_NOT_INSTALLABLE`. Eine fehlgeschlagene Prüfung liefert Exitcode 1.
CLI-Fehler, fehlende/regulär ungeeignete APK oder verbotener Ausgabeort liefern
Exitcode 2. `RESULT.json` dokumentiert begonnene Prüfungen; Inventare und Logs
entstehen, soweit die jeweilige Prüfung erreicht wurde. Nur Erfolg erzeugt
`SHA256SUMS.txt`. Filament/Fold7/GPU bleiben `NOT_RUN`.

Selbstprüfung am 12.11.2026: Syntax gültig; 48 gezielte Kontrollen erfolgreich,
einschließlich negativer CLI-, Quell-, ZIP-, Asset-, ELF- und Signaturfälle.
Acht geerbte Gate-Funktionen sind byteidentisch zur Ursprungsdatei.
Diese Selbstprüfung ist keine Prüfung der fertigen Code55-APK.

Reproduktion derselben 48 Fälle:

```bash
python3 tools/code55-apk/validator_test.py
```

Der Prüflauf benötigt Python und das lokale Git-Ursprungsobjekt. Er nutzt
synthetische ELF-/ZIP-/Assetdateien; die Quellidentität wird kontrolliert
simuliert. Temporäre Dateien werden entfernt. Die Ausgabe enthält alle
Fallnamen und den geprüften Validatorhash.
`validator-selfcheck-20261112.json` sichert die bereits beobachtete Quittung;
das nachträglich gespeicherte Script wurde nur syntaxgeprüft, nicht erneut
ausgeführt. Die Quittung benennt diese Grenze und die drei mechanischen
Anpassungen beim Speichern.
