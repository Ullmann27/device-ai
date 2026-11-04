# Tatsächlich reproduzierter NOTICE-Fehler

Die unveränderten Runtime-Dateien aus #513 wurden mit dem neuen `AvatarAssetProvenanceTest` geprüft. Das tatsächliche Paketasset und `AvatarGlbContract.ASSET_SHA256` stimmen bei `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3` überein. Die vorhandene NOTICE nennt dagegen `6b199c5989640b516b8c45401ececeadb8ed1baae1d1e1ab5db20d19e966aa1e` und eine alte Dateigröße.

Ergebnis: Kotlin-Kompilierung Exit 0; 86 JUnit-Fälle, davon 85 bestanden und genau der neue Herkunftstest fehlgeschlagen; 26 ergänzende Motion-Prüfungen bestanden. Gesamtstatus **EXPECTED_RED_STALE_NOTICE**, keine Kandidatenfreigabe.

`summary.json` bindet die 22 tatsächlich kompilierten Kotlin-Dateien an ihre SHA256-Werte. Bei Ausführung stand der lokale Checkout auf `14bc1edcb7f9076f28dba0d4029c19425c3d3013`; ausschließlich der neue Test war noch uncommitted. Sein Inhalt wurde nach dem Lauf nicht geändert. Der bytegleiche veröffentlichte Elternbaum ist Commit `fe8c1a05af6e651aa419fa1ee6c9949edbce84a4`.

`raw-execution.zip` enthält unveränderte Originalbytes von Befehlen, Exitcodes, Compiler-/JUnit-/Motion-Protokollen und dem tatsächlich verwendeten lokalen Runner. `archive-manifest.json` dokumentiert jede Datei. Die darin enthaltenen absoluten Pfade beschreiben die tatsächliche Ausführungsumgebung und sind keine neue Toolchain-Voraussetzung des Produkts.

Der eigentliche Test liegt im normalen Testquellbaum. In einem entsprechend eingerichteten vollständigen Checkout lässt er sich über die bestehende Projekttoolchain auswählen:

```bash
./gradlew --offline :app:testDebugUnitTest --tests at.heinz.deviceai.avatar3d.AvatarAssetProvenanceTest
```

Dieser Gradle-Befehl ist hier eine Reproduktionsanweisung; tatsächlich ausgeführt wurde der archivierte Kotlin-/JVM-Hostrunner. Bei späterem Assetaustausch müssen GLB, Runtime-Hash und NOTICE gemeinsam denselben neuen Inhalt beschreiben. Weder Prüfaussagen noch Fehlertoleranzen des Tests werden dafür gelockert.

Keine native GPU-, Fold7-, Audio- oder Referenztreueabnahme wurde durchgeführt.
