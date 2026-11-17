# Continuum: verifizierte lokale Sprachmodellwege (Recherche 24.10.2026)

Diese Übersicht betrifft ausschließlich `Ullmann27/device-ai`. Sie begründet **keinen** automatischen Bibliotheksimport in den Owner-APK-Kandidaten. CTX3 bleibt OFF/SHADOW, private Lerngewichte und Release-Signatur werden nicht verändert.

## A. Google LiteRT-LM
Offizielle Dokumentation:
- https://developers.google.com/edge/litert
- https://developers.google.cn/edge/litert-lm/android

LiteRT-LM dokumentiert lokale Android-/Kotlin-Inferenz, Streaming, GPU/NPU-Unterstützung und verwaltete Konversationen. Ein Android-Abhängigkeitsweg wird offiziell beschrieben; vor einem Upgrade sind **fest gepinnte Maven-Version, ABI, tatsächliches Fold7-Modellformat, Prozess-/Speicherbedarf und ausführbare Offline-Tests** notwendig. `latest.release` ist für eine reproduzierbare signierte APK nicht geeignet.

## B. llama.cpp für GGUF und Android
Offizielle Projektanleitung:
- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

Das Projekt dokumentiert einen Android-Studio-GUI-Weg, Termux-CLI und NDK-Cross-Kompilierung. Das Format GGUF ist nicht automatisch mit LiteRT-LM kompatibel. Unterschiedliche native Bibliotheken, Modellformate und Security-Gates nicht ohne echte Gerätetests vermischen.

## Vorhandene Continuum-Regeln
Die bestehenden optionalen lokalen Sprachmodellwege und Modellimporte bleiben unverändert. Für die 0.3.18-Haupt-App hat **stabile Gesprächs-/Memory-/Coding-Agent-Verdrahtung** Vorrang vor einem ungetesteten Runtime-Tausch. Unabhängiger Test auf Fold7 nötig für Modell-Ladezeit, Speicherbudget, thermische Stabilität, Offline-Antwortqualität, Prompt-Herkunft und Tool-Gate-Autorität.

**Entscheid:** Kein neuer verpflichtender externer KI-API-Dienst, keine unbekannte oder dynamisch aufgelöste Runtimeversion in den Release integrieren. Dies ist ein verifizierter Integrationsplan, keine neue getestete KI-Funktion.
