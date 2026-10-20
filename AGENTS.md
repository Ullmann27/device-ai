# Arbeitsanweisungen für Device AI Continuum (alle Agenten)
**Vor jeder Codemodifikation** `docs/CONTINUUM-EXPERIMENTVERTRAG.md` und `docs/CONTINUUM-EXPERIMENTE.json` sowie [Issue #41](https://github.com/Ullmann27/device-ai/issues/41) lesen. Geltungsbereich nur dieses Projekt, keine Vermischung mit Lumo. Bewahre die Experimente für künftige Entwicklungsrunden.

## Nicht verhandelbares Produktziel
**Mobile-first:** Der Coding-Agent muss **direkt auf Android** programmieren, testen, eine APK bauen und nach Eigentümerbestätigung installieren können. Ein PC/Mac ist lediglich ein späterer optionaler Worker. Baue keine PC-Pflicht ein und verwechsle eine Android-Oberfläche nicht mit nachgewiesenem on-device Build.

## Regelwerk
1. Reale Ist-Schnittstellen lesen, betroffenes Experiment aus dem Register bestimmen und zukünftige Adapterfähigkeit erhalten.
2. Bevorzuge eine gemeinsame Backlog-/Repair-/Evidenz-Pipeline statt paralleler konkurrierender Lösungen; UI-Transport, native Toolchain, optionale externe Worker und KI-Kandidaten getrennt kapseln.
3. Nur erlaubte feste Toolchain-Aufgaben ausführen, keine aus Modelltext übernommene Shell. Keine ungesicherten Fremdskripte/Android-Tools aus dem Netz starten.
4. Experimentelle Änderungen isoliert, abschaltbar und durch Negative-/Holdouttests belegbar halten. Nicht verdeckt in bestätigtes Gedächtnis oder produktive Codegenerierung befördern.
5. Git-HEAD, Toolversion, APK-Hash, Tests und Gerätetests getrennt berichten. Fehlende native SDK-Werkzeuge als `UNAVAILABLE` ausgeben; keinen Erfolg erfinden.
6. Kein unkontrollierter Merge nach main, keine Installation ohne Besitzerzustimmung, keine Secrets in Repos oder APKs. Erlaubte Systemgrenzen wahren.
7. Zu jedem implementierten Schritt: Experiment-ID, Kompatibilitätsbeurteilung, Tests, nächster Schritt, Rollback protokollieren.
