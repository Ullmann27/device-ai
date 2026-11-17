# Continuum 0.3.22 / v23 – Besitzer-Haupt-App-Integrationsstand

Stand: 2026-10-25. Repository: privates `Ullmann27/device-ai`.
**Ziel:** Dieselbe bisher installierte Besitzer-App **aktualisieren**, keine weitere Preview-APK.

## Ausgangsbasis und tatsächlich eingebaute Quellen
- Astra vollständig gesicherter und berichteter Prüfstand PR #133: `5824126e25d3672156a8bf1487c20164497d5481`; Meldung: Node 1202 PASS/6 SKIP, Android Unit 975 PASS/3 SKIP, Backend 60 PASS, Kotlin-Compile und konfigurierte NewApi-Lint. Diese Ergebnisse beziehen sich auf **Astras Originalbranch**, nicht auf diesen v23-Kombinationsbranch.
- Von PR #135, HEAD `e7ac9fdd94f9c568e1a3a55f42846c88dddb87d9`: vollständig neueste `ContinuumMobileBuilderActivity.kt`, `ContinuumTypedCodingBridge.kt`, neuer `ContinuumCodingPreparationReviewTest.kt` und `AGENTS.md` durch GitHub-Blobinhalt übernommen.
- Astras LocalConversationContext, BoundedAgent, LiteRT native reset, Memory-Provenienz, VH-SR und begleitende Testquellen bleiben aus PR #133 erhalten.
- `app/build.gradle.kts`: für `deviceAiMentorPreview=true` UND `deviceAiOwnerSignedUpdate=true` erhöht sich die Versionskennung auf **23 / 0.3.22**; derselbe Paketname `at.heinz.deviceai.mentorpaired` und die verpflichtende bestehende Mentor-Signierkonfiguration bleiben bestehen.
- `scripts/continuum-v22-owner-release.test.mjs` prüft weiter den alten v22-Releasepfad und den v23-Gradle-Nachfolger. **Eine separate v23-Owner-Freigabekette ist noch nicht implementiert.**
- `scripts/continuum-owner-v23-qa.sh`: Reproduktionsvorbereitung für Node, Backend und Android-Kotlin/JUnit/NewApi-Lint **OHNE** Ausstellen einer APK oder Auslesen privater Signierschlüssel.

## Vier Kontrollgänge / Grenzen

1. **Astra-Originalstand:** PR #133 ist veröffentlicht; Tests durch Astra am Original-HEAD dokumentiert. Keine echte Fold7-Modellabnahme; LM1 nicht als Hauptdialoglaufzeit nachgewiesen.
2. **Branch-Abgleich:** PR #134/#135/#136/#137/#138 repräsentieren zusätzliche Dokumentations-, Android-, OMEGA- und Signaturforschung. PR #135 relevante Android-Auftragsprüfung wurde gezielt kopiert; die anderen Research-Prototypen besitzen keine automatische Produktintegration. P-256 ohne persistente Nonce darf **keine** CCC1-Grantrechte erstellen.
3. **End-to-End-Anbindung:** Neue Android-Review-Anzeige und Bridge stammen wortgleich aus PR #135; der echte Zusammenschluss mit Astras aktualisiertem Appcode muss erst **im v23-Kombinationsbranch** kompiliert/getestet werden. Host-, Test- und Geräteausführung nicht verwechseln.
4. **APK-Build und Signierung:** Altes signiertes APK ist über `https://device-ai-mentor-apk-preview.onrender.com/BUILD-INFO.txt` als `version_code=22`, `version_name=0.3.21`, `package=at.heinz.deviceai.mentorpaired` dokumentiert. Das im bisherigen v22-Releaseprüfskript bekannte Zertifikat SHA-256 ist `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`. Eine neue Besitzer-APK darf nur nach erfolgreicher **v23-spezifischer** Source-Manifest-/Besitzerfreigabe und Signierprüfung veröffentlicht werden. Niemals v22-Freigabe für neue Quellen wiederverwenden.

## Aktueller Render-Blocker

Am 25.10.2026 verweigerte Render die Erstellung eines weiteren QA-Dienstes mit `Hobby Tier is limited to 25 services`. Render-Service-Liste zeigte noch 24 Services und einen Key-Value-Speicher. Als gelöscht vermutete fehlgeschlagene QA-Dienste waren beim direkten Nachprüfen **nur suspended** (z. B. `srv-dbeh2khsrm7s73c4jmi0`, `srv-dbeh1e2vcj2c73cu8k30`). Suspendieren schafft keinen neuen Slot. Die GitHub-/Render-Connectoren können Render-Service-Löschung aktuell nicht selbst ausführen. Keine Produktivservices oder die signierende Render-Instanz entfernen.

## Voraussetzung für Haupt-App-Update 0.3.22

1. Alte **Render QA-Services wirklich löschen** (nicht nur Suspend) und die Service-Anzahl/Quota über aktuellen Dienstabruf bestätigen. Bewahre `device-ai-mentor-apk-preview` mit bisherigem Signierkontext, `device-ai-continuum-mentor` und das fremde `lumo-ai-proxy`.
2. Auf dem exakt gepinnten Integrations-HEAD zuerst `scripts/continuum-owner-v23-qa.sh` laufen lassen und Tests/Logs mit Commit, Umgebung und SHA sichern. Nicht ausgeführte Tests = NOT_RUN.
3. Dann **neue** v23-Owner-Release-Autorisierung mit vollständiger Source-Blob-Inventur, explizit genehmigtem Commit, derselben privaten Render-Signieridentität, vorheriger signierter v22-Rollback-APK und Fail-Closed-Regeln prüfen. Signiermaterial niemals im GitHub-Code oder Chat veröffentlichen. Signierer nicht ohne freigegebenes Release-Manifest benutzen.
4. Vor Veröffentlichung: `apksigner verify --print-certs`, `aapt dump badging` auf erwartete `at.heinz.deviceai.mentorpaired`, `23`, `0.3.22`; Original-v22-APK vorher unverändert sichern und SHA256 validieren. Mehrfach-Kontrollgänge und Nutzergeräteversuche klar protokollieren.
5. **Erst wenn dieser Nachweis vorliegt**: Besitzer erhält einen verifizierten, direkten Download-Link und Anleitung zum In-Place-Update. Vorher keinen vermeintlich funktionierenden APK-Link angeben.

## Für Nutzer auf dem Fold7: spätere Funktionsabnahme
- Dialog durch drei bis vier Turns einschließlich Kontextverwendung und Sessionwechsel.
- Rückfrage nach nachweislich bestätigter Erinnerung plus Quellen-/Widerrufsverhalten.
- Komplexe Anfrage: sinnvolle Teilplanung.
- Ein sicher erlaubtes READ_ONLY-Werkzeug, Beobachtung und Antwort.
- Eindeutig falsche Antwort mit Korrektur anhand prüfbarer Evidenz.
- Keine präparierten Demoerfolge als echte Modellintelligenz ausgeben.
- Reale Latenz, Peak-RAM, Stabilität und gegebenenfalls GPU-/Akkubelastung messen.
- Privilegierte Coding-/Geräteaktionen nur über bestehende Owner- und ToolGate-Gates. 

Kein `main`-Merge, kein APK-Update, keine Besitzer-Schlüsseländerung, keine produktiven Erinnerungs- oder Modellgewichtsänderungen in diesem Forschungscheckpoint.
