# Kontrastives Lernen – experimentelle Verifikation

Der bestehende SWB1-Subword-Encoder wird ohne externe Gewichte genutzt. Der Experimentpfad trainiert ausschließlich eine Kopie und ersetzt weder produktive Modellgewichte noch Benutzererinnerungen automatisch.

## Prüfungen

* Positive Textpaare entstehen durch maskierte Wortansichten, negative durch verschiedene Trainingsbeispiele.
* Training und Validierung nutzen getrennte, normalisierte Texte. Die Testdaten sind synthetisch und enthalten keine privaten Informationen.
* Die CI führt Node → Kotlin → Node aus: Node erstellt eine SWB1-Referenz und Evaluationskennzahlen, Kotlin trainiert unabhängig, vergleicht 4.113 Modellparameter sowie Top-1/MRR-Kennzahlen mit einer absoluten Toleranz von 1e-8 und schreibt den eigenen Modellstand; Node prüft diesen erneut.
* Änderungen an den Modellgewichten dürfen nicht als allgemeine Intelligenzsteigerung ausgegeben werden. Die unabhängige Holdout-Kennzahl wird dokumentiert, nicht automatisch als Erfolg gewertet.
* Vor einer produktiven Aktivierung fehlen zusätzliche, disjunkte Datensätze, eine belastbare Regressionsgrenze, Stabilitätsprüfungen und die kontrollierte Übernahme in ContinuumLearningCore.

Die täglichen Offline-Jobs können vom Android-Betriebssystem verschoben werden. Die Fehlerprotokollierung verhindert stilles Verschlucken von Ausnahmen; sie garantiert kein permanentes Hintergrundtraining.
