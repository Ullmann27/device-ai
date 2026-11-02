# Sovereign Brain IV — gesicherter Abschlussstand

Der Besitzer hat die laufende Arbeit ausdrücklich zum sofortigen sauberen Abschluss angewiesen: „Bitte jetzt alles sauber abschließen … alles was du gemacht hast, committen und pushen … Übergabeprotokoll und … Abschlussbericht.“ Deshalb endet diese Runde mit dem geprüften Audit-/Forschungscheckpoint. **S4–S6 und die neuen Produktfunktionen A–C wurden noch nicht implementiert.** Frühere S1–S3 bleiben als geerbter Quellstand erhalten; ihre vollständige Erledigung ist durch eigene grüne Tests nicht erneut bestätigt.

Basis: PR357 `d49031843b03af6ce21427a41e761d671aa05b58`. Isolierter offizieller Zweig `research/continuum-sovereign-brain-iv-20261102`, [Draft-PR358](https://github.com/Ullmann27/device-ai/pull/358). Erster Checkpoint `87f12e373eb5bbda40a5c3827b0de16f03ad40ae`. Die geprüften Parallel-HEADs und die drei offenen Gitar-Threads stehen unverändert in `docs/evidence/sovereign-brain-iv-20261102/github-start.json`.

## Tatsächlich abgeschlossen

- Aktuelle HEADs von357/354/356/main, AGENTS, Abschluss und Originalbelege gelesen; aabd8ea7/babba332 sind im endgültigen357-Code vorhanden. Eigene isolierte Folgebranch/Draft eröffnet, keine konkurrierende Produktarchitektur angelegt.
- Frische unabhängige Prüfung des exakten357-Endstands angefordert und neue Gegenbeispiele erhalten. Die ursprünglichen Review-Threads bleiben offen; es wurde keine fremde „Changes requested“-Bewertung als erledigt markiert.
- Den eingefrorenen Holdout unverändert nachgerechnet: SHA256 `c29f57dca626901ca65cbba36027b22897794cc73d4e6a35e252da5dd442e3cf`. Neuer reproduzierbarer Bericht unterscheidet Zeilen und eindeutige Aufgaben.
- Unabhängigen Host-Reproducer erneut ausgeführt und mit Originalquellen/Prüfkommandos gesichert. Es wurden weder Modelle trainiert noch Geräteaktionen ausgeführt.

| Vorhandene synthetische Auswertung | 72 Zeilen | 54 eindeutige Fälle |
|---|---:|---:|
| Alter Compiler korrekt |48|36|
| Vollständige Evidenz korrekt |72|54|
| Verbesserungen |24|18|
| Regressionen |0|0|
| Unverändert |48|36|
| Korrekte negative Enthaltungen danach |24|18|

Dies ist eine **HOST_VERIFIED Neuauswertung vorhandener RESEARCH_SHADOW-Daten**, keine neue Modellinferenz, kein neuer unabhängiger Holdout und keine Reparatur des CDX1-Gerätebefunds0/20. Die Duplikate wurden nicht gelöscht oder umgeschrieben. Vorhersagen/Erwartungen und gleiche Ergebnisse innerhalb jeder Duplikatgruppe wurden kontrolliert.

## Offene bestätigte Befunde

1. **Prompt-Budgets:** Activity schreibt den Auftrag roh und nochmals im Compiler-Kontext. Die letzten Guards verhindern Kürzung, beseitigen aber nicht den widersprüchlichen Budgetbedarf. Lange Widerspruchsgruppen können weiterhin den Pflichtkontext sprengen. Kein Fix in dieser Runde.
2. **Start-Race:** Der357-Endstand prüft Startberechtigung und Zeit nicht atomar. In einer kontrollierten Host-Scheduling-Probe kann nach Rückgabe von TIMED_OUT bzw. CANCELLED ein Backend-Aufruf beginnen. Das erneute Prüfen nach dem Callback in aabd8ea7 reicht für diese zusätzliche Rennbedingung nicht. Mock-Backend, kein physisches Gerät. Beide Fälle unabhängig gefunden und vom Hauptagenten reproduziert.
3. **Ergebnisgröße:** Ein zulässiges8000-Zeichen-Tool-Ergebnis wird in ReadOnlyToolPlan um Beschriftung ergänzt; AgentModelOutput begrenzt ebenfalls auf8000. Ergebnis: IllegalArgumentException, kein AgentRunResult/TaskEvidence, keine Erfahrung. Dieser ältere Defekt stammt bereits aus der Basis vor357 und ist keine neu eingeführte Brain-IV-Regression.
4. **Übergabe/Freigabe:** Originalbelege bleiben historische Nachweise. Die aktuelle Freigabe stammt aus den expliziten Besitzeranweisungen dieses Chats; ein GitHub-Besitzerfreigabe-Kommentar dazu existiert nicht und wird nicht erfunden. Präzise aktuelle Kommandos und konkrete Einstiegssymbole sind in der neuen Übergabe enthalten. Kein Release-/Gewichts-/Gerätefreigabenachweis.

Die unabhängige Prüfung rechtfertigt **weitere Änderungen erforderlich**, keine Freigabe zum Merge/Release. Die neuen Reproducer zeigen offene Fehler; sie sind keine grüne Abnahme des Gehirns.

## Status und Fortsetzung

| Bereich | Stand dieser Runde |
|---|---|
| Quellen-/Review-Audit und72/54-Neuauswertung |HOST_VERIFIED, Rohbelege gesichert|
| Neue Produktintegration / SOURCE_INTEGRATED |Keine neue Produktfunktion implementiert|
| A: robuste gemeinsame Prompt-Zusammenstellung |NOT_RUN / NOT_IMPLEMENTED|
| B/S4: modellneutraler Vertrag und zwei austauschbare Adaptertests |NOT_RUN / NOT_IMPLEMENTED|
| C/S6: neuer realer mehrstufiger Host-Auftrag |NOT_RUN / NOT_IMPLEMENTED|
| S5: neue CDX Teacher-Forcing-/Generierungs-/EOS-/Kapazitätsexperimente |NOT_RUN|
| Neue vollständige Kotlin-/Node-Suite |NOT_RUN; keine Laufzeitquellen geändert. Geerbte1648/1938-PASS sind keine neue Abnahme.|
| DEVICE_VERIFIED / Fold7 / echter Modellwechsel |NOT_RUN|

Nächster Code-Schritt: Prompt-Zusammenstellung aus AgentActivity.runLocal als gemeinsam aufrufbare Kotlin-Funktion faktorisieren, auf vollständige lange DE/CJK/Emoji-Eingaben und Widersprüche RED testen, Auftrag nur einmal rendern und Byte-/Zeichenbudget gemeinsam berechnen. Parallel dazu ist vor weiterer autonomer Ausführung ein überprüfbarer Start-/Abbruch-/UNKNOWN-Vertrag nötig: bereits begonnene Nebenwirkungen dürfen nicht als rückgängig dargestellt werden. Danach vorhandenes LocalModelRuntime als Motorgrenze verwenden, nicht neu verwalten; ReadOnlyToolPlan-Ergebnisgrößen sauber behandeln. NeueS4/S5/S6-Holdouts erst nach kontrollierter Planung einfrieren.

Keine Änderungen an Android-Klick-Dateien aus354, GGUF-/Termux-Dateien aus356, main, Signierung, APK, Besitzerwissen oder produktiven Modellgewichten. Keine automatische Rechteerweiterung. Rohlogs und die verbleibenden Fehler sind für den nächsten Agenten im Repository verfügbar.
