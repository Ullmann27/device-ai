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

## Kostenoptimierte erste praktische Remote-Versuche (recherchiert 30.10.2026)

NVIDIA Developer Program: NVIDIA beschreibt die Nutzung gehosteter NIM-API-Endpunkte für Entwicklung/Prototypen als kostenfrei innerhalb geltender Nutzungslimits. Das sind echte NVIDIA-Cloud-GPU-Inferenz-Endpunkte, aber kein frei mietbarer dauerhafter 500-GB-RAM-Bereich und nicht für produktiven Betrieb ohne entsprechende Lizenz/Vertrag. Der Besitzer benötigt eigene NVIDIA-Developer-Registrierung und gültige API-Berechtigung. Kein Konto oder Token wurde angelegt.
https://docs.api.nvidia.com/nim/docs/product
https://docs.api.nvidia.com/nim/docs/run-anywhere

Hugging Face ZeroGPU: Öffentliche Spaces können gemäß aktueller Hugging-Face-Preisliste dynamisch kostenlose GPUs bis zur angegebenen Produktklasse nutzen. Das ist keine beliebig buchbare, dedizierte Continuum-Instanz, sondern kontingentierter Space-Betrieb mit Regeln und begrenztem Ressourcenfenster.
https://huggingface.co/pricing

Hugging Face Jobs sind hingegen kostenpflichtige CPU-/GPU-Jobs (auch cpu-basic), derzeit laut offizieller Preisliste per Minute. Nie ohne separate Besitzerfreigabe starten.
https://huggingface.co/docs/hub/en/jobs-pricing

Runpod: Aktueller öffentlicher Listenpreis Stand 08.10.2026 nennt u.a. H200 141GB für $5.29 je GPU-Stunde auf Pods und A40 48GB für $0.59 je GPU-Stunde. Vier H200 würden rein arithmetisch $21.16 je Stunde kosten, **ohne** Storage, Netz-, Steuer- oder Startzeitkosten; selbst 4x141GB beweisen noch nicht, dass ein konkreter 500-GB-Checkpoint dort ausführbar ist. Preise sind Momentaufnahmen, keine verbindlichen Buchungsangebote.
https://www.runpod.io/pricing

Vast.ai bietet einen dynamischen Marktplatz mit On-Demand- und interruptibler GPU-Miete. Interruptible ist preisgünstiger, kann aber die laufende KI-Arbeit jederzeit unterbrechen. Der Coding-Agent muss deshalb dauerhaft checkpointen und darf einen Abbruch nicht als Erfolg einstufen.
https://vast.ai/article/Rental-Types

Beste Reihenfolge: zuerst begrenzten kostenlosen NVIDIA-Prototyp-Endpoint für einen öffentlichen synthetischen Test nutzen, sofern verfügbar und vom Besitzer explizit freigegeben; danach billige bedarfsabhängige kleine GPU mit vLLM und erst mit gemessener Nutzeneffizienz große Tensor-Parallel-Hosts. Keine Terms-of-Service-Umgehung oder fremde GPU-Nutzung.


## NVIDIA NIM: echte Inferenzintegration vorbereitet, 30. Oktober 2026

Die offizielle NVIDIA-Dokumentation nennt den freien Entwicklungs-Endpunkt POST https://integrate.api.nvidia.com/v1/chat/completions. Benötigt ein individuelles NVIDIA Developer Program API-Token. Weder das GitHub-Repository noch die ChatGPT-Verbindung verfügt derzeit über den dafür notwendigen Token; kein echter GPU-Aufruf wurde ausgeführt.

Neue Dateien:
- .github/continuum/nvidia-nim-synthetic-benchmark.mjs: harter NVIDIA-HTTPS-Host, drei fest freigegebene Modell-IDs, maximal ein Aufruf je manuellem Auftrag, 24k Anfragebytes, 64k Antwortbytes, 120s maximale Frist, kein Redirect, kein Retry, keine private Gedächtnis-/Code-Übermittlung.
- .github/continuum/nvidia-nim-synthetic-benchmark.test.mjs: acht unabhängige synthetische Eingabe-/Transport-/Authentifizierungs-/Ergebnisprüfungen mit lokaler Response-Simulation. Benötigt keinen NVIDIA-Schlüssel und erzeugt keine Kosten.
- scripts/continuum-nvidia-nim-benchmark.mjs: --check zeigt nur den Konfigurationsstatus, --run ist ausdrücklich opt-in und verlangt NVIDIA_API_KEY als Umgebungsvariable. Keinen Schlüssel in Code, Chat, Pull Request, Logs oder GitHub-Workflow ablegen.
- Erweiterter CI-Workflow für Node 22, bisher nicht durch GitHub Actions bestätigt.

Die Benchmark-Aufgabe generiert deterministisch 192 öffentliche synthetische CSV-Datensätze und lässt das Modell die Summe bestimmter Kategorien modulo 65521 ermitteln. Der lokale unabhängige Erwartungswert beträgt 23677. Das Ergebnis wird nur bei exakter numerischer Übereinstimmung als richtig bewertet; Zeit, Prompt-/Completion-Token und End-to-End-Tokenrate werden ausgewiesen. Die Übertragung synthetischer Datensätze erlaubt keinerlei Schluss auf Vertraulichkeit der NVIDIA-Testdienste. Diese speichern unter Umständen Ein- und Ausgaben.

Reproduzierbar:
- node --test .github/continuum/nvidia-nim-synthetic-benchmark.test.mjs
- node scripts/continuum-nvidia-nim-benchmark.mjs --check
- Erst nach NVIDIA Developer Anmeldung und eigener Zugriffskontrolle: NVIDIA_API_KEY in einem geheimen temporären Umgebungsvariablenbereich setzen; node scripts/continuum-nvidia-nim-benchmark.mjs --run

Offizielle Referenzen:
https://docs.api.nvidia.com/nim/docs/api-quickstart
https://build.nvidia.com/nvidia/nemotron-3.5-lightning-30b-a3b
https://build.nvidia.com/nvidia/nemotron-3-ultra-550b-a55b
https://build.nvidia.com/deepseek-ai/deepseek-v4.1-flash

Testbericht vor echtem NVIDIA-Zugang: Der unmittelbar von GitHub abgerufene Modulquelltext wurde zusätzlich in einer isolierten JavaScript-Prüfumgebung mit simuliertem Response-Stream geprüft; vier fokussierte Assertions bestanden. Dies ist ausdrücklich weder ein nativer Node-Test noch ein bestätigter NVIDIA-GPU-Aufruf. Die Zahl 20 Token/s aus der Simulation stammt aus vorgegebenen Testwerten und darf nicht als reale Benchmark-Leistung ausgegeben werden.

Vor produktiver Continuum-Nutzung: Berechtigungs-/Geheimnisschutz auf dem Fold7, sichere Einzelauftragsfreigabe, Modellrichtlinien, Token-/Rate-Limits und providerseitiger Kosten-/Kontingentschutz, unabhängig verifizierte NVIDIA-Verbindung und Fold7-Gegenprobe. Keine implizite Übertragung von SWB1 oder CCC1-Daten.
