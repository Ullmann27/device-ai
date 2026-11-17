# Continuum 0.3.20 – gesonderte Besitzerfreigabe für den neuen Signierpfad
Datum: 24.10.2026 · Repository: Ullmann27/device-ai

## Wichtige Grenze
Diese Änderung ist ein **ungeprüfter Release-Entwicklungsentwurf**, keine neue signierte APK. Die in PR #117 geprüften **Android-Produktquellen** bleiben byteidentisch. Alte v18/v19/v20-Rezepte bleiben erhalten. Niemals durch eine Debugsignatur aktualisieren.

- Letzter unabhängig getesteter Android-Quell-/Testcommit: `d7429145ad5dff64364ed47f9ceb0c07a5a8ed85`
- Vorherige signierte 0.3.19: `afd8ea3f0ff6e73c646b71b60d1157977abbcd62`
- Paket: `at.heinz.deviceai.mentorpaired`
- Zielversion: `0.3.20`, Code `21`
- Bisheriger Eigentümer-Signierer, Zertifikat SHA-256: `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`
- Quelle des Pin-Vorschlags: `docs/evidence/astra-0320-20261024/v21-source-proposal.json`
- Quellmanifest-SHA-256 laut unabhängiger Astra-Herleitung: `134e847b8f2fad2f8131cdf3e962679eb1630ed12801973ad0f6cbb0799e177d`
- Manifest: 245 vorhandene Quellen, davon 14 seit0.3.19 verändert; sechs erstmals nicht in den alten34 Pins enthaltene Hauptquellen.
- Besondere Sicherheitsforderung: Besitzerfreigabe für Quellmanifest **und** exakt festgefrorenen v21-Builder-HEAD, zusätzlich zu Testnachweis und privatem bestehendem Signierer.

## In diesem eigenständigen Prüfbranch ergänzt
1. `scripts/render-mentor-preview.sh`: neuer Fall21, ohne Freigabe des exakten Buildcommits Exit69.
2. `scripts/continuum-v21-source-gate.mjs`: read-only. Vergleicht 245 Pfade mit Git-Blob/SHA-256, prüft Metadaten, prüft alle getrackten Android-Hauptdateien, lehnt Symlinkumleitungen und geänderte/ungetrackte Quellen ab. Ohne die zwei getrennten Besitzerfreigaben ebenfalls Exit69.
3. `scripts/render-mentor-owner-update-v21.sh`: separates, aus dem v20-Vertrag abgeleitetes Rezept. Kompiliert/testet vor der Signierung, verwendet ausschließlich bestehenden privaten P12-Signierer, prüft v20-Rollback-APK über HTTPS/Hash/Zertifikat/Paket und kontrolliert das neue Paket vor Veröffentlichung.
4. `.github/continuum/continuum-v21-owner-release.test.mjs`: Negativtests für fehlende/falsche Freigaben und Prüfung der Bash-Syntax.
   
**Die hier neu hinzugefügten Release-Skripte wurden noch nicht auf Render oder mit dem vollen Android-SDK tatsächlich ausgeführt.** Vor echten Umgebungsänderungen Tests und Sicherheitsprüfung zwingend nachholen. Der frühere Quellstand d7429145 wurde dagegen vollständig von Astra geprüft.

## Noch erforderlich
- Exakte Freigabe des Besitzers für den **festen geprüften Quellcommit d7429145...**, das Quellmanifest und den **später endgültig feststehenden Builder-Commit** dieses Release-Prüfzweigs. Allgemeine Wünsche wie „neue APK“ sind kein Ersatz für eine konkrete Quellfreigabe.
- Bestätigung, welcher **Render-Workspace** und welcher konkrete Dienst das bestehende private Eigentümerzertifikat besitzen. Es ist unzulässig, dies durch eine zufällige Auswahl zu ersetzen.
- `node --test .github/continuum/continuum-v21-owner-release.test.mjs` und anschließend komplette Regression mit echten SDK/Node/Backend-Toolchains auf dem **festgefrorenen finalen Buildcommit**; SHA-Neupinning nur mit Freigabe.
- Den Dienst ausdrücklich auf den freigegebenen v21-Branch umstellen, wenn bislang die alte 0.3.19-Basis konfiguriert ist. Kein Autodeploy unfreigegebener Branches. Alle historischen Freigabevariablen unangetastet lassen.
- Nur nach Freigabe Variablen setzen: `CONTINUUM_OWNER_RELEASE_VERSION=21`, `CONTINUUM_RELEASE_SHA=<GENAUER_NEUER_BUILDER_SHA>`, `CONTINUUM_OWNER_V21_APPROVED_SHA=<GLEICHER_SHA>`, `CONTINUUM_OWNER_V21_APPROVED_SOURCE_SHA=d7429145ad5dff64364ed47f9ceb0c07a5a8ed85`, `CONTINUUM_OWNER_V21_APPROVED_MANIFEST_SHA256=134e847b8f2fad2f8131cdf3e962679eb1630ed12801973ad0f6cbb0799e177d`. Nicht als Zugangsdaten in Git committen.
- Download ausschließlich nach neuem erfolgreichen Signier-/Hash-/Badging-/HTTPS-Nachweis ausgeben. 0.3.19 ist **kein** 0.3.20-Artefakt. Die Installation auf dem echten Fold7 und die dortige Hologrammbewegung bleiben eigene Prüfungen.

## Qualitätsgrenzen
- Teiltests und statische Quellverifikation ersetzen keinen Render-Signierlauf.
- Keinesfalls Sicherheitsschalter umstellen, um Exit69 zu umgehen.
- Main unverändert. Die gesamte Quelle einschließlich SWB1/CRE1, Archiv, Chatverlauf, CTX3-Sicherheitsmodus und experimentellem LM1 bleibt erhalten; nichts wird automatisch produktiv gepromotet.
