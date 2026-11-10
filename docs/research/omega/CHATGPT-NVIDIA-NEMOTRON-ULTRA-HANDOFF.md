# NVIDIA Nemotron 3 Ultra – BYOK für Device AI Continuum

Datum: 10.11.2026
Repository: `Ullmann27/device-ai`
Entwicklungszweig: `chatgpt/nvidia-nemotron3-ultra-byok-20261110`
Draft-PR: https://github.com/Ullmann27/device-ai/pull/714
Basis: Code-52-Kandidat PR #706, SHA `17491f4d8103f0e55836369333fab07e80e6e9c2`

## Quelländerungen

1. `app/src/main/java/at/heinz/deviceai/NvidiaNemotronDirectClient.kt`: fester HTTPS-Endpunkt `https://integrate.api.nvidia.com/v1/chat/completions`, NVIDIA-Modell `nvidia/nemotron-3-ultra-550b-a55b`, begrenzte Einzelanfrage, nicht-streamende Antwort, Medium-Reasoning, gesperrte Weiterleitungen, Timeouts und Antwortgrößenlimit. Ausschließlich der freigegebene Fragetext, keine Toolaufrufe, Erinnerungen oder Gerätedaten.
2. `app/src/main/java/at/heinz/deviceai/SecureTokenStore.kt`: separater NVIDIA-`nvapi-`-Schlüssel mit bestehendem Android-Keystore/AES-GCM sicher speichern, laden, widerrufen. KEIN Schlüssel im Code.
3. `app/src/main/java/at/heinz/deviceai/AgentActivity.kt`: Systemstatus- und Cloud-Einstellungen um `NVIDIA ... API-KEY` und `... TESTFRAGE` ergänzt; Freigabedialog für jeden Aufruf, Ausgabe als externe unbestätigte Antwort, Abschalten des Internets blockiert den Aufruf. Keine automatische Cloud-Umschaltung.
4. `.github/continuum/nvidia-nemotron-byok-contract.test.mjs`: statische Source-Contract-Negativtests.

## So testet der Besitzer sofort das Modell unabhängig von der App

- https://build.nvidia.com/nvidia/nemotron-3-ultra-550b-a55b im Browser öffnen, einloggen, Playground öffnen und eine deutschsprachige Testfrage stellen.
- Das verifiziert nur die Modell-Spielwiese, NICHT den Android-Code oder den API-Schlüssel in Continuum.

## So testet der Besitzer nach einem späteren geprüften originalsignierten Update

1. Nur die aktualisierte originale Continuum-Hauptapp verwenden. Keine zweite App, keine andere Paketkennung.
2. Systemstatus → INTERNET · FREIGABEN → „Auf Nachfrage“; OFF sperrt NVIDIA ausdrücklich.
3. Systemstatus / Cloud-Backend → „NVIDIA NEMOTRON 3 ULTRA · API-KEY“, den **neu ausgestellten** NVIDIA-Schlüssel in der App einfügen und verschlüsselt speichern; niemals in GitHub, in Chat-Nachrichten oder in Buildvariablen hinterlegen.
4. „NVIDIA NEMOTRON 3 ULTRA · TESTFRAGE“ öffnen, die Frage „Antworte genau mit: NEMOTRON VERBUNDEN“ eintragen und „EINMALIG AN NVIDIA SENDEN“ wählen.
5. „NVIDIA · NEMOTRON 3 ULTRA · EXTERN · UNBESTÄTIGT“ mit einer Antwort ist ein positiver Remote-Smoketest. Bei 401/403 Schlüssel erneuern; bei 429 Limit; bei Timeout Netzwerk/NVIDIA-Dienst prüfen.
6. Internet auf AUS: Neue NVIDIA-Anfrage muss ohne HTTP-Aufruf blockieren. Lokale Gemma-Konversation muss weiterhin funktionieren.

## Prüf- und Release-Status

- **Statischer GitHub-Readback:** 13/13 selbst durchgeführte Quell-Vertragsprüfungen auf dem geschriebenen Entwicklungszweig erfolgreich (kein Kotlin-Compile-Nachweis).
- **Node-Testdatei im Repository:** vorhanden, jedoch nicht mit Node-Runner ausgeführt (NOT_RUN).
- **Android-Gradle-Build und Android-Unit-Tests:** NOT_RUN.
- **Live-NVIDIA-API-Test:** NOT_RUN. DNS-Auflösung aus dem ausführenden Container nicht möglich; keine Anfrage an NVIDIA gesendet, Schlüssel wurde weder gespeichert noch verwendet.
- **Fold7-Gerätetest:** NOT_RUN.
- **Code52-Releasekandidat-Integration:** NOT_MERGED. Signierpfad / Besitzerfreigabe unverändert. Kein Main-Merge / keine APK-Veröffentlichung.
- **API-Freikontingent:** kostenfreier Prototype-Endpunkt laut NVIDIA, kein Garantie-/Unbegrenzt-Vertrag, keine automatischen Hintergrundabrufe.

## Verbindliche Sicherheitsgrenzen

- Die im Chat veröffentlichte NVIDIA-API-Key-Zeichenfolge wurde absichtlich **nicht** in einen GitHub-Branch oder einen Build-Artefakt aufgenommen; der Nutzer soll den Schlüssel nach Tests widerrufen und ersetzen.
- Jeder NVIDIA-Aufruf erfolgt nur nach bewusstem Einzeltastendruck; kein Datenabfluss aus dem Dialogverlauf, keine Berechtigungseskalation, keine automatische Übernahme als Gedächtnisfakt.
- Der OFF-Modus wird auch beim Start des Netzwerk-Workers erneut gelesen; es besteht kein garantierter Abbruch einer bereits laufenden Anfrage bei späterer Umschaltung.
- Nach Merge unbedingt relevante Node-/Kotlin-/Gradle-Tests auf **dem exakten neuen SHA** fahren, dann Fold7-Abnahme und ausschließlich bestehende Originalsignierung nach Besitzerfreigabe.
- Ein grüner statischer Vertragstest ist kein Beweis für korrekte NVIDIA-Inferenz oder APK-Installierbarkeit.

## Quellen

NVIDIA offizieller Endpunkt und modellbezogene Request-Parameter:
https://build.nvidia.com/nvidia/nemotron-3-ultra-550b-a55b
https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-ultra-550b-a55b

