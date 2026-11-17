# Abgleich README-Release-Stopp (v31) mit dem tatsächlichen Gerätestand – 03.11.2026

Nur Dokumentation. Kein Release, kein Tag, kein Main-Merge, keine Signier-/Workflow-Änderung.

## Widerspruch
`README.md`/`AGENTS.md` (31.10.2026) nennen `release/continuum-local-complete-v31-20261031` als Source-of-Truth
für die nächste Besitzer-APK (Ziel 0.3.30 / versionCode 31) und verlangen „keine weitere Funktionsforschung“.

## Belegter Ist-Stand (git, dieser Rechner)
| Prüfung | Ergebnis |
|---|---|
| Kopf `release/continuum-local-complete-v31-20261031` | `d2d13eab` (31.10.2026) |
| Installierte Geräte-Releasesource 0.3.38 / versionCode 39 (PR #364-Kommentar 03.11., Besitzer-Abnahme) | `a9709854` auf `research/continuum-m8-render-signed-release-20261031` |
| `git merge-base --is-ancestor d2d13eab a9709854` | **ja** – v31 vollständig in Code 39 enthalten |
| `git log d2d13eab ^<dieser Branch>` | **0 Commits** – v31 vollständig im Integrationsbranch enthalten |

## Folgerung
Der v31-Release-Stopp ist **historisch und durch den veröffentlichten Code-39-Stand überholt**. Er blockiert die
Kette #365→#369→#370/#371/#372 nicht, weil diese auf `a9709854` aufsetzt. Gültig bleiben unverändert: Paket
`at.heinz.deviceai.continuumlocal`, Original-Signer `6354e91d…ee23`, versionCode monoton (nächste In-place-APK ≥ 40,
nur über den bestehenden original signierenden Render-Pfad), keine GitHub-Actions als Abnahme, Fold7-Geräteabnahme vor Freigabe.

Lokal geprüft (unsigniert, ohne Schlüssel): `assembleRelease -PdeviceAiMentorPreview=true -PcontinuumLocalRenderUnsigned=true
-PcontinuumStandaloneVersionCode=40` → aapt2: `package=at.heinz.deviceai.continuumlocal versionCode=40 versionName=0.3.39`,
targetSdk 36, `apksigner`: nicht signiert (erwartet).

## Empfehlung an den Besitzer (Entscheidung offen)
README-/AGENTS-Kopfbanner auf „Gerät 0.3.38/39 = a9709854; nächster Kandidat = Integrationsbranch nach Fold7-Abnahme“ umstellen.
Dieser Branch ergänzt nur einen Hinweis im README und ändert den Banner selbst nicht.
