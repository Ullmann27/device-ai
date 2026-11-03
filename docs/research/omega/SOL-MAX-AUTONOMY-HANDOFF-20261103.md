# SOL MAX – Operation Autonomy, 03.11.2026

## Auftrag und Startpunkt

Dieser Forschungszweig setzt den neuen Besitzerauftrag „OPERATION AUTONOMY“ um.
Die fünf Abnahmen bleiben in dieser Reihenfolge: reale Fold7-Inferenz, echte
modellgesteuerte CCC1-Reparatur, Android-Aufgabe, Worker/Lernen, Bedienbarkeit.
Vorbereitende Hostprüfungen ersetzen keine dieser Geräteabnahmen.

- Repository: `Ullmann27/device-ai`; isolierter Branch
  `research/continuum-sol-max-autonomy-20261103`.
- PR #361 frisch geprüft: `97a930f51b40b0d25571fa0068c4fe2eb6a35f1e`, offen/Draft.
- Geprüfter Android-Ausgangscode: `48c0fb1f2b4db06619e170bf36f8d070e002eca4`.
- Unveränderte Publisher-Integration übernommen:
  `d056d7769431490bc16e0a43fd88a758b2e76d47` (fünf Release-/Testdateien gegenüber
  48c0fb1f). Keine parallele Modellverwaltung, Datenbank oder Worker.
- Der Publisher-Forschungsbranch ist während der Bestandsprüfung auf
  `2eae6b53cbdd9b8b4d3c12f8d8a3c4ca770ea8f2` weitergelaufen. Diese zusätzliche
  Katalogänderung ist noch nicht übernommen; fremden Branch nicht überschreiben.
- `main` frisch gelesen: `2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a`;
  keine Änderung oder Merge nach main.

## Frisch ausgeführte Release-/Geräteprüfung

Der bestehende Render-Publisher `srv-dbf1tr2vcj2c73ehpfog` meldete Deploy
`dep-dbki488m7kps7397tdj0` aus d056d776 LIVE. Der tatsächlich heruntergeladene
Katalog meldete `SIGNED_STAGED_DEVICE_NOT_RUN`, `approved:false`.

Die APK wurde vollständig erneut heruntergeladen und unabhängig gelesen:

- Paket `at.heinz.deviceai.continuumlocal`, Version `0.3.37`, Code `38`.
- 54.673.371 Bytes; SHA-256
  `2e4be9350ddfe2acb35d2512cc54f67bc758abe11e65342d40af6ba9fe8b85a9`.
