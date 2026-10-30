# Continuum: reale Cloud-GPU statt simuliertem RAM

Stand: 2026-10-30. Draft PR #268. Status: RESEARCH_SHADOW / NO_GPU_PROVISIONED / NO_FOLD7_TEST.

## Anschluss an unsere echte Architektur

Samsung RAM Plus ist Auslagerung und schafft keine zusätzliche GPU. Gemietete Cloud-GPUs dagegen stellen reale entfernte Hardware bereit. Continuum behält Gemma, SWB1, ToolGate und den bestehenden CCC1-Coding-Agent lokal als autoritative Kette. Externe Knoten müssen die vorhandene Android ComputeNodeRegistry / ComputeScheduler und den vorhandenen Node-Adapter passieren. Kein zweiter Scheduler, keine neue Berechtigungsinstanz.

Vorarbeiten: PR #154 (DeepSeek HTTPS-Bridge: synthetische TLS-Tests, keine reale Inferenz), #168 (Virtual Model Fabric), #184 (verschlüsselter localhost-Speicher, keine physische 500-GB-Kapazität), #240 (ARGON F2 500-GB-Bandbreitenmodell, keine Fold7-Messung), #242 (Integration; F2 inaktiv).

## Implementierter Code

Datei .github/continuum/continuum-cloud-gpu-admission.mjs

- priceGpuSeconds berechnet exakt mit BigInt und Preisrundung nach Billing-Intervall USD-Mikroeinheiten. Preise werden als externe Angebotseingaben übergeben, nicht live erfunden.
- planCloudGpuAdmission ruft ausschließlich den bestehenden planVirtualModelRouteFromRegistry auf. Gleiche Modellrevision, derselbe Job und die existierende Registrierungs-/Budgetbeschränkung bleiben verbindlich.
- Speicherkapazität, aggregierte GPU-VRAM-Grenze, Quote-Gültigkeit, Kostengrenze, Owner-Datenschutz und Preisgleichheit im Fabric-Angebot werden geprüft.
- Selbstdeklarierte Werte capacityVerified, runtimeVerified, weightsVerified und endpointReady sind keine vertrauenswürdigen Drittbeweise. Selbst eine passende Quote bleibt ausdrücklich REVIEW_ONLY.
- Keine Netzwerkverbindung, keine Gewichtsdatei, kein Cloud-Start, keine Bezahlung, keine automatisch freigegebene Inferenz. Kein Eingriff in laufendes Gemma, den CodingProvider oder den installierten APK.

## Aktuelle offizielle Forschungsansätze

NVIDIA NIM stellt containerisierte, teilweise OpenAI-kompatible KI-Serving-Laufzeiten für reale NVIDIA GPUs bereit. Es ist keine kostenlose virtuelle Hardware.
https://docs.nvidia.com/nim/large-language-models/latest/about-nim-llm/nim-offerings.html

vLLM bietet Tensor-Parallelismus (mehrere GPUs eines Hosts) und Pipeline-Parallelismus (über Hosts). Die Gewichte müssen tatsächlich geladen/lesbar und Hardware/Modell kompatibel sein.
https://docs.vllm.ai/en/stable/serving/parallelism_scaling/

NVIDIA Dynamo kann Prefill und Decode auf getrennten Worker-Pools verteilen, KV-Cache-aware routen und Auslastung erhöhen. Nicht zwangsläufig schneller bei kurzen Anfragen oder langsamem Netzwerk.
https://docs.dynamo.nvidia.com/dynamo/user-guides/disaggregated-serving

Runpod Pods und Serverless sowie Vast.ai erlauben reale NVIDIA-GPUs stunden- oder zeitabhängig zu mieten. Verfügbarkeit, Storage, Gebühren, Modellbetrieb und Billing müssen für jeden Anbieter separat geprüft werden. Serverless Scale-to-zero kann Leerlaufkosten verringern, nicht jedes GPU-Angebot ist ein kompatibler KI-Endpunkt.
https://www.runpod.io/pricing
https://www.runpod.io/product/serverless
https://vast.ai/article/how-much-does-it-cost-to-rent-a-gpu-in-the-cloud-live-pricing-guide

## 500-GB-Grenze und Priorität

Der in PR #154 dokumentierte DeepSeek-Hub-Plan hat 48 deklarierte Gewichtsshards mit insgesamt 510296708312 Bytes. Das benötigt echten Storage und zur Inferenz passende GPU/CPU-Ressourcen. Aggregierter VRAM oberhalb der Gewichtsgröße beweist KEIN funktionierendes Serving (KV, Aktivierungen, Quantisierung, Tensor-/Pipeline-Kommunikation, Treiber und Laufzeit fehlen im simplen Rechner). Beginne mit kleinem, exakt identifiziertem Modell und vorhandener Modell-Bridge statt einer ungetesteten 500-GB-Übertragung.

## Freigabe- und Experimentkette

1. Auf genau diesem Branch native Node-JUnit- und bestehende Fabric-/Coding-Remote-Regressionstests; eigener CI-Workflow ist veröffentlicht.
2. Echter Betreiber liefert aktuelle, nachprüfbare GPU/Storage/Endpoint-/Preis-Daten, der Besitzer prüft das Angebot. Keine selbst erfundenen Gratis-GPUs.
3. Frischer registrierter, signierter Node-Beleg und ausdrückliche Besitzerfreigabe für genau eine Aufgabe, einschließlich Preisdeckel, Steuer- und Volumenkosten. Preisdeklaration ist kein finanzieller Hard Cap.
4. Auf ausdrücklich genehmigtem bereits existierendem Cloud-Host passende NIM/vLLM-Laufzeit einrichten, private TLS-Verbindung absichern, kleine echte Inferenz ausführen. PR #154 verlangt noCharge und darf nicht heimlich für kostenpflichtige Requests aktiviert werden.
5. Gegen lokale Gemma-Baseline messen: Prompt-Tokens/s, Decode-Tokens/s, Latenz p50/p95, Time-to-first-token, RAM, Energie am Fold7, tatsächliche USD-Kosten, Traffic-Bytes und unabhängig bestätigte CCC1-Reparaturen.
6. Bei schlechtem Netz, falschem Zertifikat, Modellkonflikt, Ablauf oder privatem Kontext niemals ungefragt übertragen, keine automatische zweite Remote-Auswahl; lokale Continuum-Funktionen erhalten.
7. Erst nach Gerätetest, unabhängigen Tests, Datenschutz-/Besitzerfreigabe und signiertem Build ins Original-APK integrieren.

Der neue Code erschließt aktuell **noch keine zusätzliche reale GPU-Rechenleistung**. Er reduziert das konkrete Risiko falsch kalkulierter und nicht ausführbarer Mietrouten und bereitet die belegbare Integration vor. Keine externe GPU, API-Schlüssel oder Rechnung wurden angelegt.
