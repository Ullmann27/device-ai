# EXP-SYNAPSE-ZERO · Kontrollierter Forschungsstart (22.10.2026)

**Repository:** `Ullmann27/device-ai` · **Ausgangsstand:** signierte Mentor-Pair-APK v0.3.12, Commit `619ebeb2831c8184a91e3c08e165ed4f4794f969`.

**Quelle:** vom Besitzer hochgeladenes, achtseitiges „Argon 4 Master-Experiment für echte KI-Intelligenz & Anweisung an ChatGPT.pdf“, Seite 1–8. Die auf Seite 4/5 mit „vollständig“ bezeichneten Kotlin-Ausschnitte enthalten **nicht die vollständige Implementierung**; die Behauptungen „<2 ms“ oder „12/35→22/35“ sind bislang keine gemessenen Resultate.

## Umfang dieses isolierten Meilensteins

Dieser PR implementiert bewusst **keine automatische Gewichts-Promotion, keine Android-UI-Integration und keine bestehende Modellveränderung**. Er trennt zuerst drei experimentelle Subsysteme:

1. `ContinuumSynapseZeroShadow.kt`: deterministische, schreibfreie Extraktion streng begrenzter expliziter deutscher Relationen. 1-Hop: Subjekt → Ort. 2-Hop: Roboter/Einheit → **exakt identifizierte** Modulkennung → Kühlmittel/Schutzgas. Nur dann ausgeben, wenn **ein einziger** belegbarer Wert vorliegt. Beide Hops tragen originale UTF-16-Zeichenoffsets, Satzindex, Relation, Subjekt und SHA-256 des gesamten unveränderten Quelltextes. Negative, konkurrierende, fremde und gebrochene Ketten werden abgelehnt; ein globaler Negations-Veto ist absichtlich konservativ.
2. `SynapseHolographicProbe`: isolierte zirkuläre Faltung, adjungierte Korrelation und iteratives Sinkhorn-Routing als mathematische **Diagnose**. Die Matrix wird **nicht** als Wahrheit interpretiert und kann nie eine Belegprüfung ersetzen.
3. `SynapseContrastiveRoleProbe.kt`: **eigenständig trainierbares** Rollen-Ranking mit 128 Buckets × 16 Dimensionen × 2 Tabellen = **4096 trainierbare Parameter**. Zirkuläre Rollen-Unbindung und zweiphasige analytische pairwise-Softmax-Gradienten für positive und harte negative Sätze. Training nur explizit über `trainPair`, ausschließlich im RAM; kein Import fremder Gewichte, keine Verbindung zu SWB1/CDX1/CSX1. Der Probe-Score autorisiert **keine** Tatsachenbehauptungen und beeinflusst die Belegentscheidung noch nicht.

### Tatsächlich ausgeführte lokale technische Prüfungen

- Linux/JDK 21, `kotlinc-jvm 1.9.0`: beide Kotlin-Produktionsklassen und die zugehörigen zwei JUnit4-förmigen Testdateien zusammen kompiliert. Ein temporärer nur für diesen Testlauf angelegter JUnit-Annotations-/Assertions-Shim wurde verwendet, weil in dieser Umgebung kein echtes JUnit-JAR zur Verfügung stand. Die Testmethoden wurden tatsächlich per Java-Reflection ausgeführt: **10/10 PASS**. Das ist **kein** Gradle-/Android-JUnit-Beweis.
- Die vier zentralen GitHub-Dateien wurden gegen Git-Blobs der lokal tatsächlich ausgeführten Dateien abgeglichen:
  - Shadow-Core: `f17a590b3e3f520d623d6a3fd09a1dc5b3cea715`
  - Shadow-Test: `1fc3fab598c7410688753c123443234ac39b3ede`
  - Trainierbarer Probe: `7664baf884a1ed78845b59de43c070201f61a87d`
  - Trainings-Test: `fc53d1a2ff6b37506a54829622c9e96c20400d83`
- Der erste eigene Trainingsversuch **scheiterte**: sehr kleine Initialgewichte und eine zu starke Mittelung lieferten nach 240 Schritten praktisch keinen Kontrastgewinn. Nach korrigierter `1/sqrt(n)`-Merkmals-/Gradientenskalierung bestand der Test mit einer echten positiven Änderung des Rangabstands.

### Mini-DEV-Messung – kein Forschungsdurchbruch

