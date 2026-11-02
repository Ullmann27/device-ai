# Continuum – Android-Dialog, native Appwahl und lokale JARVIS-artige Stimme

Forschungszweig: `research/continuum-autonomy-dialog-voice-20261102`, isoliert vom Fusion-IV-PR #345.
Gepinnte Basis: `cb700e03e4d6ed2ab5e8cd4283692e44280d9d62`.

## Arbeitsverträge

1. Native, **rein lesende** Suche nach installierten Apps aus gesprochener/geschriebener Bezeichnung; unklare Trefferauswahl darf nie autonom starten. Bestehender `launch_app`-ToolGate/Owner-Confirm und Accessibility-Vordergrundnachweis bleiben obligatorisch.
2. Offline-Sprachwiedergabe mit expliziter Besitzeraktivierung, begrenzter JARVIS-artiger Tonhöhen-/Tempo-Anpassung auf vorhandener Android-TTS-Stimme. **Keine Imitation** einer konkreten Darstellerstimme, keine Cloud-TTS und keine mitgelieferten Modellgewichte. Gesprochene Antworten aus dem bereits vorhandenen Chatpfad beibehalten.
3. Gesprächssicherheit/F08: Fehlschläge und Korrekturen nicht durch vermeintliche Erinnerung oder erfolgreiche Modellantwort kaschieren; keine fremden Appdaten als Besitzerfakt speichern.

Keine neue App, kein alternatives Gehirn, kein produktiver Modell-/Besitzerdatenschreibzugriff, kein Main-Merge, kein Signing, kein Fold7-Claim.

Status beim Öffnen: Nur Basiskopie, neuer Code **NOT_IMPLEMENTED / NOT_TESTED**. Etappen, exakte Commits, Tests und bekannte Einschränkungen nach tatsächlicher Ausführung hier ergänzen.
