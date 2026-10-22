# Continuum – Render-first Android-Betriebsweg

Stand: 22.10.2026 · review-only · kein GitHub Actions nötig

## Zuständigkeiten

- **GitHub**: Quellcode, Git-Historie, Review-Branches und Pull Requests. GitHub Actions ist **keine** Voraussetzung für die folgende Pipeline.
- **Samsung ARM64 / Termux**: bestehender offline Android-Builder, lokale Node-/Backend-Prüfungen, optionaler Teamleader-Status. Nur `at.heinz.deviceai.preview` wird als experimenteller lokaler Build erzeugt.
- **Render**: bestehender signierter **Mentor-Pair-Update-Build** `at.heinz.deviceai.mentorpaired` mit bereits vorhandenem privaten Signiermaterial. Die lokale Preview-APK ist **nicht** dieselbe App.
- **Google Play**: getrennte künftige AAB-Pipeline mit Upload-Key, API- und Richtlinienprüfung; durch diesen Review-PR **nicht** eingerichtet.

## Bereits geprüfter Auslieferungsdienst – unverändert lassen

Render: `srv-dbaue53bc2fs73crkop0` / `device-ai-mentor-apk-preview`.

- Branch: `astra/render-mentor-apk-builder-20261019`.
- Build-Befehl: `bash scripts/render-mentor-preview.sh`.
- Publish-Verzeichnis: `mentor-build-output`.
- Auto-Deploy **aus**. Kein ungeprüfter Push löst einen Signierbuild aus.
- Letzter bestätigter Deploy `dep-dbcg2s3ncjis738cq95g`: 21.10.2026 `live`; Quell-SHA `17e22bc2d9e1851fd634f44b8aadc04479556983`.
- Nach bisher dokumentiertem Nachweis v0.3.11 / Code 14, Paket `at.heinz.deviceai.mentorpaired`, APK-SHA-256 `e529374f9e4eb4bde149c156e1f217405500c7c9c88711efa1b437d63bd01b72`. Vor Update die byteidentische Binärdatei selbst prüfen.
- Niemals das bestehende private Signiermaterial aus Render in PRs, Supportarchiv oder neue Tests kopieren.

## Direkter lokaler Einstieg (neuer, nicht auf Android ausgeführter Wrapper)

In einem **sauberen** vertrauenswürdigen Checkout, der die Datei `scripts/portable-android.sh` enthält:

```bash
bash scripts/portable-android.sh doctor
bash scripts/portable-android.sh status
bash scripts/portable-android.sh worker-status
bash scripts/portable-android.sh tests
bash scripts/portable-android.sh preview-apk
```

`doctor` und `status` delegieren an den vorhandenen Termux-Builder. `worker-status` liest nur den lokalen Teamleader-Bericht. `tests` führt Node-Tests, aus dem festgeschriebenen npm-Lockfile installierte Backend-Abhängigkeiten und Backend-Tests aus; nach dem Lauf müssen Git-HEAD und alle **getrackten** Quelldateien identisch sein. `preview-apk` führt dieselben Prüfungen und anschließend den bestehenden, zeitbegrenzten Android-Preview-Builder aus: Kotlin-JVM-Tests, Android-Lint, APK-Build, Signatur und Paketidentität. Es führt **keine** Installation oder Main-/Release-Mutation durch.

Vor dem Test verlangt der Wrapper einen sauberen Checkout und stoppt statt unbekannte Benutzerdateien zu löschen. `npm ci` betrifft ausschließlich die reproduzierbaren lokalen `backend/node_modules`-Abhängigkeiten. Testausgaben und der lokale Build-Status bleiben lokale, nicht als Release freigegebene Artefakte.

### Was ausdrücklich noch offen ist

1. Ein realer Samsung-Run der neuen Wrapper-Befehle; bisher nur Shell-Syntax und Negativfall lokal überprüft. Die bestehende Termux-Toolchain wurde früher bereits über das Android-Gerät belegt.
2. Die experimentellen Linux-Isolations-/GGUF-Ergebnisse aus PR #53 sind nicht automatisch im installierten Mentor-Pair-APK-Release enthalten.
3. Erst nach einem echten auf dem Gerät verifizierten In-App-Builder-Callback und signierten Update-Tests ist eine End-to-End-Aussage zulässig.
4. Ein automatisierter, ununterbrochener Google-Play-AAB-Release ist nicht Bestandteil dieses Schritts.

## Sichere Update-Reihenfolge

1. Quell-SHA, ursprüngliche Paketidentität, Versionscode, Teststatus und Signer der **installierten** App lesen; Benutzerlernstände separat über die vorgesehenen Exportwege sichern.
2. Änderungen nur im Review-Branch prüfen und in den bewährten Delivery-Branch integrieren, keine automatische Main-Promotion.
3. **Erst nach dem Review** den vorhandenen Render-Dienst (Auto-Deploy aus) gezielt auslösen und Deploy-Protokolle kontrollieren.
4. Signierten inhaltsadressierten APK-Download, SHA-256, `aapt` Paket und `apksigner` Zertifikat prüfen. Eine neue `.preview`-APK niemals als Update des `.mentorpaired` Pakets ausgeben.
5. Android bietet bei gleichem Paket, gleichem Signer und höherem Versionscode ein Update an. **Nie deinstallieren oder App-Daten löschen**, um einen Konflikt zu umgehen.

## Betrieb und Kosten

Der Build-Lauf bei Render nutzt **Render-Pipeline-Minuten und Bandbreite**, nicht GitHub-Actions-Minuten. Das ist keine Behauptung unendlicher oder kostenloser Kapazität. Der bestehende Dienst bleibt erhalten; dieser Review verursacht **keinen neuen Dienst, kein neues Bezahlabo und keinen Deploy**.

Render-Dokumentation: https://render.com/docs/static-sites

Zum gemeldeten GitHub-Actions-Kontingentproblem: https://github.com/Ullmann27/device-ai/issues/54