Separater **öffentlicher synthetischer Entwicklungsdatensatz** (`SynapseContrastiveShadowBenchmark.kt`): drei Trainingspaare aus Kühlmittel/Ort/Schutzgas; vier DEV-Fragen mit anderen Kennungen und teils anderen Formulierungen. 360 Epochen × drei Paare = 1080 explizite Update-Schritte pro Seed.

| Initialisierung | Richtige Rangentscheidungen vor Training | Nach Training |
|---|---:|---:|
| 47 | 1/4 | 4/4 |
| 99 | 1/4 | 4/4 |
| 20261022 | 4/4 | 4/4 |

Die vier DEV-Beispiele waren vor der Architekturabnahme bekannt und dürfen **nie** als unabhängiger eingefrorener FROZEN_TEST ausgegeben werden. Der erste Seed zeigt Lernfortschritt *auf diesem winzigen handgeschriebenen DEV-Satz*, jedoch **keinen allgemeinen Paraphrasendurchbruch**. Ablationen, stärkere Negativbeispiele, Entity-/Template-Gruppensplits, Nicht-Fakten-Antwortsperre und offene Wiederholungsdiagnostik folgen.

## Formale Sicherheitsgrenzen und offene Hypothesen

- Ein SHA-256-Offset-Beleg bestätigt **Quelltreue**, nicht Wahrheit, logische Folgerichtigkeit oder authentisierte Urheberschaft.
- Eine zirkuläre Korrelation ist keine allgemein verlustfreie algebraische Inversion. Sinkhorn erzwingt mathematische Massenverteilung, **nicht** Einzigkeit echter Tatsachen; Mehrfachwerte dürfen nie willkürlich ausgeschlossen werden.
- Die vorgeschlagene „inverses R halbiert Trainingsdaten“-Behauptung gilt nicht für nicht-injektive Relationen (zwei Roboter dürfen dasselbe Modul verwenden). Dieser Prototyp generiert **keine** inversen Besitzerfakten.
- Der SHADOW-Beweiskern nutzt vorsichtige endliche Regex-Satzformen. Pronomen/Passivsatz/implizite Kausalität, OOD-Themen, komplexe Verneinung und Eigentums-/Zeitkonflikte sind **nicht gelöst**. Bei globalem Negationswort verweigert er absichtlich zu viele Antworten.
- Keine automatische Folgerung aus vorher bloßer Wortnennung. Modul-IDs müssen buchstaben-/zifferngleich in beiden explizit typisierten Kanten stehen. Keine Kette bei `Z77 → M4`, `M9 → Helium`.
- Keine Gewichts-Serialisierung, keine App-Datenmigration, keine Systemberechtigung, kein Termux-Aufruf, keine Signierschlüssel und kein Zugriff auf das private Besitzerarchiv.
- Runtimescore ist **nicht** zertifizierte Wahrscheinlichkeit oder geeignetes Ersatzmaß für das bedingte CSX1-Risiko. PR #68 sowie QF2+ PR #70 werden in eigenen Stacks geprüft und nicht mit diesem Experiment gemischt.

## Nächste falsifizierbare Arbeit

1. Gegenprüfen, ob der unabhängige Prüfverifier dieselben Subjekt-/Relations-/Offset-/Hash-Bindungen wie ein neu geschriebenes adversariales Orakel prüft; zusätzliche Negation, passive Formen, Unicode und 3-Satz-Szenarien.
2. Gradientenprüfung gegen numerische finite Differenzen, Trainingskurven, Parameterpersistenz nur innerhalb eines Shadow-Sandboxes, Split-Hygiene und Kollisionen/128-Buckets-Ablation.
3. HRR ohne Sinkhorn, HRR mit Sinkhorn und mit/ohne typisierte 2-Hop-Brücken **auf neuen** nach Satzfamilien und Entitäten getrennten Evaluationsfällen messen. Keine Testdaten zur Schwellenoptimierung.
4. Bestehende CSX1-Baseline aus PR #62 nur lesend vergleichen; deren bekannte synthetischen Testfälle nicht als neues Blindmaterial behandeln.
5. Erst bei belegtem Mehrwert und 0 unzulässigen Negativdurchbrüchen optionalen Shadow-Lab-Aufruf zur Android-Laufzeit prüfen; **niemals** eigenständig in den Chat promovieren.

**Freigabestand:** `SHADOW_SOURCE_COMMITTED / LOCAL_KOTLINC_10_10 / DEV_PROBE_3_SEEDS`; `FULL_JUNIT`, `ANDROID_GRADLE`, `DEVICE_PERFORMANCE`, `INDEPENDENT_GENERALIZATION` und `APK_RELEASE` sind **offen**.
