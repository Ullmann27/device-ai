# Code54 Release-Reife – Kandidat `5dfe07e647aa9df7224ec5b0f178b5e59f4e0fc4`

| Punkt | Stand | Beleg |
|---|---|---|
| Paketkennung | `at.heinz.deviceai.continuumlocal` | `apk-unsigned-5dfe07e6/` aapt2-badging |
| versionCode / versionName | 54 / 0.3.53 | dto. |
| Native Bibliotheken | nur `arm64-v8a` (AArch64 ELF64, 16-KB-Alignment) | native-libraries.json |
| Originalavatar | `assets/avatar3d/continuum-avatar.glb` SHA-256 `c8b88385…` = **identisch mit Code53** `050a57cb` | assets.json |
| Kugel | gezeichnete Originalkugel im Code (keine Asset-Änderung) | Quellbaum |
| PDF-Dokumentasset | `cinema3d/document-workspace.glb` `903811921b…` | assets.json |
| Gemma-Modell | nicht im APK (separat geladen, unverändert) | Paketinventar |
| Hitzereparaturen (#773) | `1ba7b9458` ist Vorfahre des Kandidaten | `git merge-base --is-ancestor` |
| Gedächtnis-/Logik (#770/#772/#776/#778/#779) + Routing-Fix | im Kandidaten; `M3EvalGapsTest` grün | volle Suite |
| UI-Pflicht / Lint / volle Suite | 25/25 · keine Befunde · 4836/4/17; 3 der 4 FAIL unter Nicht-root+UTF-8 grün | `four-failures-5dfe07e6/MATRIX.md` |
| Unsignierter APK | PASS, **`7f463d4785466302aa188b8e81b5e75f32393654cf1ebde16c06c1845649127c`** – weiterhin aktuell (keine Quelländerung seit Bau) | `apk-unsigned-5dfe07e6/` |
| Signierweg | vorhandener Workflow `continuum-code50-sign-release.yml` (geschütztes Environment); **nicht ausgelöst** | – |

## Fehlende Voraussetzungen für die Originalsignierung
1. **Strikter nativer PDF-Test (A1)** auf echter Android-Laufzeit (Fold7 oder ARM64-Emulator mit KVM) – oder eine ausdrückliche
   Besitzerentscheidung, das Gate mit dieser einen dokumentierten Ausnahme zu signieren.
2. **SHA-genaue Besitzerfreigabe** für `5dfe07e647aa9df7224ec5b0f178b5e59f4e0fc4` (Kanalwahl ist keine Signaturfreigabe).
Danach: Signierung über den geschützten Weg, Installierbarkeit als In-Place-Update über Code53, Fold7-Abnahme.
Keine Installation, keine Veröffentlichung, kein `main`.
