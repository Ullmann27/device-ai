# Continuum – erster Samsung-Recherchetest: sichtbare Fehler und Hotfix

Gerätebeobachtung vom 19.10.2026 (private Screenshots im Entwicklungschat):

- Android-Preview startet; Start-/Chat-Hologramm und Teamleiterstatus sind sichtbar.
- Die Frage `Wie funktionieren Solarzellen?` führte zu Quellen über das
  Fragewort `wie` (DWDS/Duden) und fachfremden URLs; die modellfreie
  Synthese gab diese Ausschnitte irreführend als "Evidence-based summary" aus.
- Einige große Seiten verursachten `Research response is too large.`.
- Der lokale LiteRT-LM-Dialog war nicht geladen; experimentelles SWB1-Training
  und Web-Quellenhinweise sind kein Nachweis eines allgemein sprachfähigen LLM.

## Reparaturumfang

1. `ResearchQuestionFocus.searchText` entfernt häufige Frage-/Füllwörter
   **nur für öffentliche Websuche**. Fragen nach Solarzellen werden damit
   unter `solarzellen` gesucht; unaufgeforderte Geräteaktionen bleiben
   durch den bestehenden ToolGate geschützt.
2. `GeneralWebDiscoveryProvider` übernimmt bei echten Frageformulierungen
   nur Suchergebnisse mit einem fachlich passenden Titel. Gibt es nur
   Wörterbuch-/Login-Treffer, wird mit dem bereinigten Thema der vorhandene
   Wikipedia-Fallback versucht. Direkte Benutzer-URLs und einfache
   Stichwortsuchen behalten ihren bisherigen Pfad.
3. Der `DeepResearchEngine` behandelt einen übergroßen oder temporär
   ausgefallenen **Follow-up-Quellenabruf** als unvollständigen Quellentreffer,
   setzt aber den übrigen Bericht fort. Widerruf, unsichere URL,
   Unterbrechung und nicht eingeordnete Sicherheitsfehler bleiben Fehler.
4. Ohne einsatzbereites Modell liefert die deterministische Ausgabe nur
   eine kurze Auswahl thematisch passender, ausdrücklich **ungeprüfter**
   Originalaussagen. Bei keinem passenden Treffer sagt sie dies klar,
   statt irrelevante Texte als Begründung auszugeben.

## Regressionskriterien

- `GeneralWebDiscoveryProviderTest.solarQuestionFiltersDictionaryNoiseAndSearchesTheRealTopic`
- `GeneralWebDiscoveryProviderTest.irrelevantSearchHitsCauseTopicFocusedWikipediaFallback`
- `ResearchEngineTest.oversizedFollowUpPageDoesNotDiscardCompletedResearch`
- `ResearchEngineTest.modelFreeSynthesisRefusesUnrelatedDictionaryEvidence`

Auf einem Checkout mit Android SDK/JDK17:
```bash
gradle :app:testDebugUnitTest --tests at.heinz.deviceai.cognitive.GeneralWebDiscoveryProviderTest --tests at.heinz.deviceai.cognitive.ResearchEngineTest
gradle :app:lintDebug
gradle :app:assembleDebug -PdeviceAiPreview=true
```

**Noch nicht ausgeführt:** vollständige Gradle-Tests, neue integrierte APK,
sowie der erneute Test auf dem Samsung. Die isolierte `kotlinc`-Prüfung
des Suchfokus erreichte sechs erfolgreiche Assertions. Ein semantisch
verwandtes Dokument ohne gemeinsames Stichwort kann von dieser
konservativen Filterung fälschlich ausgeschlossen werden; das muss
auf dem Gerät geprüft werden.

Für die Wiederholung auf dem Telefon bei der bisher gespeicherten,
falschen Solarzellenrecherche `NEU STARTEN` wählen (nicht `FORTSETZEN`),
damit alte unpassende Dossierquellen nicht erneut angezeigt werden.
Gespeicherte persönliche Lerninhalte oder alte Dossiers werden durch
diesen Hotfix **nicht automatisch gelöscht oder überschrieben**.

Die Preview-Version auf dem Telefon ist Sols älterer APK-Stand.
Dieser Hotfix ist zunächst nur Quellcode in einem separaten Draft-PR,
aufbauend auf dem Integrations-PR #37.
