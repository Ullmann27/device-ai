# NVIDIA Nemotron 3 Ultra – sichere BYOK-Integration

Datum: 2026-11-10
Eigener Entwicklungszweig: `chatgpt/nvidia-nemotron3-ultra-byok-20261110`
Ausgangsbasis: Code52-Cinema-Releasekandidat `17491f4d8103f0e55836369333fab07e80e6e9c2` / PR #706

## Ziel
Den direkten NVIDIA-Nemotron-3-Ultra-Zugang als ausdrücklich aktivierten Einzelanfrage-Mentor in die bestehende Continuum-Android-Haupt-App integrieren. Kein zweiter Hauptchat, kein Cloud-Fallback, keine Tool- oder Gedächtnisrechte.

## Sicherheitsvertrag
- Niemals NVIDIA-API-Schlüssel im öffentlichen Repository, in Artefakten, in App-Konstanten oder in Logs veröffentlichen.
- API-Key wird auf dem Android-Gerät mittels vorhandener AES-GCM/Android-Keystore-Technik gespeichert und ist austauschbar.
- Einmalige Frage: nur ausdrücklich bestätigter Fragetext an den fest verdrahteten NVIDIA-HTTPS-Endpunkt; kein Chat-Kontext, keine Geräteinfos, keine persönlichen Erinnerungen.
- Keine Veröffentlichung und kein APK-Signing. Originalpaket/Signatur bleiben unverändert.
- Feste Anfragebudgets, Timeouts, Weiterleitungs- und Antwortgrößenbegrenzungen.
- Standardchat bleibt lokal; bei OFF kein automatischer Cloudwechsel.

## Abnahme
Quelltest und Android-Unit-Tests: NOT_RUN.
Echter NVIDIA-API-Test: NOT_RUN – Netzwerkauflösung im Entwicklercontainer gesperrt.
Fold7-Gerätetest: NOT_RUN. 
Integration in Release-Kandidat: NOT_MERGED.
WIP bis Testnachweis. Exakte Commit-Links nach Entwicklung nachtragen.

## Fortsetzung
NVIDIA-Client, Schlüsselspeicher, Besitzer-Dialoge und Negativtests auf diesem Branch entwickeln, Quellstand einchecken, Testbelege prüfen und PR #706 nicht ohne gesonderte QA-Freigabe ändern.
