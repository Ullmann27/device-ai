# R2 Build – Signing-Abweisung und Android-Prüfweg

Stand: 04.11.2026. Arbeitszweig `repair/round2-build-local`.
Status: `NODE_REGRESSION_PASS / INDEPENDENT_FIX_REVIEW_PASS / FOCUSED_REAL_ROBOLECTRIC_PASS`.
Vollständiger Gradle-Versuch: `BUILD_FAILED_BEFORE_TEST_EXECUTION`; seine JUnit-Fälle
blieben `NOT_RUN`. Der anschließende direkte Robolectric-Lauf hat echte Ergebnisse.

## Echter Robolectric-Nachweis: RED 2 → GREEN 25

Die beiden unveränderten Queue-Gegenproben wurden zuerst auf dem lokalen
RED-Kandidaten `d24ad51b0235826cf0baaa998a2fb4af80a17404` ausgeführt:

- `backgroundCompletionBeforeFirstAttachDoesNotWaitForAttachment`: erwartete
  0 Karten, tatsächlich 1; der Abschluss wartet fälschlich auf ein späteres Attach.
- `detachedSubscriberRetiresBackgroundCompletionsWithoutReattach`: nach 1.000
  empfangenen Abschlüssen fehlen die erwarteten letzten 24 Retired-Turn-IDs.

Beide Methoden liefen mit echtem Robolectric und scheiterten an genau diesen
fachlichen Assertions: **2 ausgeführt, 2 Fehler, JUnit-Exit 1**. Die vorherige
Kotlin-Kompilation der vollständigen Originaldateien hatte Exit 0.

Auf dem finalen lokalen Stand `d91fe8b1141d35341b67298a04f3913fd1bf0876`
bestand danach die gesamte unveränderte Klasse
`at.heinz.deviceai.ui.JarvisSequenceTurnLifecycleUiTest`:
**`OK (25 tests)`, JUnit-Exit 0, Kotlin-Compile-Exit 0**. Der offizielle identische
GREEN-Quellbaum ist in Commit `b2bc510a59e29d4ece478109e12b9785be11ee7d`
gesichert; Baum `7770e3ef628f301bf40161e4a99906836c63b296`.

Pro Lauf wurden 24 vollständige originale Produktionsdateien, darunter View,
Effekte, Android-Painter, Fonts, Bus und Motion-Abhängigkeiten, sowie die vollständige
Testdatei neu mit Kotlin 2.4.0 kompiliert. Zwischen den 25 Eingangsdateien der beiden
Läufe unterscheidet sich ausschließlich `JarvisSequenceView.kt`. Die Testdatei ist
bytegleich. Die frisch kompilierten Kandidatenklassen stehen an erster Stelle des
Klassenpfads. JUnit 4.13.2 und Robolectric 4.14.1 verwenden die vorhandenen,
hashgebundenen instrumentierten Android-JARs offline; die unveränderte
`@Config(sdk = [28])` wählt Android API 28.

Es wurden keine Android-/View-/Looper-/Handler- oder Business-Attrappen verwendet.
Für den Compile-Verweis auf `ToolGatePillActionGate.LABELS/SOURCE_MISSING` stehen
echte bereits kompilierte Business-Klassen aus dem belegten 822e-Android-Build
zuletzt im Klassenpfad. Die vollständige `SafePillActionGate.kt`-Quelle stimmt
mit dem Kandidaten überein; ihr SHA-256 und die vier vorhandenen Klassenhashes
sind in beiden Manifesten gebunden. Die Pill-Klasse erscheint in keinem der
beiden Laufzeit-Klassenprotokolle. Sämtliche beobachteten App-Klassennamen sind
in der frisch kompilierten Kandidatenmenge vorhanden. Kein Tool-/Owner-Pfad wird
daraus als getestet ausgegeben.

