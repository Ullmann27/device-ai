# Continuum Lokal – originalsignierter Releasekanal (M7)
Stand 2026-10-30 · Besitzerkontrollierter Ablauf · **Noch nicht Ende-zu-Ende abgenommen**

## Ausgangslage
- Aktueller APK-Integrationsstand: PR #265 (M6 EWM-v2, M8-Worker, Update-Button).
- Härtung darauf: PR #266 (ein Regressionstestlauf statt doppelter Fold7-Belastung, exakte Originalquell-Freigabe und Veröffentlichung aus signiertem Staging).
- Originalpaket: `at.heinz.deviceai.continuumlocal`.
- Original-Zertifikat SHA-256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
- Der Update-Button fragt nur `https://continuum-owner-v23-release-preflight.onrender.com/continuum-local/latest.json` ab und akzeptiert eine jüngere (mindestens versionCode 29), freigegebene Version mit **dem Originalzertifikat**, korrektem Paket, Größe und SHA-256.
- Auf dem zugehörigen GitHub-Releasezweig steht noch `{"schema":1,"available":false}`. Das ist **kein** herunterladbares Update.
- Bestehender Render Static-Site-Dienst `continuum-owner-v23-release-preflight`, ID `srv-dbf1tr2vcj2c73ehpfog`, Quellbranch `release/continuum-owner-v23-candidate-20261025`, Publish-Ordner `owner-v23-release-preflight`, **autoDeploy=no**. Der Build schreibt BUILD-INFO/Index und lässt eingecheckte Dateien unter `owner-v23-release-preflight/continuum-local/` bestehen.

## Owner-Milestone A – APK erstmals bauen und auf Fold7 installieren
Diese Kommandos sind für einen **vollständig heruntergeladenen, sauberen** Git-Checkout auf dem eigenen Fold7 mit bereits vorhandener Termux-JDK17-, SDK36- und Gradle-8.11.1-Toolchain. **Erst den fertig getesteten Git-Stand prüfen und exakt freigeben**, nicht automatisch ein unkontrolliertes HEAD signieren.

```bash
git remote -v
git status --short
git rev-parse HEAD
# Nach unabhängiger Owner-Quellfreigabe: geprüfte vollständige 40-stellige SHA eintragen.
export CONTINUUM_OWNER_LOCAL_APPROVED_SHA='<geprüfte-40-stellige-commit-sha>'
export CONTINUUM_OWNER_NEXT_VERSION_CODE=29
bash scripts/continuum-fold7-owner-update-gate.sh doctor "$CONTINUUM_OWNER_LOCAL_APPROVED_SHA"
bash scripts/continuum-fold7-owner-update-gate.sh owner "$CONTINUUM_OWNER_LOCAL_APPROVED_SHA"
```

Der Owner-Weg verifiziert Ursprung, Branch, sauberen Git-Stand, originale P12 und deren gespeicherten Fingerprint. Der eigentliche Standalone-Builder prüft den **exakt freigegebenen HEAD erneut** und führt einmal die komplette unsigned Regression durch, **bevor** er lokal das P12-Passwort interaktiv abfragt. Er erstellt und verifiziert die originalsignierte APK in `$HOME/storage/downloads/Continuum_back_up/`. Er **installiert sie nicht** und **ändert keinen produktiven Git-Branch**.

## Owner-Milestone B – bereits geprüfte signierte APK bereitstellen

```bash
# Exakten vom Build gemeldeten APK-Dateipfad unverändert verwenden:
node scripts/continuum-owner-release-stage.mjs '<originalsignierter-v29-apk-pfad>' "$CONTINUUM_OWNER_LOCAL_APPROVED_SHA"
# Der vorige Aufruf meldet z. B. den privaten STAGING-PFAD.
node scripts/continuum-owner-release-publish.mjs '<staging-pfad>' "$CONTINUUM_OWNER_LOCAL_APPROVED_SHA" --check

# Erst nach Sichtprüfung von Quelle, Version, P12-Zertifikat und Manifest:
export CONTINUUM_OWNER_RELEASE_PUBLISH_APPROVED_SHA="$CONTINUUM_OWNER_LOCAL_APPROVED_SHA"
node scripts/continuum-owner-release-publish.mjs '<staging-pfad>' "$CONTINUUM_OWNER_LOCAL_APPROVED_SHA" --publish
```

Der Publisher installiert oder signiert **nichts**. Er prüft die APK mit `apksigner`/ `aapt2` erneut über den bestehenden Manifest-Generator und verifiziert den Inhalt des privaten Stagings. Er erlaubt nur das Update eines **älteren, originalsignierten Releases** oder der anfänglichen `available:false`-Markierung. Die APK muss wegen GitHub-Grenzen unter 90 MB liegen; bei größeren APKs brauchen wir ein anderes geprüftes, kostenfreies Veröffentlichungs-Backend. Er schreibt nur `owner-v23-release-preflight/continuum-local/{Continuum-Lokal-<sha256>.apk,latest.json}` durch einen **nicht erzwungenen Fast-Forward-Push** auf den vorhandenen Veröffentlichungszweig. Er benötigt eine bereits autorisierte GitHub-Verbindung **auf dem Fold7**. Er schreibt nie nach `main`.

