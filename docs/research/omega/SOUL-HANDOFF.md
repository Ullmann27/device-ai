# SOUL-HANDOFF — Continuum Infrastruktur und Modellforschung (25.10.2026)

## Verbindliches Projekt
Ausschließlich `Ullmann27/device-ai`, bestehende Besitzer-App `at.heinz.deviceai.mentorpaired`; keine zweite Haupt-App. Draft-PR #147, Branch `integration/continuum-soul-infrastructure-20261025` gegen `config/continuum-deepseek-gemma-selection-20261025` (PR #146). Weitere Parent-Bezüge: #143, #139. Weder Main-Merge noch Besitzer-Signierung.

## Branch-Abstammung und integrierte Originalarbeit
Der Branch wurde aus dem bereits veröffentlichten DeepSeek-Kapazitätszweig `ops/deepseek-v41-1017-h200-deployment-20261025` @ `a466a89604a0aad09ff2fcdaaba14e3d86730ea0` erstellt. Dieser basiert auf dem Modellentscheidungszweig #146 und damit der Android-Integration #143. Er bewahrt den vorhandenen Capacity-Preflight und dessen Tests ohne Umschreiben der Originalhistorie. Die vier von #145 übernommenen Forschungsdateien sind inhaltlich unverändert:
- `scripts/continuum-native-corpus.mjs`
- `scripts/continuum-native-corpus.test.mjs`
- `scripts/continuum-native-corpus-qa.sh`
- `docs/research/continuum-native-model-20261025.md`

PR #145 bleibt unverändert und offen. Die Übernahme bedeutet keine automatische Trainingsdaten- oder Modellfreigabe.

## Neue Infrastruktur
- `scripts/continuum-deepseek-tier-plan.mjs`: bereits vorhandene, rein deklarative 48-Shard-/GPU-/RAM-/Volume-Prüfung (kein Download, keine Inferenz).
- `scripts/continuum-deepseek-hub-preflight.mjs`: neue, rein lesende HTTPS-Metadatenprüfung gegen die feste offizielle Hugging-Face-Repo-ID; ermittelt zuerst die HEAD-SHA und liest **dieselbe fixierte Revision** nach. Inventarisierung von 48 Gewichts-Shards mit veröffentlichten Größen und optionalen LFS-Digests; keine tatsächliche Gewichtsverifikation, kein Gewichtsdownload und keine Infrastrukturkosten.
- `scripts/continuum-deepseek-hub-preflight.test.mjs`: negative Tests für fehlerhafte Revisionsbindung, unvollständige Shards, fehlende Hashes, URLs, Zeitlimits und fehlerhafte Metadaten.
- `scripts/continuum-soul-infra-qa.sh`: versionsgebundener Node-Testlauf, Build-Manifest, ehrliche Nichtausführung von Gerät-/GPU-/APK-Tests.

## Experiment-ID, Risiken, Status
EXP-05 (Modelle), EXP-06 (Quellenprovenienz), EXP-08 (autorisierte Infrastruktur), EXP-11 (Fachmodelle und Agenten), EXP-12 (Modellidentität). Status: `SOURCE_INTEGRATED` für neue Dateien, `DEVICE_INSTALLED` und `END_TO_END_VERIFIED` ausdrücklich **nicht erreicht**. Virtueller Speicher kann den GPU-RAM nicht ersetzen. Der Entwickler darf keine fremden Systeme, Zugangsdaten, Rechte oder kostenpflichtige GPU-Kapazitäten ohne entsprechende Freigaben benutzen. Modellantworten niemals als Werkzeugfreigaben interpretieren.

## Testanweisung
```bash
node --test scripts/continuum-native-corpus.test.mjs scripts/continuum-deepseek-tier-plan.test.mjs scripts/continuum-deepseek-hub-preflight.test.mjs
node scripts/continuum-deepseek-hub-preflight.mjs
```
Die zweite Zeile benötigt Internet und lädt ausschließlich Metadaten; bei HTTP-, Änderungs- oder Schemafehler `BLOCKED_HUB_METADATA`, nicht Erfolg. Der versionierte Render-QA-Build darf nur Node-Tests ohne aktiven GPU- oder Modelldownload ausführen.

## Rückfall und nächste Schritte
Dieser Branch verändert keine Android-Laufzeit. Rückfall durch Nichtmerge von PR #147; alle bisherigen Branches einschließlich #145 und #146 bleiben erhalten. Nach grüner QA: zweite Kopie der manifestbasierten Validierung prüfen, nach unabhängiger Geräteabnahme die UI-Modelldiagnostik aktualisieren, für Cloud-Bridge zunächst einen ausdrücklich opt-in konfigurierten Provider mit sicheren Datenflüssen und echten Tests verwenden. Für freie Android-App-Synthese müssen vorhandener M4-/M5-Builder und die Termux/CCC1-Pipeline zu einem tatsächlichen End-to-End-Pfad ausgebaut werden; Modellantworten dürfen niemals als Shellbefehle direkt laufen. Designharmonisierung der Untermenüs gehört in gesonderte visuell und mit Robolectric geprüfte Commits, nicht ungeprüft in die Modellspeicher-Forschung.

## Offene Abnahmen
Auf Fold7 ist Gemma 4 E4B noch nicht mit dem neuen Branch importiert/inferiert worden. DeepSeek 1017 ist nicht heruntergeladen, nicht serviert und nicht mit dem Android-Coding-Agenten verbunden. Eine 0.3.22 APK wurde aus diesem Branch nicht gebaut, signiert oder installiert. Test-Ergebnisse erst mit tatsächlichem Log/SHA im Projekt bestätigen.
