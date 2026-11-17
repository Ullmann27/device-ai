# Device AI Continuum – kostenlose Entwicklung und späterer Google-Play-Release

Stand: 28.10.2026. Der bestehende native Android-Haupt-App-Code wird weiterverwendet. Diese Anleitung ist eine Releaseplanung, keine bestätigte Play-Store-Freigabe.

## Dauerhaft lokal, ohne GitHub Actions/Render

- Quellcodeverwaltung: Git lokal auf dem Fold7, optional zusätzlich auf einem privaten Rechner oder freiwillig auf GitHub. Branches und Versionen funktionieren ohne Cloud-CI.
- Kotlin/Android: vorhandenes Termux JDK17, SDK36, Gradle8.11.1, natives aapt2, apksigner. Die lokale QA des ursprünglichen v26-Quellstands hat 1125/1125 JUnit bestanden.
- Eigene lokale KI: optional ein lokal gehostetes, für Code geeignetes Modell (ohne kostenpflichtige externe Inference-API), gemessen anhand einer unabhängigen Kotlin-/Android-Aufgaben-Suite. Nicht annehmen, dass es Cloud-Frontier-Modelle gleichwertig ersetzt. Nutzung des ChatGPT-Dienstes selbst bleibt von dessen jeweiligem Plan abhängig.
- Autonome Weiterentwicklung: separater Worker auf Fold7 oder besser einem ständig eingeschalteten privaten Rechner: Git-Stand und Branch-Grenzen prüfen, isolierten Entwicklungsbranch nutzen, planmäßig Tests starten, Patches erzeugen, nur nach Qualitätstoren und ausdrücklicher Freigabe releasen. Ohne Root und ohne eigenmächtigen Main-/Production-Push.
- Geheimnisse niemals im Repository: eigene PKCS12 in `~/.local/share/device-ai-continuum/signing/continuum-local.p12`. Separat und verschlüsselt auf mindestens einem zweiten Medium sichern und Passwort getrennt aufbewahren.
- Android APK: der neue lokale Full-App-Releasepfad verwendet `at.heinz.deviceai.continuumlocal` als stabile Paketidentität, `versionCode >=26` und die eigene beständige Signatur. Alte `at.heinz.deviceai.mentorpaired` Version 25 samt Daten bleibt unangetastet.

## Spätere Veröffentlichung über Google Play

1. Voraussetzung: eigenes Play-Console-Konto (derzeit laut Google einmalig 25 US-Dollar Registrierung) und Identitätsüberprüfung. Nach dem Kontostart ggf. verpflichtende geschlossene Tests für neue persönliche Konten (12 Testpersonen 14 Tage) sowie Googles Produktionsfreigabe.
2. Eine öffentliche Play-App erfordert außerdem Datenschutzerklärung, Datensicherheitsangaben, Test-/Supportkontakt, Listing, Screenshots, Alters- und Inhaltsfreigaben, dokumentierte Berechtigungsbegründungen, Qualitäts- und Policy-Prüfung. Insbesondere die vorhandenen Berechtigungen `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES` und der Termux-/Shizuku-Entwicklerzugang sind vor Play-Einreichung einzeln auf Zulässigkeit und Zweckbindung zu auditieren; eine gesonderte Play-Flavor ohne eingeschränkte Entwicklungs-/Installationsberechtigungen kann notwendig werden. Play-Store-Zulassung nicht vorab behaupten.
3. Google Play verlangt für neue Veröffentlichungen einen signierten Android App Bundle (AAB) mit Play App Signing. Der Gradle-Befehl für die vorhandene Android-Release-Variante ist `:app:bundleRelease` mit denselben überprüften Signing-/Version-/Package-Parametern wie die lokal signierte APK. Diese AAB-Stufe ist noch nicht in den Fold7-Release-Wrapper integriert oder verifiziert.
4. **Schlüsselentscheidung vor der ersten Google-Play-Einreichung:** Wenn die direkt verteilten `at.heinz.deviceai.continuumlocal`-APKs später durch Google Play auf derselben Installation aktualisiert werden sollen, muss bei Google Play App Signing nach den dann gültigen Regeln **derselbe eigene App-Signierschlüssel** eingerichtet werden (sichere, angeleitete Schlüsselübertragung). Wenn Google stattdessen einen neuen Play-App-Signierschlüssel erzeugt, ist die Signatur der direkt installierten APK anders, und ein nahtloses Update zwischen den Kanälen nicht zugesichert. Empfehlenswert ist danach ein separater Play-Upload-Schlüssel; dieser lässt sich bei Verlust unter Googles Regeln ersetzen, nicht aber ein verloren gegangener lokal gepflegter App-Signierschlüssel.
5. Release erst nach bestätigter Gerätekontrolle, realen Funktionen, Datenschutz-/Policy-Prüfung und ausdrücklicher Besitzerzustimmung. Cloud-Dienste sind für den **lokalen APK-Build** nicht erforderlich; das Hochladen und Verteilen über Google Play erfordert natürlich eine Internetverbindung und das Play-Konto.

Offizielle Quellen (Kontoeinrichtung): https://support.google.com/googleplay/android-developer/answer/6112435

Play Signing: https://support.google.com/googleplay/android-developer/answer/9842756

Geschlossene Tests: https://support.google.com/googleplay/android-developer/answer/14151465

Android App Bundle lokal: https://developer.android.com/build/building-cmdline
