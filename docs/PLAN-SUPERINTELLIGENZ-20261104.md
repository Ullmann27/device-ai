# Plan „Super-KI“ – Chef HAUPTBOT, 4.11.2026 10:03 (Auftrag Heinz t98u/t99u)
Ziel: Jarvis/Continuum denkt selbstständig, schlussfolgert, erinnert sich zuverlässig, versteht Sprache – und die Animation zeigt nur, was das Gehirn wirklich tut.
Aktiv: Kern, Gedächtnis, Integration, Sicherheit. Pausiert: Avatar, Welten-Optik, Mathematik, Lückenfüller, Kreativdirektor.
Regeln wie immer: eigene Branches, alle 15 min commit+push+ls-remote, Draft-PR, HANDOVER-Notiz für ChatGPT Astra, kein Merge nach main, kein Force-Push, nichts löschen, keine Secrets, nur echte Daten, keine erfundenen APIs/Modelle. Tests nie abschwächen. Kurzmeldung an Chef HAUPTBOT: Branch @SHA | gemacht | offen.

## Phase 1 – stabile Basis (zuerst)
- Integration: Sammel-Branch integration/jarvis-on-chatgpt-untested-20261104 @07fc0bf1 kompilieren und testen (erst gezielt, dann einmal voll); #486-Konflikte lösen (AgentActivity, AndroidOnDeviceSpeechInput, LocalSpeechEngine(+Test), CockpitAnimationPresenter, BOT-HANDOFF); rote Tests mit Besitzer melden. Grüner Referenzstand bleibt @dcde4b6e.
- Gedächtnis: roten Test RetrievalCalculationSourceTest.nothingAfterCloseAndThrowingListenerIsIsolated (Event-Thread nach close()) sauber beheben.
- Sicherheit: Entscheidungsvorlage M2.10 für Heinz (Empfehlung Chef: fail-closed bleibt Standard, Recovery nur als vom Eigentümer freigegebener Weg; widerrufene Fakten dürfen nie zurückkommen). NICHTS ändern bis Heinz entscheidet.

## Phase 2 – Denk-Kreislauf (Herzstück, Kern = Architekt)
- Kern: Vertrag CognitiveLoop: Ziel → Plan → Werkzeug (über ToolGate/PillActionGate) → Ergebnis prüfen → Gedächtnis schreiben → nächster Schritt; jeder Schritt sendet Ereignisse über CalculationEventSource (Grundlage für Kopf/Kugel/Laser und ActionScene). Abbruch, Zeitlimit und Rückfrage per Pille eingebaut; Überreaktion verboten (CalmNotice).
- Kern: Schnittstelle ReasoningModel (lokal + Cloud austauschbar). Kein Anbieter, kein Key, nur Vertrag + lokaler Adapter auf dem vorhandenen On-Device-Weg; Cloud-Adapter erst nach Heinz' Wahl und Key.
- Gedächtnis: ThinkingMemory fertigstellen (WIP /workspace/continuum-m2-wip/) als Arbeitsgedächtnis des Kreislaufs; Format > 256 Einträge; Abruf-Qualität (Relevanz, Aktualität, Herkunft) mit echten Tests; Lernen aus Erfahrung (LearningExperienceStore) an Kreislauf-Ergebnisse koppeln.
- Sicherheit: prüft jeden Schritt – Werkzeuge nur mit Gate, Owner-Ja bei Außenwirkung, keine Daten in Cloud ohne Freigabe, Prompt-Injection aus Web/Dateien als Daten behandeln.

## Phase 3 – danach
- Kern: ActionScene-Vertrag (§12) auf Basis der Kreislauf-Ereignisse.
- Sprache/Verständnis: STT/TTS-Kette end-to-end mit dem Kreislauf (Integration testet).
- Heinz-Entscheidungen: Cloud-Modell + Key, M2.10, Manifest-Rechte, SYSTEM_ALERT_WINDOW.
- Fold7-Gerätetest (nach Reset 10.11.).