**Geltungsgrenze:** Der direkte Lauf hat kein App-Manifest. Robolectric meldet
ausdrücklich den Rückfall auf Android-Systemressourcen. Diese Warnung wurde nicht
durch eine Änderung an den Testmethoden oder Annotationen unterdrückt. Der Nachweis
gilt für die konkrete Widget-/Queue-Lebensdauer mit realen Robolectric-Framework-
Klassen; er ist kein bestandener vollständiger Gradle-/APK-/Ressourcenpaket- oder
Gerätetest und ersetzt den unten dokumentierten Gradle-Abbruch nicht.

Vollständige Compile-/JUnit-Rohlogs, Manifeste, Ergebnisdateien und Quellbindung
liegen in `build/robolectric-red-1/` und `build/robolectric-green-1/` unter dem
Runde-2-Belegverzeichnis. `class-load-selected.log` enthält jeweils alle relevanten
App-/View-/Looper-/Handler-Zeilen, ausdrücklich einen Auszug. Der SHA-256 des
ursprünglichen vollständigen Klassenladeprotokolls und die Auswahlregel stehen
in `class-source-audit.json`. `robolectric-receipt.json` führt die Ergebnisse und
Abnahmegrenzen zusammen.

`run-direct-robolectric.py` ist das vollständige Protokoll des tatsächlich
verwendeten lokalen Aufbaus mit absoluten Pfaden, kein portabler Voll-App-Builder.
`d24ad51…` und `d91fe8b…` sind lokale Git-IDs. Für einen anderen Checkout ist RED
über die archivierte unveränderte `robolectric-red-1/JarvisSequenceView.kt.source`
auf den ansonsten identischen 25 Eingängen des offiziellen GREEN-Standes
rekonstruierbar. Genau dieser Ein-Datei-Unterschied wurde anhand aller Quellhashes
geprüft. Manifeste enthalten die vollständigen Klassenpfade sowie die verwendeten
JAR-/SDK-Hashes. Es wurden nach GREEN 25 keine weiteren breiten Laufserien begonnen.

**Späterer Nachtrag des Koordinators:** Beim unabhängigen Abschlussabgleich waren
die vier temporären Business-Klassen im fremden 822e-Build nicht mehr vorhanden;
bei einer folgenden Nachmessung fehlte auch die dortige Quelldatei. Die zum
Laufzeitpunkt erfassten Klassenhashes werden daher nicht als erneut nachgemessen
ausgegeben. Die eigenen Quellen, 47 Runtime-JARs, zwei SDK-JARs, Rohlogs und
vollständigen Klassenladeprotokolle blieben prüfbar. Die Pill-Klasse erschien in
keinem Lauf; alle beobachteten Appklassen sind in der frischen Kandidatenkompilation
vorhanden. Eine spätere Wiederholung benötigt die dokumentierte Business-
Compile-Abhängigkeit erneut. Der genaue Abgleich steht in
`docs/evidence/three-repair-bots-round2-20261104/coordinator/native-robolectric-review.json`.

## Vollständiger Gradle-Versuch und gesicherter Abbruch

Ein echter Offline-Gradle-Lauf auf dem sauberen UI-RED-Kandidaten
`d24ad51b0235826cf0baaa998a2fb4af80a17404` (Baum
`45dff2f8cd7c2a34aadd1690502316878be83394`) hat AGP 8.10.0 aufgelöst und die
Android-SDK-, Ressourcen- und Manifestaufgaben ausgeführt. Während
`:app:compileDebugKotlin` verschwand der Gradle-Daemon unerwartet. Exitcode 1,
keine erzeugte JUnit-XML-Datei, keine ausgeführte Testmethode. Dies ist ausdrücklich
kein fachlicher RED-Nachweis der beiden angeforderten UI-Gegenproben.

Die Ursache des Prozessverlusts ist nicht gesichert. In den eigenen Verzeichnissen
wurde keine `hs_err`-Datei gefunden; cgroup-Speicherzähler sind hier nicht exponiert.
Ein zunächst vermuteter Transform-Zuwachs wurde bei der Nachmessung nicht bestätigt.
Während und nach dem Lauf schwankte der freie geteilte Plattenplatz stark; auch ohne
eigenen JVM-Prozess fiel er unter 300 MiB. Kein Appfehler oder gesicherter OOM-Grund
wird aus diesem Prozessverlust abgeleitet. Eine zweite Gradle-Ausführung wurde nicht
gestartet.

