# Continuum Code54 – Originalsignierweg: geprüfte Vorbereitung, FREIGABE GESPERRT

Stand: 12.11.2026. Dies ist eine **Implementierung auf einem isolierten Vorbereitungszweig**, keine Originalsignierung. Quell-, Artefakt- und Zertifikatsgrenzen gelten unverändert. Nichts nach `main` gemergt.

## Verifizierte Produktions-/Artefaktidentität

| Merkmal | Wert |
| --- | --- |
| Produktquelle | `9713854bfb01d699f666058ac44379735307b66a` |
| Git-Quellbaum | `b815b5f2896b279edef15cad85aead82a85951af` |
| Produktpaket | `at.heinz.deviceai.continuumlocal` |
| Produktversion | `54 / 0.3.53` |
| ABI | `arm64-v8a` |
| QA-Run | [43134158265](https://github.com/Ullmann27/device-ai/actions/runs/43134158265) |
| Unsigned Job | `130916433295`, SUCCESS |
| Android Job | `130916433622`, noch nicht als erfolgreich nachgewiesen |
| GitHub-Unsigned-Artefakt | `13908580815`, `Code54-UNSIGNED-NOT-INSTALLABLE` |
| GitHub-Artefakt-ZIP-Bytes | `73553790` |
| GitHub-Artefakt-ZIP-SHA256 | `855e9877ed3eef8b96cf9da2dc96931dfd93a339800b3ce76a6894e5fb4a787b` |
| Entpackte APK-Bytes | `102864175` |
| Entpackte APK SHA256 | `eda9d67a56d3e1bdc934eee8eda7a5ff9d2ea649f5b767587ef078d8faf840ab` |
| Vergleich | GitHub-APK und zuvor geprüfte Host-APK bytegleich anhand SHA-256 |
| Originalzertifikat SHA256 (Fingerabdruck, kein Geheimnis) | `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` |
| Release-Gate | `BLOCKED_PENDING_NATIVE_PDF_OR_EXPLICIT_OWNER_EXCEPTION` |

Das GitHub-Artifact wurde **tatsächlich heruntergeladen**, auf ZIP-SHA256 geprüft und die APK mit Streaming-SHA256 aus dem Archiv ausgelesen. `BADGING.txt`, `BUILD-INFO.txt` und `SHA256SUMS.txt` wurden auf Version, Paket, Quelle und erwartete Unsigned-Kennzeichnung überprüft. Weder P12 noch Passwort wurden gelesen.

## Implementierung

1. Das existierende `scripts/continuum-code52-github-signer.sh` wurde um die **exakte Code54-Quell-SHA** als dritte Profilwahl erweitert; Code52/Code53 bleiben erhalten.
2. `scripts/continuum-code54-signing-evidence.py` überprüft SHA-gebundene GitHub-Quelle, Quellbaum, Workflowpfad, QA-Lauf, beide Jobs, Pflichtschritte, Artefakt-Provenienz und das komplette Archiv vor jeder Schlüsselverwendung.
3. Die Bindung `.github/continuum/code54-owner-signing-binding.json` enthält reale GitHub-Daten und einen **absichtlich gesperrten** Freigabestatus.
4. Die bestehende manuelle Workflow-Registrierung, geschützte Branchprüfung, Owner-Review-Umgebung, ursprüngliche Schlüssel-/Fingerabdruckprüfung, ZIP-Alignment und ausschließlich unveröffentlichter Draft bleiben unverändert.
5. `.github/workflows/continuum-code52-owner-signing-check.yml` prüft den neuen Prep-Zweig ohne Secrets oder Signierung.

## Tests und Belege

[GitHub Secrets-free Testlauf 43140668704](https://github.com/Ullmann27/device-ai/actions/runs/43140668704) auf `d3003074ffa24740f970d253fd4775434c1fe017`: **SUCCESS**.

- `bash -n` für den gemeinsamen Signierer: PASS.
- Bestehende Code52-Node-Verträge: **12/12 PASS**.
- Bestehende Code53-Python-Verträge: **16/16 PASS**.
- Neue Code54-Python-Verträge: **13/13 PASS**.
- **Insgesamt 41/41 überprüfte Vertragsfälle erfolgreich**, keine Signierung, keine Secrets.
- Negativfälle: falscher Git-SHA, falscher Quellbaum, falscher Workflow, fehlgeschlagene oder unvollständige QA, fehlender nativer PDF-Pflichtschritt, veränderte Artefakte, manipulierte ZIP-Member und unautorisierte Release-Scope werden abgewiesen.

Diese Tests sind Host-Vertragsprüfungen. Sie beweisen nicht die vollständige Android-Suite, eine echte PDF-Dekodierung, eine signierte APK oder Fold7-Wärmeverbesserung.

## Was zur Freigabe noch fehlt

1. Aktuellen [QA-Lauf](https://github.com/Ullmann27/device-ai/actions/runs/43134158265) vollständig dokumentieren. Der originale strikte PDF-Test läuft auf diesem GitHub-Host nicht, daher **kein vollständig grüner Android-Gesamtnachweis**.
2. Entweder einen **tatsächlich bestandenen** nativen PDF-Test auf geeigneter Android-Laufzeit quellen-/sha-genau dokumentieren, oder vom Besitzer eine separate ausdrückliche Ausnahme **nur für eine unveröffentlichte Code54-Gerätetest-APK** erhalten. Letzteres ist nicht implementiert und nicht stillschweigend aktivierbar; vor einer solchen Entscheidung ist ein separater sicherheitsgeprüfter Ausnahmevertrag erforderlich.
3. Erst nach dokumentierter Entscheidung und bestandenen Vertragstests darf die `release_gate`-Bindung geändert und die getestete Änderung in den vorhandenen geschützten Signierzweig integriert werden. Der Besitzer muss die exakte Produkt-SHA bestätigen.
4. Danach beginnt die manuelle [Originalsignierung](https://github.com/Ullmann27/device-ai/actions/workflows/continuum-code52-sign-release.yml) auf `chatgpt/code52-owner-signing-20261109`. Die vorhandene `continuum-original-signing`-Reviewer-Umgebung stoppt vor Schlüsselverwendung. Der Besitzer gibt dort selbst frei.
5. Erwartetes Ergebnis bei vollständigen Gates: **unveröffentlichter Release-Entwurf**, mit erneuter Signatur-/Paket-/SHA-Validierung. Keine automatische Veröffentlichung, keine automatische Installation.

## Wichtige Besitzerlinks

- [Code54 Integrations-PR #769](https://github.com/Ullmann27/device-ai/pull/769)
- [Prüfbelege PR #786](https://github.com/Ullmann27/device-ai/pull/786)
- [Unsignierter QA-Lauf #43134158265](https://github.com/Ullmann27/device-ai/actions/runs/43134158265)
- [Bestehender manueller Originalsignierworkflow](https://github.com/Ullmann27/device-ai/actions/workflows/continuum-code52-sign-release.yml)
- [Protected Environment-Einstellungen](https://github.com/Ullmann27/device-ai/settings/environments)
- [Actions Secrets-Namen prüfen](https://github.com/Ullmann27/device-ai/settings/secrets/actions)

**Status zum Abschluss dieses Dokuments:** Code54-Signierprofil vorbereitet und durch Secrets-freie Testverträge geprüft. Originalsignierung NICHT ausgelöst. Ausnahmeregel NICHT genehmigt. Originalsignierzweig NICHT verändert.
