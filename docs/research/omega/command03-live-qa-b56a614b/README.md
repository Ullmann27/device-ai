# Command03: lokale Prüfung der Live-Medien-Anbindung

Geprüfte Quelle: `b56a614b290977684b50910fac3cc8d14e4294c0`. Produktionsbaum `app/src/main`: `8ead90327b0a99898ac75632b8f83621d4f77d0a`.
Die Belege gehören genau zu dieser sauberen Quelle. Ein späterer Dokumentationscommit
ändert die geprüfte Anwendung nicht und wird nicht als erneut getestete Quelle ausgegeben.

## Ergebnis

| Auswahl | Frische Ergebnisse | Fehler / übersprungen |
|---|---:|---:|
| Android-HTML-/Medien-Brücke | 22 | 0 / 0 |
| Computerfreigabe, Sitzungen, Sprachreihenfolge und Projektion | 71 | 0 / 0 |
| Bestehende klassische Oberfläche | 32 | 0 / 0 |
| DOM und tatsächliches Kotlin-JSON-Paket | 28 | 0 / 0 |
| Gesamt | **153** | **0 / 0** |

Die 18 JUnit-Dateien sind die unveränderten, unmittelbar nach den jeweiligen Läufen
gesicherten Originalberichte. Die Web-Prüfung verwendet das tatsächlich von der
Android-Stage serialisierte Paket eines synthetischen 2×2-JPEGs. Das ist kein
Bildschirmbild eines Nutzers. `verification.json` verbindet Quellen, Laufbelege,
Ergebniszahlen und APK. `SHA256SUMS` bindet die Dateien dieses Belegsatzes.

## Regulärer Build und unabhängige Artefaktprüfung

`live-media-unsigned-02`: `:app:assembleRelease`, **PASS_LOCAL**, 12.704 s.

- Paket `at.heinz.deviceai.continuumlocal`, Code60 / Version0.3.59.
- 111.421.502 Bytes; SHA256 `00def8e94f0f2d26257d25eeb5176f4e7d007bf33013c48a92ee6f8874d57e4c`.
- **UNSIGNED_NOT_INSTALLABLE**; keine Originalsignatur, Installation oder Veröffentlichung.
- Alle 38 ausgelieferten Command03-Dateien stimmen bytegenau mit der geprüften Quelle überein.
- Die 35 freigegebenen Originalassets bleiben bytegleich; die Android-Anbindung ist separat.
- Unabhängig geprüft: ZIP-CRC und Pfade, Paket/Version/SDK, ausschließlich ARM64,
  fünf native ELF-Bibliotheken und deren 16-KiB-Ausrichtung, `zipalign -P 16`, fehlende Signatur.

Die sieben neuen/geänderten Release-Hauptklassen wurden bereits in `live-media-unsigned-01`
frisch kompiliert. Dieser Lauf scheiterte anschließend beim Verpacken veralteter
Asset-Zwischenstände und bleibt als fehlgeschlagen protokolliert. Lauf02 verwendet
dieselben b56-Klassen und erzeugt Assets sowie APK regulär neu. Einzelne Klassenhashes,
Zeitfenster und Taskstatus stehen in `provenance/release-compilation-and-packaging-proof.json`.

## Reproduktion und Bedeutung der Laufbelege

Die tatsächlich ausgeführten Befehle, Versionen, absoluten Toolchainpfade und
physischen Testquellen mit SHA256 stehen vollständig in `receipts/`. Konfiguration
und Erklärung der Auswahlparameter: `tools/command03-native-qa/README.md` im
Repository. Die lokale Toolchain verwendet JDK17, Gradle8.11.1, AndroidSDK36 und
Build-Tools35.0.0; Abhängigkeiten und Modellpins wurden aus dem vorhandenen Cache geprüft.
Es wurde keine vollständige Repository-Testsuite ausgeführt.

Die beiden `ui-compile`-Belege bescheinigen ausschließlich Testkompilation. Die
Testzahlen stammen ausschließlich aus den späteren Läufen mit frischen JUnit-Dateien.
Die klassische Auswahl lief in sieben nacheinander gestarteten JVMs mit jeweils
unverändert 1024MiB Heap. Alle neun physischen Test-/Hilfsquellen sind bytegleich
zur vorherigen fehlgeschlagenen Klassikprüfung; keine Assertion wurde abgeschwächt.

Web-Prüfung mit jsdom26.1.0 außerhalb des Repositorys:

```sh
COMMAND03_DOM_MODULE=/absolute/path/jsdom/lib/api.js \
COMMAND03_REQUIRE_NATIVE_PACKET=1 \
COMMAND03_NATIVE_COMPUTER_PACKET=/absolute/path/native-computer-wire.json \
node --test --test-reporter=tap tools/command03-native-qa/native-bridge-behavior.test.mjs
node tools/command03-native-qa/check-visual-source.mjs
```

Für einen neuen Lauf stammt `native-computer-wire.json` aus der Zeile
`COMMAND03_COMPUTER_WIRE:` der frisch bestandenen
`ContinuumCommand03MediaStageUiTest`-JUnit-Ausgabe. Das beigelegte Paket und dessen
Provenienz dokumentieren den hier ausgeführten Lauf; sie ersetzen keinen neuen Androidlauf.

## Fehlerbehebung und Compilerzustand

Die endgültige Quelle korrigiert die Empfängeridentität im klassischen Avatarpfad.
Die HTML-Sprachweiterleitung bleibt erhalten; die klassischen fünf betroffenen
Avatarprüfungen bestehen mit ihren ursprünglichen Anforderungen.

Frühere abgebrochene oder fehlgeschlagene Läufe werden in `historical-attempts.json`
ausdrücklich nicht zur Abschlusswertung gezählt. Dazu gehörten Hostfensterprobleme
im Robolectric-Testaufbau, der echte Klassikfehler, Ressourcenabbrüche und veraltete
abgeleitete Asset-Zwischenstände. Der normale inkrementelle Compilerlauf wurde am
ursprünglichen Pfad nach vollständiger Hashprüfung eines echten zusammengehörigen
Compilerzustands aus `533eb104` wiederhergestellt. Alle sieben geänderten/neuen
Hauptklassen wurden anschließend neu kompiliert. Es wurden keine alten Testberichte
oder APKs als neue Ergebnisse eingesetzt und keine Compilerfingerprints editiert.
Dies ist kein Nachweis eines erfolgreichen Builds mit vollständig leerem Cache.

## Abnahmegrenzen

**VERIFIZIERT** sind die genannten lokalen Host-/DOM-Prüfungen und die Struktur der
unsigned APK. **GERÄTEBESTÄTIGT: NOT_RUN.** Kontrollierte Decode-/Layout-/Zeit- und
Canvas-/WebView-Callbacks testen Verträge und Reihenfolge; sie beweisen keine
tatsächlichen Chromium-/GPU-Pixel. Physische Sprachausgabe und Lippensynchronität,
Fold-/IME-Verhalten, Captureberechtigungen, Leistung und Thermik auf Hardware sowie
vollständige Release-QA und Originalsignierung bleiben eigene Abnahmeschritte.
