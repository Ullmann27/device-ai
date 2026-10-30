# CONTINUUM JARVIS · Verbindliche Forschungs- und Besitzerkontroll-Charta

**Gültigkeit:** Forschungs- und Architekturregel ab 30.10.2026 für Device AI Continuum. Dies ist **kein Nachweis implementierter Funktionen**. Grundlage sind bestehende PR #242, #245, #247, #249 und #250. Der Forschungszweig bleibt Draft; `main` wird nicht verändert.

## Systemziel

Ein persönlicher, langlebiger, möglichst lokal arbeitender JARVIS-Begleiter mit **einem autoritativen Gehirn** und synchronisierten, vom Besitzer genehmigten Endpunkten (Fold7, PC, Haus; Fahrzeug nur bei regelkonformer Audioanbindung). Gesprächskontext, nachweisbares Gedächtnis, Werkzeugfreigaben, lernende Strategien und Sprachdialog sollen innerhalb bestehender Continuum-Schnittstellen zusammenarbeiten. Konkrete Gerätefähigkeiten erfordern echte Hardware- und Berechtigungsprüfungen.

## Die vier Resilienz-Forschungsfamilien

**EXP-16 · Geräteübergreifender Zustand & Datenschutz.**
- Forschungsoption: authentisierte Ende-zu-Ende-Verschlüsselung, Gerätebindung, signierte Zustandsversionen, Merkle-/Hash-Nachweise, geprüfte Backups, Replay-Schutz, Ablauf/Widerruf.
- Optional nach messbarem Bedarf: Shamir Threshold Secret Sharing als Wiederherstellungsmechanismus, Zero-Knowledge-Proofs (ZKP) für **exakt definierte** Prüfbehauptungen. ZK-SNARKs replizieren weder Fakten noch beweisen sie die Wahrheit eines Weltmodells; synchronisierter Inhalt benötigt weiterhin einen autorisierten Datentransfer.
- **Nur autorisierte Backups**; nie heimliche Selbstvervielfältigung oder Umgehung von Stop-, Lösch-, Widerrufs- oder Shutdown-Befehlen. Bei Entzug aller Berechtigungen Ende aller Aktionen. Wiederherstellung nach Neustart nur auf ausdrücklichen Besitzerwunsch.

**EXP-17 · Evolutionsbasierte Software-Reparatur.**
- Vorhandene CCC1-Repair-Loop-, isolierte Executor-, Mutation-/Makro- und Review-Strukturen wiederverwenden; `CognitiveGenome` existiert bereits und erzwingt `allowSelfReplication=false`, `allowHiddenPersistence=false`, `allowShutdownEvasion=false`. **Diese Invarianten dürfen nicht entfernt werden.**
- Vorschläge von Mutation/Rekombination nur auf ausdrücklich freigegebenen Testkopien, konservative Ressourcenbudgets, unveränderliche Prüfer, negative Gegenbeispiele und versionsgebundene Provenienz. Keine automatischen Änderungen an Produktionsdateien, main, Sensorrechten, Netzwerkpfaden oder Modellgewicht; Besitzer-Review vor Promotion.
- Keine Ersatznetzwege zur Umgehung gesperrter Schnittstellen; ein API-Sperrsignal ist keine Aufforderung zum Ausweichen.

**EXP-18 · HDC / Vector Symbolic Architectures.**
- Optionale Forschung zu binären Hypervektoren (z.B. 10.000 Bit), XOR/Permutation/Bündelung als schnell prüfbarer Vorfilter für Reparaturstrategien, Kontextsignale und Anomalie-Kandidaten.
- HDC speichert nicht beliebige Daten verlustfrei und ersetzt Gemma oder die autorisierte Belegauswahl **nicht**. Effizienz und Genauigkeit sind gegen gleiche Aufgaben, gleiche Belege, gleiche Grenzen, gleiche Hardware und verifizierte Baselines zu messen.
- Beliebige überraschende Sensorwerte sind keine Erlaubnis zu proaktiver Geräteaktion.

**EXP-19 · Kryptografisch überprüfbare Besitzerautorität.**
- Bestehende ToolGate-/Owner-Gates sind maßgebend. Kryptografische Schlüssel gehören in geeignete sichere lokale Speicher; keine Geheimnisse im Git-Repository oder im Modellprompt. Verifizierbare Widerrufe, Schlüsselrotation, Nutzerwechsel, Consent-Scopes, hardwaregeeignete Attestierung und Gerätelöschung sind Teil des Gate-Designs.
- Besitzerkontrolle umfasst jederzeit **Stop, Pausieren, Offline, Entkoppeln, Export und Löschung**. Rechte sind geräte-, aktions- und zeitgebunden; keine pauschale Dauerfreigabe und keine Umgehung von Betriebssystem- oder Sicherheitsvorgaben.
- Ein „Root-Schlüssel“ legitimiert nicht beliebiges Verhalten: Android-Berechtigungen, geltendes Recht, Gerätesicherheit und unabhängige Verifikation bleiben erforderlich.

