# Continuum German research contradiction gate — 28.10.2026

Separate Draft-PR stacked on #204, which itself is stacked on #203. Parent SHA: `f2a42ed28e59b3eb8acc1b1ee034c430c8310acc`. No main merge, no owner APK signing or installation. EXP-00/06/07, Issue #41 and AGENTS.md reviewed.

**Evidence-backed issue:** Existing `ResearchPipeline` classifies similarity ≥0.7 as `SUPPORTS` before looking for negation. Nearly identical claims differing only by German `nicht` can thereby falsely corroborate each other; both that pipeline and `DeepResearchEngine` only recognize English negation. Scope limited to a small deterministic German/English explicit-negation helper reused by both existing paths and actual negative integration tests. This does not grant new network access, tools, memory writes or model authority.

**Status at creation: WIP / NOT_RUN.** Real Android Gradle/Termux/Fold7 and owner P12 unavailable to this environment. Use the existing local release builder only after full suite, APK QA and owner-signing verification. An improvement to a concrete contradiction case is not a measured overall search-quality improvement.

## Implementierter Forschungspfad und unabhängiger lokaler Compile-Nachweis

**Tatsächlich veränderter produktiver Quellcode**:
- `cognitive/ResearchNegationSignals.kt`: dieselbe konservative explizite DE/EN-Verneinung für beide vorhandenen Web-Recherchewege; Negation allein ist kein Wahrheits- oder Autoritätsnachweis.
- `cognitive/Research.kt`: Gegenbeweis-Prüfung vor der bisherigen `similarity >= 0.7`-Bestätigung, verhindert unzutreffende positive Bestätigung sehr ähnlicher Gegenbehauptungen.
- `cognitive/ResearchEngine.kt`: nutzt denselben Signalsatz, um `CONTRADICTED` im bestehenden Beleggraphen und ggf. notwendige Nachrecherche auszulösen.

**Neue echte Projekt-JUnit-Tests eingetragen, hier noch NOT_RUN**: `ResearchNegationSignalsTest.kt` (4 Methoden), `ResearchTest.kt` (1 Integrationsmethode), `ResearchEngineTest.kt` (1 Integrationsmethode). Die Projektregression darf nicht mit dem isolierten Hilfstest gleichgesetzt werden.

**Tatsächlich lokal ausgeführt:** `kotlinc-jvm 1.9.0` / JDK21 kompilierte die bit-identische Datei `ResearchNegationSignals.kt` (GitHub- und lokaler Git-Blob-Hash `0fe6e121782be6a630c3a9edf0e5f63901dfa715`) zusammen mit einem neun Fälle umfassenden eigenständigen Kotlin-Prüfprogramm. `java -jar negation-check.jar` ergab **9/9 PASS, Exitcode 0**. Kein Android-Kompilier- oder Fold7-Nachweis. Rohstatus und Testvektoren: [lokale Validierung](../../evidence/continuum-german-research-20261028/local-kotlin-negation-validation.md).

**Benutzerwert / Abnahmebedingung:** Die autonome Recherche kann bei deutschen Gegenbehauptungen wie „Gerät unterstützt kabelloses Laden“ vs. „Gerät unterstützt kabelloses Laden nicht“ potenziell einen falschen Konsens vermeiden. Mehr Suchtreffer, höhere Gesamtintelligenz, zuverlässige semantische Wahrheitsbewertung oder spürbare Gemma-Verbesserung sind **NICHT nachgewiesen**. Der Prüffall allein belegt keinen Gesamtnutzen. Neue Netzwerk- und Schreibrechte wurden nicht geschaffen.

**APK-Status = BLOCKED / NOT_BUILT**. Für das einzige zulässige **In-Place-Update der echten Besitzer-App** `at.heinz.deviceai.mentorpaired` bleibt der vorhandene Original-P12/Passwort lokal erforderlich; letzteres ist in dieser Sitzung nicht zugänglich. Zusätzlich fehlen hier das erforderliche Android SDK36/JDK17/Gradle8.11.1, der vollständige private Checkout und der Fold7-Gerätezugriff. Der vorhandene `scripts/continuum-local-owner-apk.sh` prüft Git-SHA, Tests, Lint, Paketname, Versionscode und Signierzertifikat. Keine Debug-APK als Besitzerupdate benutzen oder die vorhandene App deinstallieren.

**Nächster echter Arbeitsschritt im bestehenden authentifizierten Android/Termux-/Linux-Checkout:** zuerst den PR #205 an einen überprüften, sauberen HEAD binden, anschließend PR #204s vollständige JUnit-Regression und die bestehende Haupt-App-QA-Buildstrecke ausführen. Besitzer-P12 nur sicher lokal bereitstellen und danach mit dem Original-Zertifikat die echte Haupt-App bauen. Vor Freigabe müssen installierte alte Version, Signatur und künftige Versionsnummer geprüft und ein kontrollierter realer Recherche-Baseline/Kandidat-Test durchgeführt werden. `main`, #203, #204 und vorhandene Besitzerinstallation bleiben unangetastet.
