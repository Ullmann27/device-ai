# Code54 – Vorbereitung des Originalsignierwegs (Stand 12.11.2026, NICHT ausgeführt)

**Status: VORBEREITUNG / NICHT IMPLEMENTIERT / NICHT SIGNIERT.** Kein Workflow wurde ausgelöst, kein Geheimnis gelesen.

## Feststehende Kandidatendaten
| Feld | Wert |
|---|---|
| Quelle | `9713854bfb01d699f666058ac44379735307b66a` (`integrate/omega-apk54-20261111`) |
| Quellbaum | `b815b5f2896b279edef15cad85aead82a85951af` |
| Paket / Version | `at.heinz.deviceai.continuumlocal`, versionCode 54, versionName 0.3.53, nur arm64-v8a |
| Host-APK (unsigniert, validiert) | SHA-256 `eda9d67a56d3e1bdc934eee8eda7a5ff9d2ea649f5b767587ef078d8faf840ab`, 102 864 175 Bytes (`../evidence/omega-apk54-20261111/apk-unsigned-9713854b/`) |
| GitHub-QA-Lauf auf genau dieser Quelle | [43134158265](https://github.com/Ullmann27/device-ai/actions/runs/43134158265) (push, Stand 01:53 UTC: **in_progress**) |
| Vorgänger-Release (Basis) | Code53 / 0.3.52, Quelle `050a57cb…`, originalsigniert |

## Wie Code53 signiert wurde (Präzedenz, gelesen)
- Zweig `prep/code53-original-signer-20261111` → geschützter Signierzweig `chatgpt/code52-owner-signing-20261109`.
- Workflow `.github/workflows/continuum-code52-sign-release.yml` (auf `main` registriert), Environment `continuum-original-signing`.
- `scripts/continuum-code52-github-signer.sh` aktiviert ein Profil **nur** für die exakte Quell-SHA und bindet über
  `.github/continuum/code53-owner-signing-binding.json`: Quellbaum, Workflow-SHA, QA-Run-ID/-Attempt/-Job, Artefakt-IDs,
  ZIP-Digests und APK-SHA. Signiert wird das **unsignierte APK-Artefakt des GitHub-QA-Laufs**, nicht ein Host-Build.

## Was für Code54 noch fehlt (in dieser Reihenfolge)
1. **QA-Lauf 43134158265 abschließen lassen** und Ergebnis lesen. Der Job `unsigned-arm64-transport` ist unabhängig von
   `android-gates` und soll das Artefakt `Code54-UNSIGNED-NOT-INSTALLABLE` liefern. Danach dessen APK-SHA mit dem
   Host-APK `eda9d67a…` vergleichen (gleich = Reproduzierbarkeit belegt; ungleich = nur das GitHub-Artefakt binden).
2. **Native-PDF-Bedingung festlegen (Besitzerentscheidung).** `android-gates` verlangt `STRICT_NATIVE_PDF_REQUIRED`.
   Auf `ubuntu-latest` und auf diesem Host scheitert der Test an der fehlenden Android-Laufzeit (`FileDescriptor.getOwnerId$`).
   Optionen: (a) Test auf dem Fold7/Emulator ausführen und belegen; (b) ausdrückliche, SHA-gebundene Besitzerausnahme
   nur für diesen einen Test, im Binding dokumentiert. Ohne (a) oder (b) bleibt das Gate rot und der Signierer darf nicht laufen.
   Zusätzlich muss #789 (17 bekannte SKIPs) übernommen sein, sonst ist `android-gates` auch bei bestandenem PDF-Test rot.
3. **Code54-Profil im bestehenden Signierer** (kein neuer Signierer): auf Basis von `prep/code53-original-signer-20261111`
   ein Profil `code54`, das nur bei Quelle `9713854b…` greift; QA-Pfad `.github/workflows/continuum-code54-qa.yml`,
   Branch `integrate/omega-apk54-20261111`, Artefakt `Code54-UNSIGNED-NOT-INSTALLABLE`, Version 54/0.3.53,
   Basis-Release Code53; Binding-JSON mit Run-/Job-/Artefakt-IDs und Digests; Contract-Tests analog Code53.
   Prüfen: Der Code53-Signierer erwartet `event == workflow_dispatch` für den QA-Lauf – der Code54-Lauf ist `push`.
   Das ist entweder per neuem `workflow_dispatch`-Lauf mit `expected_sha` zu lösen oder ausdrücklich im Profil zu begründen.
4. **Environment `continuum-original-signing`:** die Branch-Regel zeigt auf den geschützten Signierzweig; nur ändern,
   wenn der Signierzweig wechselt. Pflicht-Reviewer bleibt der Besitzer.
5. **Erst danach** startet der Besitzer den Signierlauf mit `source_sha = owner_approved_sha = 9713854bfb01d699f666058ac44379735307b66a`
   und der QA-Run-ID aus Schritt 1; GitHub hält vor `release-original-signed` an → „Review deployments“ →
   `continuum-original-signing` → „Approve and deploy“. Ergebnis ist ein **unveröffentlichter Entwurf**, der vor Installation
   auf Zertifikat, Paket, Version und Hash geprüft wird.

## Nicht erfolgt
Signierlauf, Release, Installation, Änderung an `main`, Lesen/Drucken von P12 oder Passwort, Fold7-Test: NOT_RUN.