`android-attempt-1.log` ist der unverändert übernommene vollständige Lauf mit
Quellbindung, exakter Befehlszeile und Exitcode. SHA-256:
`883cb056026e0a0075a8c986dceeb3fadf571afeb32cea9e5555422ee74d512a`.
`android-attempt-1.json` und `android-attempt-1-resource-diagnostic.json` enthalten
die abgegrenzte Auswertung; `ANDROID-SHA256SUMS.json` bindet die gespeicherten Dateien.

Nach Sicherung dieser Dateien wurden auf Anweisung des Koordinators ausschließlich
der eigene reproduzierbare `caches/8.11.1`-Anteil (623.270.916 Dateibytes) und die
eigenen abgebrochenen `app/build`-/`.gradle`-Zwischenprodukte entfernt. `modules-2`,
Robolectric-/Android-JARs, Runtime-Helfer, Quellen, Rohlogs und Snapshot-Manifest
blieben erhalten. `cache-cleanup-receipt.json` hält diesen Schritt fest.
Der unten dokumentierte vollständige Snapshot war vor dieser gezielten Bereinigung
vorhanden; der aktuelle Zustand enthält nur noch den benötigten Dependency-Cache.

Der anschließende direkte Robolectric-Hostlauf der unveränderten Queue-Tests ist
oben mit eigener Quellbindung und eigenen Ergebnissen dokumentiert. Der endgültige
kombinierte Produktquellstand bleibt
`d91fe8b1141d35341b67298a04f3913fd1bf0876`, offiziell
`b2bc510a59e29d4ece478109e12b9785be11ee7d`, identischer Baum
`7770e3ef628f301bf40161e4a99906836c63b296`.

## Ergebnis und Quellbindung

Die vorhandene Preflight-Testdatei prüft jetzt jede der sechs bekannten
Signing-Umgebungsvariablen im gewöhnlichen und im Owner-QA-Modus. Alle zwölf
Fälle verwenden ausschließlich den harmlosen Wert `fixture-not-signing-material`.
Die Fixture entfernt zuvor sämtliche geerbten Signing-Variablen, erstellt einen
eigenen temporären Git-Checkout und ersetzt `curl` durch einen lokalen Canary.
Die Prüfungen verlangen Exit 69, den konkreten Signing-Fehler und das Ausbleiben
von Downloadversuch, Build-Arbeitsverzeichnis und QA-Protokollverzeichnis.

Die Reparatur selbst stammt vom Koordinator und wird hier nicht erneut erzeugt:

- Offizieller Commit: `84cd8cfc05dedef5d4feb28ecfa103530e95f7ff`.
- Lokaler identischer Fix: `e0714c5f2999346ef4e258e2ffaf224d58cfdc9a`.
- Geprüfter Skript-Blob: `9ea8eae2011504b5283a0cf4e62231f0aaa7c39b`.
- Ursprünglicher Arbeitsstand: `63dec3b1296b1d4257310296de3781d75b7a266a`.

Für GREEN wurde die exakte Skriptdatei aus dem lokalen Fix in den eigenen
Arbeitsstand übernommen. Nach dem Lauf wurde sie zurückgesetzt; anschließend
wurde der Arbeitszweig ausschließlich per Fast-forward auf den vorhandenen
Koordinator-Commit gebracht. Der eigene Änderungssatz ergänzt Tests und Belege,
keine zweite Produktionskorrektur.

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis | Exit |
| --- | --- | --- |
| Erweiterter Preflight auf unverändertem alten Skript | 43 Fälle; 41 PASS, genau 2 FAIL | 1 |
| Sechs vorhandene Build-/Release-Vertragssuiten mit erweitertem Preflight auf Root-Fix | 81 Fälle; 81 PASS, 0 FAIL, 0 SKIP | 0 |
| Einzeldatei-Probe `cp --reflink=always` an vorhandener AGP-8.10.0-JAR | `Operation not supported` | 1 |