## Vier zusätzliche neurodynamische Forschungsoptionen

**EXP-20 · CTRNN-SHADOW:** beschränkte kontinuierliche Zustandsdynamik `dx_i/dt=(-x_i + sum_j W_ij σ(x_j) + I_i)/τ_i` mit endlicher, stabilitätserprobter numerischer Integration. Ausschließlich als zusätzliche Vorhersage-/Routing-Hypothese, nicht als unbewiesener Austausch des lokalen Sprachmodells. Nachweis: Ablationen auf externem Holdout, Reproduzierbarkeit, Timing, Speicher.

**EXP-21 · Fisher/EWC-SHADOW:** quadratische Regularisierung relativ zu früheren Parameterständen und empirisch geschätzten, insbesondere diagonal approximierten Fisher-Gewichten. EWC kann Vergessen **verringern**, nicht ausschließen. Es ist kein garantiertes vollständiges Gedächtnis. Vor jedem Training: Original-Checkpoint, verifizierte Replay-Kontrollen und Rollback; keine automatische Mutation des produktiven NTW1.

**EXP-22 · STDP/Metaplastizität-SHADOW:** ereignisbasierte lokale Updates nur als experimentelle Modellvariante mit begrenzten Gewichten, reproduzierbarem Timing und unabhängig überprüften Trainings-/Testsplits. Biologische Plausibilität ist kein Beweis besserer Coding-Leistung.

**EXP-23 · Energie-/Thermal-Budget:** nur bedarfsbezogen arbeiten, bounded gas, Pause/Abbruch bei zu hoher Last, kein versteckter Dauerbetrieb. Android `PowerManager.getThermalHeadroom` ist ein Indikator **wie viel thermische Reserve bereits beansprucht wird** (größer = näher an schweren Throttling-Grenzen; NaN möglich), nicht eine Batterie-Prozentzahl oder garantierter Temperaturwert. Echte Nutzung, RAM, Energie, Foreground-/Doze-Verhalten und Fold7-Screens müssen gemessen werden. Ein aktives Telefon braucht weiterhin Energie.

## Wissenschaftlicher und sicherheitstechnischer Freigabevertrag (alle EXP-16 bis EXP-23)

1. Aktuelle Quellen, schon vorhandene Pakete und PR-Divergenzen vergleichen, bevor ein Modul gebaut wird. Keine zweiten Gehirne/Worker/Gedächtnisse.
2. Neue Ansätze zunächst als **eigenständige, abschaltbare SHADOW-Hypothesen** mit dokumentierten Eingangssignalen, Aufgaben, unabhängigen Holdouts, Baselines und Abbruchkriterien implementieren.
3. Keine automatischen Promotes durch „0 Fehler“ bei synthetischen Tests. Verifizierte historische Fehler, echte Nutzerabläufe und unabhängige negative Fälle getrennt bewerten.
4. Kein produktives Selbstreplizieren, Shutdown-Evasion, versteckte Persistence, Berechtigungsumgehung, ungeprüfter Hardware-Aktor oder selbstständiges Modell-Training. Alle freigegebenen Aktionen über bestehende Owner-/ToolGate-Grenzen.
5. `main`, vorhandene Besitzerfakten, Secrets, Modellgewichte und signierte APKs unangetastet lassen. Forschung erhält eigenen Branch, Testnachweis, Commit-SHA, Fehler-/Skip-Protokoll und Rollback-Weg.
6. **APK-Gate:** Erst wenn vollständige Android-Gradle-/Node-Regression, tatsächlich gebautes und geprüftes APK-Artefakt und nachfolgende reale Fold7-Abnahme nachgewiesen sind, von funktionsfähigem Gerätelauf sprechen. Kein prozentualer Fortschritt ohne definierte Messskala.

## Quellen für spätere Experimente

- Kirkpatrick et al., *Overcoming catastrophic forgetting in neural networks*, PNAS 2017, DOI: 10.1073/pnas.1611835114 – diagonale Fisher-Approximation und Grenzen des EWC.
- Android Developers, PowerManager#getThermalHeadroom: https://developer.android.com/reference/android/os/PowerManager – 0 bis 1+ repräsentiert beanspruchten Thermal Headroom; `NaN` bedeutet u. a. nicht verfügbar.
- Bestehende Repository-Grenzen: `app/src/main/java/at/heinz/deviceai/cognitive/CognitiveGenome.kt`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, Issue #41, `AGENTS.md`.

**Status:** Architekturpolitik und experimentelles Backlog, keine neuen Berechtigungen. Keine automatische Echtwelt- oder Selbstüberlebensfunktion.
