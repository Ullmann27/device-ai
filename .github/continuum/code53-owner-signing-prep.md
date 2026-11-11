# Code53-Signer: Bindung und Prüfnachweis

Status: **PRODUCTION_BINDING_VERIFIED / LOCAL_CONTRACTS_PASS**.
Gehosteter Contract-Check und Originalsignierung werden separat nachgewiesen.

Basis ist der tatsächlich gelesene geschützte Zweig
`chatgpt/code52-owner-signing-20261109` bei
`0199741efba0a90e20684520762c7d8ded0fcfc8`.
Der bestehende Workflow `.github/workflows/continuum-code52-sign-release.yml`
ist auf `main` vorhanden und als ID `412499661` aktiv registriert.
Die drei bisherigen Dispatch-Eingaben bleiben erhalten.

Nur `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05` aktiviert das getrennte
Code53-Profil. Gemeinsamer Signierkörper, Originalzertifikat, geschützter
Zweig, Besitzerprüfung und unveröffentlichter Release-Entwurf bleiben erhalten.
Der Code52-Prüfzweig und seine bestehenden vier Contracts bleiben bestehen.

Code53 bindet zusätzlich Quellbaum
`539ea3aac0af04644a015b3fb69ba865af80d1a8`, getrennte Workflow-SHA
`07efc77652638cfa40a67270475aa2c08ded5c76`, Run `42985335421`, Job
`130405169817`, erfolgreiche Pflichtschritte sowie exakte Artefakt- und APK-Hashes.
Der vorhandene Paketvalidator bleibt durch seinen tatsächlichen Dateihash gebunden.
Der zusätzliche Schritt `Maya settings lifecycle preflight` ist verpflichtend;
ein fehlender oder übersprungener Schritt wird abgewiesen.

Der vorherige Lauf `42977884118` ist fehlgeschlagen: 4.668 Androidtests,
ein Fehler in `MayaVoiceSettingsUiTest`, 17 übersprungene Tests, keine APK.
Weder dessen Quelle noch dessen Run-Identität werden als neuer Kandidat akzeptiert.
Der Zwischenlauf `42984950999` wurde abgebrochen und sein Workflow ersetzt,
damit der gezielte Testfilter die bestehende Gradle-Task-Schranke einhält.
Auch dieser Run und seine frühere Workflow-SHA werden ausdrücklich abgewiesen.

## Tatsächlich geprüfte Produktionsbindung

Der [QA-Lauf 42985335421](https://github.com/Ullmann27/device-ai/actions/runs/42985335421)
und Job `130405169817` sind vollständig erfolgreich abgeschlossen.
Die Datei `.github/continuum/code53-owner-signing-binding.json` enthält ausschließlich
die tatsächlichen Metadaten und unabhängig nachgerechneten Download-Hashes.

| Artefakt | ID | ZIP-Bytes | ZIP-SHA256 |
| --- | --- | ---: | --- |
| Code53-050a57cb-UNSIGNED-NOT-INSTALLABLE | 13842542249 | 73412550 | 083c2df520fb5356d8d6357cbb0d7eb57b6aa279fd57db7ec2c0ac2d9a1573ee |
| Code53-050a57cb-QA-Evidence | 13842736860 | 426597327 | a08c090d94ec63edf7ca08b36fda00ff286fa7eb9b0e42b6ad226e679098708e |

APK: `Continuum-Code53-UNSIGNED-NOT-INSTALLABLE.apk`, `102701335` Bytes,
SHA256 `28153df1b4e6f0d97a8f625a7f444248efc6ee09436e7e77c5aab4e3aaaa9c0e`.
Report im Evidence-ZIP: `_temp/code53-package/RESULT.json`.
Paket, Version, Quellbaum, saubere Quelle vor/nach Prüfung, unbenutzte Signierung,
16-KiB-Ausrichtung und unveränderte APK stimmen mit dem Validatorbericht überein.

## Ausgeführte Prüfung

- `bash -n scripts/continuum-code52-github-signer.sh`: PASS.
- Vier bestehende Code52-Contracts: PASS.
- Alle 16 Code53-Contracts einschließlich Produktionsbindung: PASS.
- Produktions-Verifier gegen originale REST-Metadaten: `CODE53_METADATA_VERIFIED`.
- Derselbe Verifier gegen beide echten ZIP-Dateien: `CODE53_ARCHIVES_VERIFIED`.

Der vorhandene secrets-freie Contract-Workflow prüft zusätzlich ausschließlich
den Prep-Zweig `prep/code53-original-signer-20261111`.
Die Branchgrenze des manuellen Signierworkflows bleibt unverändert auf
`chatgpt/code52-owner-signing-20261109` beschränkt.

Die Tests importieren ausschließlich den reinen Belegprüfer. Sie starten weder
Signer noch Netzwerkaufrufe und lesen keine Environment- oder Secret-Inhalte.
Auch die lokale Prüfung der tatsächlichen Metadaten und Archive hat keinen
Signer gestartet. Originalsignatur und unveröffentlichter Release-Entwurf
werden erst im bestehenden geschützten Ablauf erzeugt und separat überprüft.
Fold7-Installation und Gerätetest bleiben `NOT_RUN`.
