# Device AI Continuum – Owner-Update 0.3.17 / CCC1 + Premium-UI
Status: 23. Oktober 2026. **RELEASE-KANDIDAT: NICHT GEBAUT, NICHT INSTALLIERT, NICHT FREIGEGEBEN.**

## Ziel ohne Designverlust

Die nächste, vorhandene Besitzer-App aktualisierende APK muss das **aktuellste von Astra tatsächlich gespeicherte und geprüfte Premium-Design** sowie die neueste **CCC1-Android/Termux-Reparaturkette** in **einer gemeinsamen Quellversion** enthalten. Kein separates altes Hologramm, kein Weglassen des Chat-/Fold7-Fixes, kein zweiter CCC1-Kern, kein Wechsel zu einem anderen APK-Paket.

| Merkmal | Bestätigung |
|---|---|
| Bisher installiert | `0.3.16`, `versionCode 17`, `at.heinz.deviceai.mentorpaired` |
| Neuer Kandidat | Owner-signed Profil **0.3.17 / versionCode 18** nur auf diesem isolierten Zweig |
| Signer-Fingerprint | `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc` |
| CCC1-Quelle | Opus `opus/ccc1-e2e-20261023`; echte Worker-Reparatur/Verifier-Belege als Linux-Quellnachweise; **keine Fold7-E2E-Abnahme** |
| UI-Basis | PR #97/98 mit Hologramm/Bento/Fold7-Fix; **Astras neuester Cloud-Glow-/Renderer-Patch ist noch nicht als GitHub-Commit vorhanden** |
| Signierdienst | Render `device-ai-mentor-apk-preview` (`srv-dbaue53bc2fs73crkop0`): vorhandene **0.3.16**-Auslieferung, wird NICHT umgestellt |
| Neuer Release-Build | `scripts/render-mentor-owner-update-v18.sh` – eigener Skriptpfad, noch NICHT ausgeführt |
| Release-Sicherheitsprüfung | `.github/continuum/continuum-owner-v18-release.test.mjs`; wird vor dem Signieren ausgeführt |
| GitHub CI | Eigener Quellen-Vorprüflauf `39967606901` endet innerhalb des GitHub-Runners mit `failure` ohne Steps/Logs – **nicht als bestandener oder fehlerhafter Quelltest werten** |

## Abgeschlossene Repository-Arbeit

1. Von Opus' neuestem getesteten CCC1-Zweig separaten Buildkandidaten erstellt, ohne `main` oder die vorhandene Render-Signer-Quelle zu verändern.
2. Kotlin-Gradle: In **nur dem Owner-Update-Profil** Versionscode von 17 auf 18, Versionsname auf 0.3.17 angehoben. Paket-ID und Signierkonfiguration nicht verändert.
3. Aus PR #98 die Designintegrationsregeln vollständig übernommen; der Fold7-Reflow-Quelltest war bereits identisch.
4. Neue V18-Buildroute erstellt: sauberer exakt gepinnter Quell-SHA, *Astra-UI-Commit als Git-Vorfahre erforderlich*, qualitätsgeprüfter Node-/Backend-/Gradle-/Lint-Build, gleiches echtes Besitzerzertifikat, AAPT-Paket-/Versionsprüfung, **echter wiederbeschaffbarer v0.3.16-Rollback mit Hash und gleicher Signatur**, eigenständiges unveränderliches APK-Dateinamen-Schema und BUILD-INFO mit wahrheitsgetreuem Status.
5. Fünf automatisierte Release-Source-Gates erstellt. Ein No-Secrets-Workflow ist eingerichtet; GitHub-Runnerstörung verhindert aktuell die Ausführung dieser Tests auf GitHub. **Kein Test-PASS erfunden.**

## Noch fehlende Voraussetzungen – bitte keine unvollständige Besitzer-APK verteilen

1. **Astra-Arbeit sichern:** Die letzte Cloud-Sitzung berichtete ~53–54 % weniger JVM-Renderer-Allokationen und visuelle QA. Der letzte Remote-Durchlauf wurde als fehlgeschlagen angezeigt. Ein daraus hervorgegangener überprüfbarer GitHub-Commit fehlt. Erst die tatsächlich fertigen Dateien, Tests, Renderbelege und Referenzvergleiche sichern.
2. **Konfliktfreie gemeinsame Integration:** Die letzte Astra-UI-Änderung als vollständigen Git-Commit in diesen v18-Branch integrieren; `AgentActivity`, Hologramm-Renderer, Fold7-Eingabe, CCC1/Termux-Rückkanal und Sicherheitsbelege gemeinsam erhalten. `git merge-base --is-ancestor <ASTRA_SHA> HEAD` muss wahr sein.
3. **Vollprüfung:** Gesamte Node/Backend-Tests; Gradle `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`; echte Android-SDK-/Emulator- oder Fold7-Ansichten und CCC1-Abbruch-/Freigabeabläufe, einschließlich Modelllast/RAM.
4. **Signing-Umgebung:** Die neue, vom alten v17-Publikationsdienst getrennte vertrauenswürdige Bauumgebung benötigt den *bereits vorhandenen Besitzer-Signierschlüssel*, ohne diesen nach GitHub oder in Logs zu kopieren. Auf dem bestehenden Render-0.3.16-Dienst wurde nichts geändert.
5. **Rollback:** Die echte Vorgänger-APK `0.3.16` mit HASH/SIGNER/PACKAGE vor neuer Veröffentlichung herunterladen und lokal/extern unveränderlich archivieren; `BUILD-INFO` allein reicht nicht.
6. **APK-Bau:** Erst auf einem freigegebenen gemeinsamen, sauberen Commit den V18-Build starten und das resultierende APK anhand AAPT, APKSigner, SHA-256 und Versionscode 18 prüfen.
7. **Samsung Fold7:** APK über Androids Installer als In-Place-Update installieren, bestehende Nutzer-/Trainingsdaten erhalten, Chat/IME/Fold, Hologramm/Glas und CCC1-Freigabe/Cancel/Worker-Funktion gegen echte Anwendung prüfen.

## Verbindlicher Abschlussbeweis

Freigabe erst mit konkretem Dateinamen `DeviceAI-Continuum-v0.3.17-<sha256>.apk`, tatsächlichem SHA-256, signierter Paket-ID/Fingerprint, Build-Log zum Quell-SHA, vollständigem Testreport und Fold7-Abnahme. Ohne diese Werte bleibt es **PREPARED_ONLY**.

Keine falsche Abschlussmeldung, keine unbestätigten Erfolgsmeldungen, keine Designreduktion. Das v17-Signer-Projekt bleibt betriebsfähig.