### Ein zusätzlicher, bisher fehlender Schritt: Render ausrollen
Der vorhandene Render-Dienst hat `autoDeploy=no`. Nach dem Push ist `OWNER_RELEASE_PUSHED_AWAITING_RENDER_DEPLOY` **noch keine Live-Freigabe**. Den Deploymentvorgang ausdrücklich beim bereits bestehenden Dienst `srv-dbf1tr2vcj2c73ehpfog` auslösen und überprüfen, dass **beide** Dateien über HTTPS wirklich abrufbar sind. Den alten Placeholder nur durch das tatsächlich originalsignierte Manifest ersetzen.

Es wurde **kein neuer Dienst** eingerichtet. Laut offizieller Render-Dokumentation sind Static Sites grundsätzlich kostenlos zu deployen, aber sie verbrauchen die enthaltenen Pipeline-Minuten und das Bandbreitenbudget des Workspaces. Bei Überschreitung können Zusatzkosten entstehen; deshalb vor Freigabe den bestehenden Abrechnungs-/Spending-Cap prüfen und nicht von garantiert 0 € ausgehen. Quellen: https://render.com/docs/free und https://render.com/pricing .

## Owner-Milestone C – Update-Knopf am Gerät prüfen
1. APK v29 auf dem Fold7 originalsigniert installieren, ohne bisherige App-Daten zu löschen.
2. Nach erfolgreicher Installation den Update-Knopf und die gespeicherten Daten prüfen.
3. **Für den realen End-to-End-Updatebeweis ein zweites, neueres originalsigniertes Release** vorbereiten und über denselben Kanal veröffentlichen. Erst dann kann der Button v29 → v30 den echten Download, Signaturcheck, PackageInstaller und Systemstatus beweisen.
4. Falsche Signatur, alte/gleiche Version, falsches Paket, ungültige SHA-256, nicht freigegebenes Manifest und Offline-Fall müssen fail-closed bleiben.
5. Die lokale Signatur/P12 dürfen **niemals** an Render, GitHub, eine Cloud-KI oder in Chatprotokolle übertragen werden.

## Ehrlicher Abnahmestand
- `SOURCE_PREPARED`: JA, Branch/PR #266.
- `OWNER_CODE_APPROVED`: noch ausständig.
- `NODE_TESTS_ON_NEW_SHA`: ausständig (die früheren 254/254 beziehen sich auf PR #265 vor diesen Änderungen).
- `ANDROID_GRADLE_LINT_BUILD`: ausständig.
- `OWNER_P12_APK_SIGNED`: ausständig.
- `STAGED_RELEASE`: ausständig.
- `LIVE_RENDER_DEPLOY`: ausständig.
- `FOLD7_UPDATE_V29_TO_V30`: ausständig.
- `UNATTENDED_FUTURE_OWNER_SIGNING`: **nicht implementiert**. Ein Update-Button allein erzeugt weder neue signierte APKs noch veröffentlicht er sie. Dafür ist ein gesonderter lokal gesicherter Release-Worker nötig; hier wird **kein Passwort dauerhaft gespeichert**.

Keiner dieser ausstehenden Schritte darf als erfolgreich dargestellt werden, bevor er am konkreten Git-/APK-Stand wirklich ausgeführt und belegt wurde.


## Unabhängige HTTPS-Nachkontrolle ab 31.10.2026 (read-only)

Der bisherige Publish-Vorgang endet beim Git-Push; der Render-Dienst hat weiterhin \`autoDeploy=no\`. Eine erfolgreiche Render-Deploymentmeldung allein beweist weder ein veröffentlichtes Manifest noch die Abrufbarkeit der identischen APK. Deshalb ist der eigenständige Node-22+-Prüfer \`scripts/continuum-owner-render-readback.mjs\` hinzugekommen:

\`\`\`bash
node --test scripts/continuum-owner-render-readback.test.mjs
node scripts/continuum-owner-render-readback.mjs --manifest-only
# Erst nach tatsächlicher Besitzer-Releasefreigabe und Render-Deploy:
node scripts/continuum-owner-render-readback.mjs
\`\`\`

- \`NO_RELEASE\` bedeutet: exakter und gültiger \`{"schema":1,"available":false}\`-Platzhalter; **kein Update vorhanden**.
- \`MANIFEST_ONLY_APK_NOT_VERIFIED\` bedeutet: Release-Deklaration strukturell geprüft, **APK nicht geprüft**.
- \`APK_HASH_VERIFIED_CERT_NOT_VERIFIED\` bedeutet: Exakte öffentlich heruntergeladene APK-Bytes gegen manifestierte Länge und SHA-256 geprüft, **aber keine eigenständige P12-/APK-Zertifikatsprüfung**. Diese bleibt Aufgabe des vorhandenen lokalen Owner-Verifiers und des Android-Updaters.

Der Prüfer verwendet nur den hart codierten Render-HTTPS-Host, lässt keine Weiterleitungen zu, begrenzt das Manifest auf 8192 Bytes und den APK-Stream auf 90 MB, verhindert Metadaten-Typumwandlungen und hält die APK nicht als Ganzes im RAM. Er hat weder Schreibzugriff auf GitHub noch Schlüsselzugriff, keine Deploy-Funktion und verändert die App nicht. Er macht aus einem veröffentlichten Manifest **keine** Besitzerfreigabe.

**Nachweisgrenze:** Acht selbständige Offline-Tests gegen simulierte HTTP-Streams bestanden im lokalen Node-22-Lauf. Der öffentliche Render-Abruf sowie ein vollständiger Android-Build auf dieser neuen Git-SHA sind dadurch **nicht** nachgewiesen. Den Live-Dienst erst nach originalsignierter Owner-APK und gesonderter Deploymentfreigabe verändern.
