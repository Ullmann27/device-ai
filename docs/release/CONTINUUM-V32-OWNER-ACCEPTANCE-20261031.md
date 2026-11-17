# Continuum Lokal v0.3.32 – Besitzer-Abnahme 31.10.2026

**Release-Identität:** `at.heinz.deviceai.continuumlocal` · `versionCode=33` · `versionName=0.3.32` · **Originalzertifikat zwingend** `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.

## Verbindliche Ziele des Besitzers (heutige drei Chats)

1. **Echte installierbare APK:** Vollständiges integriertes Android-Artefakt, nicht eine alte v29/v30-Teilversion, nicht nur Unsigned-QA. Originalsignatur, Paketname, höherer Code, SHA-256, HTTPS-Manifest und funktionierender Update-Knopf nachprüfen.
2. **Gesamtes öffentliche Web statt Wikipedia-Limitierung:** Freigegebene direkte Recherche sucht mit `GeneralWebDiscoveryProvider` bei Bing-RSS und DuckDuckGo-HTML. Nicht-Wikipedia-Seiten werden mit dem beschränkten, DNS/TLS-geprüften `HttpReadOnlyResearchProvider` gelesen. Fehler von Suchanbietern müssen sichtbar bleiben; keine erfundenen Quellen.
3. **Recherchieren und Quellenzähler:** Die Hintergrundforschung `ContinuumPublicStudyJobService` nutzt `ContinuumOpenWebStudyFetch`. Sie erfordert explizit `ALWAYS_ON` und separate Aktivierung, Android JobScheduler kann verzögern. Die zuvor gemeldeten ~550 Quellen sind nur ein Besitzerbefund, **kein nach diesem Release gemessener Fortschritt**. Quellindex ohne Dubletten, Datum, URL und Hash beibehalten; nach Geräteinstallation neue Quellen und job reasons messen.
4. **Gehirn/Gedächtnis/Experimente:** EWM-v2, SWB1, CRE1, HybridMemoryRetriever, beleggestützte Forschung und JARVIS-/M8-Kern bewahren. Forschungs-Shadow-Module nicht ohne Benchmarks als produktiv behaupten. Kein heimliches Retraining, keine automatische Erhebung personenbezogener Quellen.
5. **Coding-Agent und Autonomie:** Schutz für Akku, Temperatur, Speicher, Prüfer, Berechtigungen und den bestehenden Teamleiter beibehalten. Kein automatischer Geräte-Shell-Zugriff aus unbestätigtem Modelltext.
6. **OpenRouter:** Separater Modellanbieter; Benutzer kann nach APK-Installation in **Systemcenter → OpenRouter · API-Key eingeben** einen **neuen** API-Key eingeben, ändern oder lokal löschen. Dieser wird im Android Keystore verschlüsselt gespeichert. Unter **Modellverwaltung → OpenRouter · Einzelfrage** nur explizite, einzeln freigegebene Frage; lokale Erinnerungen/Device-Infos werden nicht gesendet. Max. 5 Versuche/UTC-Tag lokal; zusätzlich OpenRouter-Kontolimit setzen. Der im Chat offengelegte ältere Schlüssel muss widerrufen sein.
7. **Bedienung und Qualität:** In-App-Updater liefert ausschließlich vom gepinnten Besitzerhost `https://continuum-owner-v23-release-preflight.onrender.com` und prüft Manifest-Version, Hash, Originalzertifikat. Fold7-Installation und Antwortqualität benötigen echte Gerätetests, auch wenn Host-Tests erfolgreich sind.
8. **Buildweg:** GitHub Actions gilt hier als unzuverlässig. Node-/Android-/Gradle-Abnahme über Render; Signieren ausschließlich auf dem bereits vorhandenen Render-Owner-Release-Host, niemals auf einem neuen Testhost. QA-Berichte sind kein Downloadversprechen.

## Checkpoints / Ehrlichkeitsstatus

- **Quellstand integriert:** OpenRouter Android-BYOK, vorhandene v32-Web-/Autonomie-Verbesserungen, bestehender Updater und Release-Gate.
- **Weiterhin einzeln zu belegen:** Render-QA für den *exakten Release-SHA*, vollständige Gradle-/Node-Tests, Original-P12-signierter APK-Build, Verfügbarkeit unter dem gepinnten Host, echte `latest.json`-/APK-Readbacks, direkte Fold7-Installation und dauerhaft wachsender Quellenindex.
- **Keine stillschweigende Änderung von `main`**; dieser Integrationszweig wird vor Veröffentlichung gegen den gepinnten Release-Zweig geprüft.
