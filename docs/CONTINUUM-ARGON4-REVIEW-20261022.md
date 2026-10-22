# Continuum · Kritische Auswertung des Argon-4-Gutachtens vom 22.10.2026

## Prüfgrundlage und Status

Quelle: vom Besitzer bereitgestelltes zehnseitiges PDF **„Argon 4 Technisches Forschungs- und Code-Review-Dossier (22.10.2026)“**, Teil A–G, Seiten 1–10. Argon hatte ausdrücklich **keinen direkten GitHub-Zugriff** und beurteilte ältere Snippets (unter anderem PR #67 HEAD `2ea5650`). Diese Prüfung kombiniert das Dossier mit direkt im Repository rückgelesenen Dateien von PR #67 HEAD `13e67c958e626aca56659579efa158b82c056de3` und CSX1-Branch PR #68 HEAD `07b66f40d098aed5b038c94ac872011065277f7d`. Keine Änderungen an `main`, der signierten APK, Gewichten oder persönlichen Daten.

**Ergebnis:** fünf interessante Experimente, aber der PDF-Code ist teilweise über Seitengrenzen abgeschnitten (vor allem D.2, D.3, D.4, E.1 und E.2) und darf nicht als vollständiger kompilierbarer Patch übernommen werden. Kosten-, Leistungs- und Innovationsbehauptungen sind Hypothesen, keine Samsung-Messungen.

## 1. Tatsächlich vorhandene und fehlende Fehler

| Argons Befund | Ergebnis im aktuelleren GitHub-Stand | Korrekte Entscheidung |
| --- | --- | --- |
| C1: Zertifikat prüft keine aktiven Modellgewichte | War in PR #62 richtig; PR #68 setzt inzwischen `answer(model,evidence,question,certificate)` mit Snapshot-SHA im selben Monitor um und hat die alte Runner-Signatur repariert. | PR #68 vollständig auf JVM testen; nicht doppelt als neue Architektur einbauen. |
| C2: `binomialCdf(errors, outcomes.size, alpha)` angeblich „falsches N“ | **Begriffliche Verwechslung:** Die vorhandene Garantie bezeichnet explizit **unbedingtes Fragenrisiko** `P(answered AND wrong)`. Dafür ist `n=alle Fragen` mathematisch der richtige Nenner. Sie zertifiziert **nicht** `P(wrong | answered)`. | Risikomaß im UI/Papier ausdrücklich unterscheiden; bedingte Qualitätsprüfung **zusätzlich** und gesondert kalibrieren. Die alte Garantie nicht stillschweigend ersetzen/als falsche Mathematik ausgeben. |
| C2: Beispiel 6 falsche aus 264 | Die bedingte Fehlerquote ist 100 %, die unbedingte 6/264 ≈ 2,27 %. Die unter `Bin(264,0.05)` linke Flanke ist etwa 0,0206. Keine direkte Widerlegung der ausgewiesenen Joint-Risk-Garantie, aber großer Unterschied zur Nutzererwartung. | Coverage, Gesamtfehler und selektive Präzision *gemeinsam* berichten; Bedingungs-Risiko benötigt korrektes statistisches Verfahren und genügend beantwortete Kalibrierfragen. |
| C1: Schwellenoperator `>=` vs. `>` | Auf PR #68 ist `prediction.confidence >= certificate.threshold` bereits implementiert. | Keine zusätzliche Reparatur nötig; Regression behalten. |
| QF2: kurze Fachwörter ohne Pflichtanker | Im bisherigen PR #67: Wenn keine langen oder gemischten Ziffernkennungen vorhanden sind, Rückfall auf unscharfe Rangprüfung. Reales Risiko. | Hier in separatem Folgezweig nur für `SOURCE_TEXT` mit kurzen Ankern ergänzt. Besitzererinnerungen unangetastet. |
| QF2: präfixähnliche Termini | Die alte Regel erlaubte unzulässige Zwei-Zeichen-Präfixnähe (`Speicherplan` versus `Speicherplatz`). | Gerichtete Komposita und eng begrenzte Flexionssuffixe eingeführt, gemeinsam in JS/Kotlin. |
| Bisher übersehen: ungültige JS-Unicode-RegExp | Tatsächlicher **Node/V8-Syntaxfehler** in früherem QF2: `/\\p{L}/u` und `/\\p{Nd}/u` enthielten doppelte Backslashes; der JS-Parser lehnt diesen Quelltext ab. | Im vorliegenden Branch durch echte Unicode-Property-Expressions ersetzt und direkt gegen rückgelesenen GitHub-Blob geprüft. |

**Teststand dieses Review-Zweigs:** der exakt aus GitHub abgefragte neue JS-Quelltext (Blob `6324a3c7f8eedfb64426fdf8913c79615afa6baf`) wurde im isolierten V8 geladen. Die beiden exakt rückgelesenen Testdateien (`retrieval-false-positive.test.mjs`, Blob `dd30f7be9d7175f407353c2520dd329f91e33b8b`, und `retrieval-compound.test.mjs`, Blob `6be4ced7d13a71db12f8cdfafd33a349662557a5`) bestanden **13/13** unter einem eigenständigen Test/Assert-Shim; weitere **15/15** direkte Funktions-Gegenproben bestanden. Dies sind **keine** vollständigen Node-`node:test`-Ausführungen und **keine** Kotlin-/Gradle- oder Samsung-Tests.

## 2. Argons zusätzliche Codevorschläge – nicht blind übernehmen

**Quellenfilter QF2+ (PDF Seite 4):** Der Vorschlag, alle kurzen Inhaltswörter gegen denselben Beleg zu fordern, kann viele berechtigte Fragen übermäßig blockieren. Er ist nur als konservativer unbestätigter `SOURCE_TEXT`-Filter vertretbar, mit eigener Präzisions-/Recall-Messung. Symmetrische Langkomposita würden erneut `Fehlerrückführung` mit bloßer `Rückführung` verwechseln; dieser Zweig nutzt bewusst gerichtete Übereinstimmung. `wordEvidence` und Owner-Fakten werden nicht verändert.

**Bindungszertifikat (PDF Seite 5):** Ein SHA-256-Hash von Feldern (Argons `certificateSealSha256 = computeSeal()`) ist eine **Integritätsprüfsumme**, kein authentifizierter Beleg: Eine Person mit Schreibzugriff kann Felder und SHA gemeinsam neu berechnen. Für authentische Herkunft wäre ein vertrauenswürdig gesicherter Schlüssel/HMAC oder eine digitale Signatur mit überprüfter Vertrauenskette erforderlich. Die Modell-/Checkpoint-SHA-Bindung aus PR #68 ist trotzdem ein sinnvoller erster Fail-Closed-Schutz. Ein neuer Kalibriervertrags-Entwurf braucht außerdem eine formal geprüfte Definition für *bedingtes* Risiko samt Festlegung der Schwellen-Testreihenfolge und Cluster-Abhängigkeiten.

**Proof-Carrying Graph (PDF Seiten 2, 3, 6):** Die Prüfung exakter Originalbytes, Byte-/UTF-16-Offsets, Hash, Status `REVOKED` und Quellen-ID kann **Provenienz/Unverändertheit** beweisen. Sie beweist **nicht**, dass ein Satz wahr ist, dass die behauptete Relation oder das Subjekt korrekt ist oder dass Negation zuverlässig verstanden wird. Subjekt-/Relationskanten müssen aus einer vertrauenswürdigen expliziten Struktur stammen oder gesondert validiert werden. Am besten als read-only Shadow-Validator zuerst experimentieren; keine neuen Besitzerfakten erfinden.

**CRB1-Brückenencoder (PDF Seite 7):** Ein 4.096-Parameter-Modell mit SLOT-Maskierung und kontrastivem Training ist testbar und kann nützliche relationale Signale lernen, ohne SWB1/CDX1 zu überschreiben. Eine große Verbesserung von 12/35 auf 22/35 unbekannte Umformulierungen ist **ein Ziel**, keine Prognose. Gruppenweiser Split nach Entitäten **und Satzfamilien**, neue semantisch unabhängige Formulierungen, mehrere Initialisierungen und einfrierbarer Testdatensatz zwingend. Trainingsdauer unter 250 ms oder RAM unter 512 KB auf ARM64 sind ungemessene Schätzungen.

**Coding-Agent (PDF Seite 8):** Argons Zustandsautomat/Handoff-Idee ist brauchbar, aber [PR #57](https://github.com/Ullmann27/device-ai/pull/57) und [PR #58](https://github.com/Ullmann27/device-ai/pull/58) besitzen bereits kanonischen Teamleader, Worktree-Tests, Quittungen und Android-UI-Entwicklung. Keine zweite konkurrierende Pipeline installieren. Gute Ergänzungen: wiederaufnehmbare maschinenlesbare Handoffs, sichere Revisionsbindung, Limit-/Timeoutzustände, überprüfte Hashes. Eine reine Datei-Endungs-Blacklist oder diff-regex reicht **nicht** als Ausführungs-/Sicherheitsgrenze.

**Android-Backup (PDF Seite 10):** Ein Vorab-Guard, der erst *in der bereits aktualisierten App* startet, kann nicht vor der APK-Überinstallation sichern. Externes, vom Besitzer kontrolliertes Backup muss deshalb bereits vor der Installation vorhanden sein. Interne Kopien im selben App-Sandbox-Verzeichnis schützen nicht vor Deinstallation oder Sandboxverlust. Kein ungetestetes Storage-Migrationsexperiment im nächsten Release.

## 3. Selektives Risiko separat und korrekt erforschen

- `R_joint(t) = P(answered at t AND wrong)`; für dieses Ziel bleibt der vorhandene `n=alle Fragen`-Binomialtest legitim unter den Kalibrierannahmen.
- `R_cond(t) = P(wrong | answered at t)`; hierfür ist die Stichprobe der **beantworteten** Fragen relevant. Unsicherheit und Risiko dürfen bei sehr kleiner Antwortzahl nicht verschwiegen werden.
- `Coverage(t) = P(answered at t)` und `Precision(t) = 1 - R_cond(t)` ergänzen die Antwortqualitätsanzeige; keine "zertifizierte 98%-Präzision" ohne eigenständige Validierung.
- Clusterabhängigkeit und Distribution-Shift können beiderlei Aussagen entwerten. Nur Cluster-Deduplizierung beweist noch keine Unabhängigkeit; OOD-Wortquote ist höchstens ein zusätzlicher Fail-Closed-Heuristikfilter.
- Keine nachträgliche Schwellenoptimierung auf `FROZEN_TEST`; wiederholte Messungen auf denselben 264 bekannten Fragen sind keine neuen Blindtests.

## 4. Priorität und Umsetzungsreihenfolge

1. **Sofort:** Node-Syntaxfehler und QF2-Kurzanker/Komposita (dieser isolierte Forschungszweig); Kotlin/JUnit, Node-Gesamtsuite und Retrieval-Präzisions-/Recall-Gegenprüfung ausstehend. Kein Release.
2. **Danach:** bereits vorhandene I1-Modell-SHA-Bindung PR #68 kompilieren und Benchmark-Regressionsprüfung; bedingtes Risiko zunächst *nur als Zusatzbericht*, ohne überzogene Zertifikatsaussage.
3. **EXP-A/C zuerst als Shadow-Beweisvalidator:** Quellspanne/Quelle/Version/Widerruf/Konflikt prüfen, ohne faktische Wahrheit zu versprechen. Exakte UTF-8-/UTF-16-Grenzen und adversariale Manipulationsfälle.
4. **EXP-B als Forschungsmodell:** 4.096 Parameter nur wenn nachgeschaltete, unabhängige neue Generalisierungstests einen Vorteil gegen n-Gram-Baseline zeigen; keine Gewichtspromotion.
5. **EXP-E Integration:** Vorhandene Coding-Agent-Kette PR #57/#58 zusammenführen, nicht duplizieren; Freigabe- und Revisionsgrenzen beibehalten.
6. **Spätere gemeinsame APK:** Baseline v0.3.12 (15), Signaturzertifikat und private SWB1/CDX1-/Wissensarchivbestände vor Überinstallation verifizieren. Kein `main`-Blindmerge.

**Freigabestand:** `SOURCE_INTEGRATED / V8_SELECTED_TESTS` für diese Quellenänderung. `JVM_TESTED`, `ANDROID_GRADLE_TESTED`, `SIGNED_APK` und `DEVICE_VERIFIED` sind ausdrücklich **nicht erreicht**.
