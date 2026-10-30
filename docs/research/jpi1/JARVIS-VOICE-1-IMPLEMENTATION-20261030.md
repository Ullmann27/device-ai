# JARVIS VOICE-1 · Implementierungs-Checkpoint

Basis: PR #247, Commit f3a5e3081428d223fc346174cd81493e8e03929a.
Experiment: JARVIS-VOICE-1 / EXP-12 (vorläufiger Sprach-Dialog ohne Besitzer- oder Toolrechte).

Problem der bestehenden App: Android-Spracherkennung schreibt beim Standardmikrofon den Text nur in den Composer. Es gibt keinen kontrollierten, sprachbasierten Gesprächsmodus. Bereits vorhanden sind Android TTS, die Standard-Spracheingabe, LocalConversationContext und die bestehende kognitive Antwortpipeline. Deshalb wird kein weiteres Gedächtnis und kein zweiter Agenten-/ToolGate-Kern angelegt.

Beabsichtigter kleiner Vertical Slice: Der Besitzer aktiviert ausdrücklich einen begrenzten Dialogmodus. Jede Äußerung stammt aus einer bewusst gestarteten System-Spracherkennungsrunde. Ungefährliche Gesprächsfragen werden ausschließlich in die bestehende, deterministische Continuum-Dialog-/Memory-Pipeline weitergeleitet und anschließend ggf. über das vorhandene TTS gesprochen. Unklare und potentielle Tool-/Gedächtnis-/Geräte-/Sicherheitsaufträge bleiben als Textentwurf zur manuellen Freigabe stehen. Keine behauptete lokale Offline-ASR: EXTRA_PREFER_OFFLINE ist lediglich eine Android-Dienstpräferenz. Keine Voiceprint-Identität, kein automatisches Home-Actuation, keine Hintergrundmikrofon-Aufzeichnung und keine Auto-Erweiterung des Android Auto-Vertrags.

Diese erste Datei ist nur ein gepushter Start-Checkpoint, kein Fertigstellungs- oder Testnachweis.
