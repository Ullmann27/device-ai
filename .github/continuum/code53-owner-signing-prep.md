# Code53-Signer: lokaler Prüfstand

Status: **WIP / NOT_READY_FOR_SIGNING**.
Ein veröffentlichter Prep-PR ändert diese Signiersperre nicht.

Basis ist der tatsächlich gelesene geschützte Zweig
`chatgpt/code52-owner-signing-20261109` bei
`0199741efba0a90e20684520762c7d8ded0fcfc8`.
Der bestehende Workflow `.github/workflows/continuum-code52-sign-release.yml`
ist auf `main` vorhanden und als ID `412499661` aktiv registriert.
Die drei bisherigen Dispatch-Eingaben bleiben erhalten.

Nur `fd86f11a4a822ee4805f4c462791baeb1d741db4` aktiviert das getrennte
Code53-Profil. Gemeinsamer Signierkörper, Originalzertifikat, geschützter
Zweig, Besitzerprüfung und unveröffentlichter Release-Entwurf bleiben erhalten.
Der Code52-Prüfzweig und seine bestehenden vier Contracts bleiben bestehen.

Code53 bindet zusätzlich Quellbaum
`21e5603d2a3f20c840bc3c2ba7836be408dbb5ad`, getrennte Workflow-SHA
`23146e7f817b825e48a2b086f73da5cf28e0a274`, Run `42977884118`, Job
`130381258271`, erfolgreiche Pflichtschritte sowie exakte Artefakt- und APK-Hashes.
Der vorhandene Paketvalidator bleibt durch seinen tatsächlichen Dateihash gebunden.

## Absichtlich noch geschlossen

`.github/continuum/code53-owner-signing-binding.json` fehlt absichtlich,
solange der echte Lauf und seine Artefakte noch nicht vollständig geprüft sind.
Keine erfundenen IDs, Digests oder Erfolgswerte werden eingesetzt.
Der Code53-Pfad bricht deshalb vor dem Signieren ab; der Produktionsbindungs-Test
ist entsprechend **rot**. Dieser Stand ist **kein Gesamt-PASS**.

Noch einzusetzen: tatsächlich erfolgreiche Run-Metadaten, beide Artefakt-IDs,
ZIP-Digests und ZIP-Größen, APK-SHA256/Bytes sowie exakter Pfad des
Paketvalidator-Reports. Danach alle bestehenden und neuen Contracts erneut ausführen.

## Bereits geprüft

- `bash -n scripts/continuum-code52-github-signer.sh`: PASS.
- Vier bestehende Code52-Contracts: PASS.
- Zwölf neue reine Daten-/Archiv-/Workflow-Tests: PASS.
- Produktionsbindung: FAIL, weil der reale Abschlussbeleg noch fehlt.

Die Tests importieren ausschließlich den reinen Belegprüfer. Sie starten weder
Signer noch Netzwerkaufrufe und lesen keine Environment- oder Secret-Inhalte.
Ein Prep-Patch ist keine Signierfreigabe, kein Merge und keine Veröffentlichung.
