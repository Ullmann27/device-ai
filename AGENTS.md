# Arbeitsanweisungen für Device AI Continuum (alle Agenten)

## Einstiegspunkt der aktuellen Integrations- und Aufräumrunde – 23.10.2026
**Vor Entscheidungen über alte/neue Branches zuerst [Aktueller Integrationsstand](docs/CONTINUUM-ACTIVE-CHECKPOINT-20261023.md) lesen.** Dieser Index beschreibt den aufgeräumten *Review*-Branch `integration/continuum-current-review-20261023`, der PR #95 und die geprüften UI-Dateien aus PR #96 enthält. Das ist **nicht** `main`, keine produktive APK und keine permanente SHA-Festschreibung: `git rev-parse HEAD` und PR-Abstände bei jeder Sitzung erneut prüfen.

**Übergabe- und Zweigregel:** Bestehende ältere Zweige, Artefakte, Experimente und Belege vorerst **nicht löschen, schließen oder automatisch mergen**. Ihre Abstammung, fehlende Dateipfade, Quellidentität und Ersatzfähigkeit zuerst vergleichen. #72 (I2-Risikozertifizierung) und #90 (UI-QA) sind bewusst erhaltene, noch nicht vollständig übernommene Forschung/Testkandidaten. #70 (QF2+ Quellenanker) ist in den verglichenen Kern-Dateien bereits byte-identisch. CCC1 gehört Opus; UI/Fold-Abnahme gehört Astra; keine parallelen Worker- oder Receipt-Systeme erfinden. Für neue Codeänderungen zuerst einen klaren kleinsten Prüfgegenstand definieren und danach isoliert testen.

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

## Pflicht bei Nutzungslimits und unterbrochenen Astra-/Sol-Sitzungen
Lies bei einer Fortsetzung nach Sitzungsende zusätzlich `docs/CONTINUUM-ASTRA-RESUME-UND-LIMITPROTOKOLL-20261021.md` und den **tatsächlichen** aktuellen Git-HEAD. Vor der nächsten größeren Arbeit die zuletzt noch offenen Fehler, echten Tests und nach dem letzten verifizierten APK eingespielten, ungetesteten Änderungen abgleichen. Derzeit Priorität: im Samsung-Trainings-Monitor belegter öffentlicher Forschungsfehler `FAILED_IllegalStateException` vom 21.10.2026, 08:21 Uhr; genaue Ursache noch ungeklärt. Stand 23.10.: der Fall „fehlender Auszug“ ist als `SOURCE_UNAVAILABLE` behoben; für `FAILED_SAVE_CYCLE_IllegalStateException` (Issue #76) ist ein Mechanismus am Android-Framework nachgewiesen und abgefangen (Interrupt-Flag lässt `commit()` `false` melden), übrige Ursachen tragen jetzt feste Codes (`STORAGE_FULL`, `NOT_WRITABLE`, `COMMIT_FALSE`). Gerätebestätigung steht aus.

Für ChatGPT-/Work-/Codex-Sitzungen nach jedem abgeschlossenen kleinen Teil oder spätestens alle 15 Minuten reproduzierbaren Stand committen und ein kompaktes Übergabeprotokoll mit SHA, echten Tests, Blockern und nächstem Schritt erhalten. Nur nach tatsächlich vom Besitzer mitgeteilter Nutzungslage konservativ planen: unter ca. 30 % keine neuen großen Aufgabenstränge, bei ca. 15 % Handoff statt Featurearbeit, bei ca. 5 % keine riskanten Aktionen. **Weder Repository noch App besitzen einen verifizierten Zugriff auf die ChatGPT-Nutzungsquote; keine fiktiven Werte oder automatische Quotenrücksetzung behaupten.** Externe Builds/Worker haben unabhängige Lebenszyklen, gelten aber erst mit geprüften Ergebnissen als erfolgreich. Diese Regeln überschreiben keine Besitzerfreigabe und erlauben nie main-Automerge oder ungeprüfte APK-Installation.
