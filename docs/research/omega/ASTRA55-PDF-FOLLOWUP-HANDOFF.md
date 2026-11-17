# Astra Code55: PDF-Folgefragen und Einzeltreffer

Basis: `a99eba365d`. Integrator: Astra, PR #799, Master-Feldfehler #670.
Bestehender Gesprächspfad im Umfeld EXP-05; keine neue Modell- oder Speicherarchitektur.

## Änderung

`reuseOpenSummary` unterscheidet jetzt die Dokumentquelle von der gewünschten Ausgabe.
„Lies die PDF und erstelle eine neue Zusammenfassung“ verwendet die offene PDF.
„Fasse eine neue PDF zusammen“ verlangt weiterhin die Dateiauswahl.
Explizite andere Dateien, Dateinamen, Negationen und zusätzliche Geräteaktionen
werden nicht als Zusammenfassung des offenen Dokuments übernommen.

Die bestehenden App-Flow-Tests erwarten bei einem tatsächlich einzelnen Treffer
direkt die konkrete Bestätigung. Sie prüfen weiterhin echte ToolGate-Ablehnung,
keinen Backend-Aufruf vor Freigabe und einen frischen passenden Fensterbeleg.
Zusätzliche Tests prüfen abgeschnittene Mehrfachtreffer, implizite unscharfe Wünsche,
falsche Pakete und wiederverwendete Beobachtungen.

Fünf zusätzliche Activity-Tests prüfen offene PDF, neue Zusammenfassung derselben
PDF, neue PDF, fehlende offene PDF und fehlendes lokales Modell. Sie verwenden die
bestehende kontrollierte Modell-Laufzeit und synthetische bereits gelesene Seiten.
Sie sind keine PdfRenderer-, Modellqualitäts- oder Geräteprüfung.

## Nachweise

- `OwnerDeviceClockReplyTest`: 6/6 bestanden mit echtem Produktionscode, Kotlin/JVM und JUnit.
- `OwnerOpenDocumentSummaryTest`: 4/4 bestanden mit echtem Produktionscode und seinen echten Abhängigkeiten.
- `OwnerAppSingleCandidateTest`, geänderte `OwnerAppActionFlowTest` und fünf neue
  `OwnerPdfDialogueFollowUpUiTest`: hier noch nicht ausgeführt; kombinierte Gradle-Prüfung beim Integrator.
- Keine Gradle-Ausführung parallel zur Integrationsprüfung; keine Geräteprüfung.

Status: PORTIERT im lokalen Folgebranch; VERIFIZIERT nur für die genannten zehn
JVM-Prüffälle; GERÄTEBESTÄTIGT: nein. Veröffentlichung durch den Integrator.

## Offene Persistenzlücke

Nach Wegwischen bleibt die Dokumentreferenz nicht erhalten. Der bestehende
`LocalConversationContext` hält sie im RAM, die Activity sichert sie nur im Bundle.
`LocalChatTranscriptStore` speichert den aktiven Chat, aber keine Dokumentreferenz.
Das Arbeitsjournal kennt URI und Dokumentmetadaten, jedoch keine Bindung an den Chat.

Eine vollständige Reparatur benötigt Änderungen an Activity, TranscriptStore und
Context. Dafür wurde keine unverbundene zweite Ablage angelegt. Minimaler Vorschlag:
einen begrenzten Dokumentreferenz-Datensatz im vorhandenen TranscriptStore an denselben
aktiven Chat binden, dessen Datensatztyp beim Laden ausdrücklich behandeln, gespeicherte
URI-Freigabe erneut prüfen und die PDF erst bei einer neuen Besitzerfrage erneut lesen.
Widerrufene oder nicht lesbare Quellen müssen die Referenz verwerfen und erneut nach der
Datei fragen. Das erfordert eigene Wiederanlauf-, Chatwechsel- und Widerruftests.

## Integration und Rücknahme

Den getrennten Folgecommit in die Code55-Integrationslinie übernehmen und die genannten
Testklassen in der kombinierten Suite ausführen. Bei Routing-Regression lässt sich dieser
Commit zurücknehmen; Paket, Signatur, Originalassets und Speicherformat sind unverändert.
