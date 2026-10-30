# VIDEO C: „Über 1.000 KI-Modelle kostenlos?“ – Quellen- und Wirtschaftlichkeitsaudit
Stand: 2026-10-30, zusätzlich zur Original-JARVIS-A/Videoprüfung.

**WICHTIG – Identität:** Das markierte Video wurde als Bildschirmbild/verbale Beschreibung angeführt, jedoch liegt bislang keine verifizierte Video-URL oder eindeutiger Titel als auslesbare Datei vor. Daher ist keine Gleichsetzung mit einem konkreten YouTube-/TikTok-Angebot belegt. Die nachfolgenden Kandidaten sind **Recherchetreffer, keine Identifikation des Videos**. Ein vollständiges videogenaues Transkript wäre unredlich.

## Online-Quellen gegen die Werbebehauptung

| Portal/Kandidat | Belastbarer Ist-Befund per Anbieterdokumentation | Tatsächlicher Haken | Eignung Continuum |
|---|---|---|---|
| OpenRouter | Laut https://openrouter.ai/pricing/ 500+ **Katalogmodelle**, aber im kostenlosen Plan **25+ Free-Modelle**, 50 Anfragen/Tag; laut https://openrouter.ai/collections/free-models/ 20/Minute und bis zu 1.000/Tag **nach** $10 Kreditkauf | Kataloggröße ≠ kostenloses Kontingent, Dienst braucht Internet, Konto/Key, fremde Provider. Keine unlimitierte lokale KI. | Optionaler **vom Besitzer ausdrücklich freigegebener** Vergleichsprovider, nicht autonomer Produktionskern |
| Puter.js | https://developer.puter.com/tutorials/free-unlimited-ai-api/ beschreibt 400+ KI-Modelle, keine Entwickler-API-Keys; https://docs.puter.com/ nennt sogar 500+. | Explizites **User-Pays**: Endbenutzer übernimmt seine eigenen Modell-/Cloud-Kosten; die „kostenlos für Entwickler“-Formulierung bedeutet **nicht** unbegrenzt gratis für Besitzer. Cloud-Abhängigkeit, Nutzer-Login, Netzwerk. | Optional für isolierte Web-Demos, **keine** Android-Haupt-App-Abhängigkeit oder autonomer Signierer |
| freellmapi.co | https://freellmapi.co/de/ wirbt für **969 Modell-Einträge** und zentralen kostenlosen Router. | Aktuell **19 USD pro Jahr** für den Live-Katalog; kostenlose Tokenkapazitäten liegen bei Drittherstellern und sind ausdrücklich kontingentiert/schwankend. Ist keine Garantie, dass alle 969 Modelle ständig gratis laufen. | Kein Blind-Import, keine Zahlungs-/Tokenfreigabe |
| WaveSpeed AI | https://wavespeed.ai/landing/introduce: **1000+** Bild-/Video-/Audio-/3D-Modelle | Der Anbieter bezeichnet seine API als **pay per generation**, nicht als 1.000 gratis unbegrenzt nutzbare Modelle. | Nur optionale Multimedia-Forschung |
| AI/ML API | https://saaspartout.com/de/marketplace/aimlapi/ beschreibt 1000+ Katalogmodelle | Pay-as-you-go, begrenzte Teststufe. Kein Autarkiegewinn durch bloßes API-Proxying. | Kein Import ohne belegten Free-Lane-Limit-/Privacy-Audit |
| Hugging Face Model Hub | https://huggingface.co/docs/hub/models-downloading: frei erhältliche Open-Weight-Modelle können heruntergeladen und **lokal** ausgeführt werden. | Einzel-Lizenzen, Modellgewichte und Hardwarelimits; gehostete Inferenz nicht generell kostenlos: https://huggingface.co/docs/inference-providers/pricing nennt $0.10/Monat Testguthaben für kostenlose Konten. | **Für Continuum sinnvollster lokaler Weg**: kleines, kompatibles Qwen-/Gemma-Modell gezielt auswählen und gegen Fold7-Baseline messen, nicht 1.000 herunterladen |

## Vorläufiges Urteil (keine unbelegte Betrugsvorwürfe)
**Werbeaussage „über 1.000 Modelle gelistet“ kann wahr sein. Aussage „über 1.000 starke KI-Modelle dauerhaft ohne Beschränkung kostenlos nutzbar, offline und unbegrenzt“ ist durch diese Quellen NICHT belegt.** Aus dieser Differenz allein lässt sich weder strafrechtlicher Betrug noch eine Täuschungsabsicht nachweisen.

**Continuum-Strategie:**
1. Immer lokal zuerst: vorhandene LiteRT-/Gemma-/Qwen-Pipeline, keine externe KI-API als Pflicht für M6/M8 oder das Installations-/Signiergate.
2. Optional `ResearchProvider` im **nicht produktiven Shadow-Audit**, mit expliziter Zustimmung, Modell-ID, Lizenz-, Privacy-, Kostenlimit, token-/request-Grenze, Herkunft und reproduzierbarer 30-Task-Holdout-Messung. Bei Unklarheiten `BLOCKED/UNKNOWN`.
3. Online-Modelle nur als Vergleichskandidaten; niemals private Speicher-, Signier- oder GitHub-Zugangsdaten übertragen. Kein unbestätigter Cloud-Dienst erhält CCC1-Schreibbefugnis.
4. Vor Import: exakten Videoanbieter (Original-URL, Screenshottext), Tariftext und Modellendpunkt verifizieren, Code-Lizenz/Abhängigkeiten prüfen, dann in separatem Draft-PR ein **austauschbares Interface**, nicht den Cloud-Dienst als Gehirn einbauen.
5. Erfolgs-Gate: bessere *unabhängige* Benchmarkwerte bei gleichen Testfällen/Tokenbudget, gemessene Kosten/RAM/Netz/Privacy, Rückfall auf offline.

**Laufstatus:** Videolink noch nicht verifiziert; keine API-Token angelegt, keine fremden Pakete heruntergeladen, keine fremde Cloud in die Android-App eingebaut, keine Laufzeitbenchmark behauptet.
