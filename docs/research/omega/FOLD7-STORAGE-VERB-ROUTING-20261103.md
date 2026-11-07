# Fold7 Regression: „Speichere nicht“ darf kein Speicher-Tool auslösen
Stand: 03.11.2026 · Besitzerbefund aus Continuum Lokal 0.3.38/versionCode39 · Paket `at.heinz.deviceai.continuumlocal`

## Reproduzierter Gerätefehler (RED)
Der Besitzer hat in der laufenden Original-App und bei ausgeschaltetem Internet eingegeben:

> Für diese Testaufgabe gilt: In der roten Kiste liegen 4 Kupferringe und in der blauen Kiste 7 Kupferringe. Welche Kiste enthält mehr? Antworte kurz. Speichere diese Angaben nicht dauerhaft.

Tatsächlicher App-Output: `Speicher prüfen: Speicherbeobachtung v1` mit Geräte-Bytewerten. Erwartet war eine lokale Gesprächsantwort zur **blauen Kiste (7 Ringe)** ohne Datenträgerabfrage und ohne dauerhafte Besitzer-Faktschreibung. Die Folgeeingabe „Korrektur: In der roten Kiste liegen jetzt 9 statt 4 ...“ führte auf dem Gerät zu einem `gemma4-e4b-it · ANTWORTENTWURF · NOCH UNGEPRÜFT`; das Modell erklärte, es habe keinen Zugriff auf den früheren Gesprächsinhalt. Der Screenshot zeigt laufende Generierung/STOP, daher ist dieser Entwurf **kein verifizierter finaler Antwortzustand**.

## Quellcodeursache, exakter Release SHA
Originalsignierter Live-Release: `a9709854f48d2fd6589f9ce5ea97f6ec3be0d233` (Code39). `BootstrapBrain.decide()` nutzte `"speicher" in low`. Die Zeichenfolge ist Teil der Verben `speichere` und `speichern`, sodass `get_storage_info` gestartet wurde. Der kurz zuvor eingebaute `OwnerChatIntentRouter` erkennt nur begrenzte exakte Intents und konnte diesen langen Satz nicht als Storage-Intent erkennen; die spätere Broad-Match-Logik des BootstrapBrain umging diese Schutzwirkung.

Für Toolresultate ruft `AgentActivity.executeBootstrapReadOnly()` anschließend `addAssistantMessage()` auf, was regulär den lokalen Modell-Gesprächskontext räumt. Damit fehlen bei der nächsten Korrektur die ursprünglichen Kistenzahlen; das ist eine aus dem Code abgeleitete Ursachenverkettung, kein gesondert aufgezeichneter Android-Trace.

## Source-Fix in separatem Branch
Branch `repair/continuum-fold7-storage-verb-routing-20261103` ab exakt dem veröffentlichten Release. Unverändert bleiben ToolGate, Android-Berechtigungen, bestehender Modellpfad, Gedächtnis, Original-Signatur, Render-Publisher und `main`.

- `app/src/main/java/at/heinz/deviceai/BootstrapBrain.kt`: Wortgrenzenerkennung für Speicher-Substantive (Speicher, Speicherplatz, Speicherstatus, Speicherkapazität) ersetzt Teilstringtreffer in `speichere` / `speichern`.
- `app/src/test/java/at/heinz/deviceai/BootstrapBrainTest.kt`: echter Besitzerprompt, drei weitere negative Verb-/Speicherverbot-Fälle, fünf echte positive Speicher-Intents. Sicherstellen: Gesprächsfall `BootstrapKind.REPLY` + `allowNeuralRecall` und kein `tool`; Geräteabfrage bleibt nur für passende Begriffe aktiv.

## Ausgeführte Prüfungen
- Auf unabhängigem lokalem **Kotlin/JVM 1.9.0, JDK21** wurde der gleiche Regex-Ausdruck kompiliert und gegen vier negative sowie fünf positive Fälle ausgeführt: **9/9 PASS**. Das ist ein **isolierter lexikalischer Syntax-/Verhaltenstest**, keine Ausführung der ganzen Android-App und nicht der JUnit-Projektklasse.
- GitHub: Quellcode und JUnit-Regressionen auf diesem Branch gespeichert, Draft-PR #365 offen.
- **NOT_RUN:** vollständige Android Gradle/SDK36/JUnit/Node/lint-QA auf diesem neuen SHA, unabhängiger Reviewer, originalsignierte Code40-APK, erneuter Fold7-Test, voller Mehrturn-Dialog/Native Modellkontext.

## Sicherheits- und Weiterarbeitsvertrag
1. Exakten neuesten Branch-HEAD frisch lesen; keine geschützten Zweige überschreiben.
2. `app:testDebugUnitTest --tests 'at.heinz.deviceai.BootstrapBrainTest'` plus vollständige Tests mit realer Android-SDK/JDK-Toolchain ausführen; den neuen Regressionstest wirklich ausführen, XML/Exitcodes und HEAD-SHA protokollieren. Unveränderte alte Testresultate zählen nicht.
3. Danach auf Gerät denselben Besitzerprompt in Continuum testen und prüfen, dass kein `get_storage_info` erscheint und die richtige Kistenantwort gegeben wird. Die Folgekorrektur („rot jetzt 9“) und anschließende Rückfrage („ursprünglich blau 7“) müssen als echte fertige Modellantworten beobachtet werden. Laufen/STOP und `ANTWORTENTWURF` separat von finalem Ergebnis behandeln. Zeigt sich weiterhin fehlender Gesprächskontext, nächste **separate** RED→GREEN-Reparatur im kanonischen `runLocal`/Kontextpfad, nicht durch zusätzliches zweites Gedächtnis.
4. Für eine neue In-place-Version gegenüber installierter Code39 strikt **versionCode >=40**; unverändertes Originalpaket/-zertifikat und Publisher `srv-dbf1tr2vcj2c73ehpfog`. Keine Signierung, Publikation, Installation oder Besitzer-Datenänderung ohne exakte Freigabe und erfolgreiche QA.
5. Besitzer hält beim lokalen Modelltest Handy-Internet AUS und Continuum-Webfreigabe auf „Nicht zulassen“. Internet erst bei ausdrücklich angekündigter Netz-/Update-Prüfung wieder zulassen.

Rollback: Nur die minimalen 2 Dateien in einem neuen Commit revertieren; vorhandene Besitzer-App und Daten niemals deinstallieren.