- `apksigner verify --verbose --print-certs`: erfolgreich, APK-v2-Signatur;
  Originalzertifikat
  `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
- `aapt2 dump badging`: Paket, Versionsname und Versionscode stimmen überein.

Das ist eine unabhängige **Artefaktprüfung**, keine neue Signierung, Freigabe,
Installation oder Fold7-Abnahme. Ein paralleler Publisher kann den Live-Katalog
später ändern; vor einer Besitzerhandlung immer erneut lesen.

Der lokale SDK-ADB-Aufruf `adb devices -l` lieferte **keine Geräte**. Der
Cloud-Umgebungsstatus hat keinen Android-Connector, kein VPN und keine
konfigurierten TCP-Geräteziele. Unter `/workspace` wurden keine `.litertlm`-,
`.gguf`-, `.safetensors`- oder `.tflite`-Gewichte gefunden. Das sagt nichts über
die privaten Dateien des unerreichbaren Fold7 aus. Der alte Besitzerbefund vom
31.10.2026 (Code 30, Gemma READY) bleibt historisch, kein aktueller Laufbeleg.
Der Besitzer hat die Anschlussfrage beantwortet: **derzeit kein Fernzugriff;
Fold7-Prüfungen vorbereiten**. Keine neue Einrichtung oder Freigabe erforderlich.

## Abnahmestatus zum Startcheckpoint

| Gegenstand | Status | Grenze |
| --- | --- | --- |
| Vorhandene Gehirn-/Tool-/CCC1-Quellen | SOURCE_INTEGRATED | Aus PR #361 übernommen; neue Gesamtsuite noch NOT_RUN |
| Frühere 1.687 JVM / 1.969 Node PASS | HOST_VERIFIED, historisch | Bezieht sich auf 48c0fb1f; keine neue Inferenz |
| Reale Fold7-Modellantwort / Mehrschrittauftrag | NOT_RUN | Gerät aktuell nicht erreichbar |
| Echte autonome CCC1-Modellreparatur | NOT_RUN | Skriptadapter zählen nicht als neuronaler Beweis |
| Neue Android-Aufgabe / Workerzyklen | NOT_RUN | Kein Gerätelauf |
| SWB1/CRE1/CDX/NEURON-Lernen | RESEARCH_SHADOW | Keine Gewichts- oder Gedächtnispromotion |
| Signierte bestehende Code-38-APK | HOST_VERIFIED (Artefakt) | Installation und aktuelle Geräteversion unbekannt |
| Aktueller Fold7-Nachweis | NOT_RUN | Kein DEVICE_VERIFIED-Anspruch |

## Nächster Einstieg, Verträge und Rückfall

Zuerst Live-Katalog und Branch-HEAD erneut prüfen. Danach den tatsächlichen
`AgentActivity → LocalModelRuntime/LiteRT → BoundedAgent → ToolGate`-Aufrufpfad
auf noch gültige Reviewfehler untersuchen. Insbesondere PR #361 / Kommentar
4447058551: ungenutzte Toolantwort-Kodierung kann Bildschirm-Turns abbrechen.
Nur mit reproduzierbarem RED und minimaler GREEN-Reparatur übernehmen.

## Erster geprüfter Fortschritt

Commit `cf0f3c0d52d821ba024f3acb17f1f67d533bcd61` repariert den bestätigten
Bildschirm-Turn-Fehler. AgentActivity ruft jetzt den bestehenden
LocalToolContinuation-Helfer `submit` auf. Ein bereits vollständig geprüfter
frischer Bildschirm-Prompt startet ohne ungenutztes natives Tool-Encoding.
Echte Toolfortsetzungen bleiben unverändert begrenzt und im nativen Toolkanal.

Fokussierter echter SDK-/Gradle-JVM-Nachweis: **RED 12 ausgeführt / 1 Fehler →
GREEN 12 PASS / 0 Fehler / 0 Skips**. Der positive Test durchläuft gültigen
Bildschirmsnapshot, SHA/Grant/Ablaufprüfung und vollständiges Promptbudget;
die Enginegrenze ist ausdrücklich ein Hostadapter ohne Gewichte. Rohbelege,
Reproduktionsdiff, genaue Befehle und XML unter
`docs/evidence/sol-max-autonomy-20261103/screen-turn/`.
Vollständige Gesamtsuiten und frischer unabhängiger Review stehen am Zeitpunkt
dieses Checkpoints noch aus; kein DEVICE_VERIFIED-Anspruch.

Vorbereitetes Besitzer-/KI-Protokoll:
[SOL-MAX-FOLD7-AUTONOMY-ACCEPTANCE-20261103.md](SOL-MAX-FOLD7-AUTONOMY-ACCEPTANCE-20261103.md).
Es trennt die tatsächliche App-LiteRT-Inferenz, den optionalen GGUF-CLI-Pfad,
deterministische CCC1-Reparatur und modellgesteuerte typisierte Coding-Aufgaben.
Der aktuelle Android-ToolRegistry hat kein allgemeines `read_file`; nur wirklich
registrierte/erteilte Fähigkeiten testen. Keine erfundenen Dateizugriffe.

Relevante Register: EXP-05 (lokale Inferenz), EXP-08 (bestehender
Android/Termux-Kanal), EXP-11 (kanonische Orchestrierung), EXP-12 (Kontinuität);
später EXP-00/01/02/04 für CCC1/Worker/Lernen. Issue #41, AGENTS.md,
Experimentvertrag/-register und die drei SOL61-Abnahme-/Übergabedokumente wurden
gelesen. Android bleibt primäre Maschine, Hosttests bleiben getrennt.

Keine Installation, Veröffentlichung, Release-Host-Änderung, produktive
Gewichtsänderung oder Besitzer-Datenmigration. Neue Reparaturen bleiben im
Forschungsbranch und sind einzeln revertierbar. Für einen Rückfall auf die
geprüfte Ausgangsquelle 48c0fb1f müssen persönliche Daten weder gelöscht noch
deinstalliert werden; keinen Geräte-Rollback ohne gesonderte Prüfung ausführen.
