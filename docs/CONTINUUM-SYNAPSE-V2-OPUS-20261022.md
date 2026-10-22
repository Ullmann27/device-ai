# EXP-SYNAPSE-ZERO v2 – Prüfung, neuer eingefrorener Benchmark und Ablationen (Opus, 22.10.2026)

**Zweig:** `opus/continuum-synapse-v2-20261022`, abgeleitet von PR #71 (`experiment/continuum-synapse-zero-20261022`, `2b58ed17`).
Der Stand ist reine Forschung im Schatten: keine Gewichts-Promotion, keine Android-UI-Anbindung, kein Merge nach `main`.
SWB1-, CDX1- und CSX1-Gewichte sowie Checkpoints werden weder gelesen noch verändert. Eine Ausnahme ist der dokumentierte,
rein lesende CSX1-V8-Vergleich in einem separaten Scratch-Build.

Status: **in Arbeit**. Dieses Dokument wird mit jedem gepushten Teilschritt fortgeschrieben.

## 1. Reproduktion PR #71 (echtes JUnit 4.13.2, kein Shim)

| Schritt | Befehl (aus `app/src`) | Exitcode | Ergebnis |
|---|---|---|---|
| Kompilieren | `kotlinc main/java/at/heinz/deviceai/learning/synapse/*.kt test/java/at/heinz/deviceai/learning/synapse/*.kt -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d pr71.jar` (kotlinc 2.0.20, JDK 21) | 0 | – |
| JUnit | `java -cp pr71.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore …ContinuumSynapseZeroShadowTest …SynapseContrastiveRoleProbeTest` | 0 | **OK (10 tests)** |
| DEV-Probe | `java -cp pr71.jar at.heinz.deviceai.learning.synapse.SynapseContrastiveShadowBenchmark` | 0 | Seeds 47/99/20261022: vorher 1/4, 1/4, 4/4 → nachher 4/4, 4/4, 4/4 (je 1.080 Updates) |

Damit sind die Angaben aus PR #71 exakt reproduziert, diesmal mit echtem JUnit.
Logs: `docs/evidence/synapse-v2-20261022/pr71-*.txt`. Die vier DEV-Fälle bleiben **DEV** und sind kein Blindtest.

## 2. Architekturprüfung v1 (gemessen, `SynapseV1ReviewDiagnostics`, Log `review-v1.txt`)

| Nr. | Befund | Messung | Bedeutung |
|---|---|---|---|
| R1 | Die Rolle ist `e_1` (one-hot). „Unbind“/„bind“ ist damit eine **feste zyklische Verschiebung**: score = Σ_k q[k]·c[(k+1) mod 16]. | max. Abweichung **0,0** | Die „holographische Rollenbindung“ ist mathematisch ein gewöhnliches bilineares Skalarprodukt. Sie bindet keine Rollen. |
| R2 | Der analytische Gradient von `trainPair` stimmt. | max. Fehler **7,9·10⁻¹¹** über 2.800 Parameter (zentrale finite Differenzen) | Die Implementierung des paarweisen Softmax-Gradienten ist korrekt. |
| R3 | Zeichen-2–4-Gramme werden in 128 Buckets gehasht. | Belegung 62–98 von 128 je Satz; positiver und negativer Satz teilen 43–83 Buckets (Jaccard 0,52–0,75) | Starke Überlappung. Die Unterscheidung hängt an wenigen nicht geteilten Buckets. |
| R4 | Die Kennungs-Regex macht **jede** ID zu `subj_slot`. | \|score(R10-Satz) − score(R99-Satz)\| = **0,0** bei Frage nach R10 | Mehrentitäten-Bindung ist konstruktionsbedingt unmöglich. |
| R5 | Die Begrenzung `coerceIn(-2, 2)` würde exakte Gradienten brechen. | 0 Parameter nach 4.000 Updates mit lr 0,5 an der Grenze | Sie greift praktisch nicht; das ist nur ein theoretischer Befund. |
| R6 | Sinkhorn erzwingt eine 1:1-Zuordnung. | Zwei Roboter in derselben Halle: Rohpräferenz 0,9 → nach Sinkhorn **0,5/0,5**; ein Gleichstand bleibt 0,5/0,5 | Bei nicht-injektiven Relationen ist Sinkhorn schädlich und entscheidet keine Gleichstände. |
| R7 | Adversariale Fälle gegen den Beweiskern (13 Fälle) | 5 OK, **4 unbelegte Antworten**, **4 unbegründete Enthaltungen** | siehe unten |

R7 im Einzelnen:
- **Unbelegte Antworten** (Sicherheitslücken):
  - Verneinung mit „nie“ (nicht in der Vetoliste);
  - Präfix „Falsch:“;
  - verneinte Frage („… benötigt Z77 nicht?“);
  - „Wo steht das Modul von Z77?“ wird mit dem Standort des Roboters beantwortet.
- **Unbegründete Enthaltungen:**
  - eine Verneinung in einem fremden Satz blockiert global;
  - Kette im selben Satz;
  - Passiv;
  - Zeitbezug „früher/heute“.
- **Korrekt:** falsche Brücke, „niemals“ (nur weil die Regex-Wortfolge bricht), zwei Subjekte in einem Satz, kyrillisches Homoglyph-„М4“, Präfix-ID „M4“ vs. „M40“.