Die beiden roten Fälle sind ausschließlich
`CONTINUUM_MENTOR_SIGNING_P12_BASE64` in Modus `0` und `1`.
Die vorhandenen Versionsfälle, positiven kanonischen Versionen sowie die
Owner-/Service-/Host-/Quellfreigabesperren wurden unverändert mitgeprüft.

Reproduktion auf einem Stand mit dem oben genannten Root-Fix:

```bash
node --test --test-reporter=tap \
  scripts/continuum-owner-continuous-qa-preflight.test.mjs \
  scripts/continuum-owner-catalog-contract.test.mjs \
  scripts/continuum-owner-continuous-release.test.mjs \
  scripts/continuum-one-tap-updater-contract.test.mjs \
  scripts/continuum-owner-v38-release.test.mjs \
  scripts/continuum-release-host-preflight.test.mjs
```

Rohdaten und SHA-256-Bindungen liegen unter
`docs/evidence/three-repair-bots-round2-20261104/build/`:
`signing-red.tap`, `signing-green.tap`, `reflink-probe.json`, `receipt.json`.

## Unabhängiges Review der Produktionskorrektur

Ergebnis: keine offene Beanstandung im untersuchten Ein-Zeilen-Diff.
Der fehlende sechste Schlüssel wird in derselben vorhandenen Schleife wie die
fünf anderen ausgewertet. Ein nichtleerer Wert stoppt den Einstieg vor
Verzeichnisanlage und Download. Der Wert selbst wird nicht ausgegeben.
Die Erweiterung lockert weder Versionsprüfung noch Owner-, Host-, Service-,
Branch- oder Quell-SHA-Prüfung. Die echten Ausführungsproben bestätigen die
Abweisung in beiden unterstützten Modi. Das Review ist auf diesen Fix und seine
Preflight-Wirkung begrenzt; es ersetzt keine vollständige Render-/Release-Abnahme.

## Android: verfügbarer Pfad und isolierter Snapshot

Der zuvor fehlende AGP-Stand ist inzwischen tatsächlich lokal vorhanden.
Der erfolgreiche Integrationsprüfweg liegt unter
`/workspace/scratch/822e72396ed9/build-env`:

| Bestandteil | Tatsächlich vorgefundener Pfad / Stand |
| --- | --- |
| JDK | `jdk17` → `/workspace/scratch/b7d23de0e715/build-toolchain/jdk17`; Temurin 17.0.20.1+1 |
| Gradle | `gradle-8.11.1` → `/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1` |
| Android SDK | `android-sdk` → `/workspace/scratch/b6ee64d418cf/toolchain/android-sdk`; API 36, Plattformrevision 2 |
| Abhängigkeiten | `gradle-cache/caches/modules-2`; gemessen 841 MiB |
| Gradle-Versionscache | `gradle-cache/caches/8.11.1`; gemessen 616 MiB |
| Vorheriges App-Buildverzeichnis | `/workspace/scratch/822e72396ed9/continuum-combined/app/build`; gemessen 389 MiB |

Beim ersten eigenen Check waren 1,8 GiB frei. Eine gewöhnliche Kopie beider Cacheteile
zuzüglich eines vergleichbaren Buildverzeichnisses würde ungefähr 1,8 GiB
belegen, ohne ausreichende Reserve für temporäre Dateien und parallele Arbeit.
Die geprüfte Reflink-Alternative scheitert bereits an einer einzelnen bekannten
AGP-JAR. Ihr eigenes temporäres Ziel wurde nach der Probe entfernt.
Zu diesem Prüfzeitpunkt wurde deshalb kein Cache kopiert oder Gradle-Build
gestartet; `receipt.json` hält genau diesen früheren Zwischenstand fest.

Nach dem Freigeben eigener abgeschlossener Arbeitskopien durch den Koordinator
standen 3,0 GiB zur Verfügung. Der anschließende Kopiervorgang stoppte an der
gewählten 1.250-MiB-Reserve. Nach Sparse-Checkout historischer Belege im eigenen
Arbeitsbaum waren 1,6 GiB für die verbleibenden 439.082.869 Bytes vorhanden.
Mit neu berechneter 1-GiB-Reserve wurde die eigene Teilkopie vollständig ergänzt.
Der aktuelle isolierte Snapshot liegt unter
`/workspace/scratch/7dcf5d78f2cb/round2-android-env` und enthält 5.119 Dateien
mit 1.493.822.104 Dateibytes. Danach waren 1.165.447.168 Bytes frei.

Der vorhandene Donor-Launcher-Lock wurde nur lesend geöffnet und während der
Kopie per gemeinsamem `flock` gehalten. Quellmanifest vor/nach der Kopie sowie
Zielmanifest stimmen bei relativen Dateinamen, Größen und `mtime_ns` überein.
Zusätzlich wurde der Inhaltshash der AGP-JAR verglichen; dies ist ausdrücklich
kein vollständiger Inhaltshashvergleich aller 5.119 Dateien. Es wurden keine
Hardlinks und keine `*.lock`-/`gc.properties`-Dateien übernommen. JDK, Gradle
und SDK werden nur lesend referenziert; der Gradle-Cache, temporäre Dateien,
Android-Benutzerverzeichnis und Logs gehören ausschließlich dieser Runde.

`snapshot-receipt.json` dokumentiert den neuen Stand;
`prepare-cache-snapshot.py` hält den tatsächlich verwendeten Kopierweg fest.
`run-targeted-android.py` verlangt einen ausdrücklich benannten sauberen Commit,
mindestens 1 GiB freien Platz, Klassenfilter, Offline-Modus und einen Worker.
Der Gradle-Heap beträgt 1 GiB, der einzelne Testprozess maximal 512 MiB.
Der Launcher entfernt alle sechs Signing-Umgebungsvariablen. Die Heap-Initdatei
ändert keine Tests oder Assertions. Ein fremder aktiv beschreibbarer Cache wird
nicht als unveränderlicher `GRADLE_RO_DEP_CACHE` ausgegeben.

Der bereits erfolgreiche 822e-Lauf bleibt ein eigenständiger Beleg. Sein
`combined-gradle.log` bindet den Lauf an lokalen Commit
`377ac199b1e360cecefbe1e04bf43e68e94695b9`, Baum
`2be53764ed72e00e780c7b87ff1401c195ac0a50`, `--offline`, `-Xmx1g` und die
benannten Testklassen. Der Koordinator hat dessen Quellidentität und XML-Belege
separat geprüft und in der Runde-2-Abnahme gesichert. Dieser frühere Lauf wird
hier nicht zu einem eigenen Lauf oder zu einer Prüfung späterer Änderungen
umetikettiert.

## Anschluss, Erhaltung und Rückfall

1. Den Testcommit auf den Koordinatorstand mit dem Signing-Fix übernehmen und
   dieselben sechs Node-Suiten unabhängig ausführen.
2. Für ein vollständiges Gradle-Gate ausreichend reservierte lokale Ressourcen
   bereitstellen. Der fokussierte echte Robolectric-Lauf ist abgeschlossen;
   der vollständige Gradle-Versuch bleibt vor Testausführung abgebrochen.
3. Fold7-/GPU-/Audio-/Installationsabnahme bleibt offen. Es wurde keine APK
   gebaut, signiert, veröffentlicht oder installiert.

EXP-00, EXP-07 und EXP-08 sowie ihre Schnittstellen bleiben erhalten. Keine
UI-/Retriever-Produktionsänderung, kein Versions-, Paket-, Signer- oder
Updatekanalwechsel, kein Main-Merge, keine Änderung an Owner/Offline/CCC1.
Rückfall: ausschließlich den eigenen Test-/Belegcommit zurücknehmen; der
bereits unabhängig reparierte Produktionsschutz bleibt bestehen.

Die Veröffentlichung erfolgt ausschließlich durch den Koordinator in PR505;
dieser Bot nimmt keine Remote-Mutation vor.
