# Operation Autonomy – ausführbarer erster Abschnitt

> Ausführung: dieselbe KI implementiert; anschließend frischer unabhängiger
> Code-Review. Auftrag/Spec: `SOL-MAX-AUTONOMY-HANDOFF-20261103.md` und der
> Besitzerauftrag vom 03.11.2026. Keine erneute Planfreigabe erforderlich: Der
> Besitzer hat diese Forschungs- und Reparaturrunde ausdrücklich autorisiert.

**Ziel:** Den vorhandenen Fold7-Modellpfad von belegten Übergabefehlern befreien
und reale Geräteprüfungen am bestehenden Gehirn reproduzierbar vorbereiten.

**Architektur:** AgentActivity bleibt Aufrufer von BoundedAgent, Kontextcompiler,
ToolGate und LocalModelRuntime. Der gemeinsame LocalToolContinuation-Helfer
entscheidet nur über eine vorhandene frische Eingabe oder Toolfortsetzung;
keine zusätzlichen Rechte, Modelle, Speicher oder Worker.

**Technik:** Kotlin/JUnit mit echter Android-Gradle-Kompilierung, Node 22,
vorhandenes ADB/SDK. Native Modellinferenz ausschließlich mit tatsächlichen
Gewichten und erreichbarem Gerät; Testadapter bleiben als simuliert markiert.

## Globale Grenzen

- Eigenbranch/Draft #362 auf #361; kein main-Merge, Signieren oder Installieren.
- Vollständige Pflichtinformationen erhalten; zu große echte Toolfortsetzungen
  weiterhin vollständig abweisen, keine Grenzwerterhöhung oder Abschneidung.
- Bildschirm-/Web-/Dateidaten bleiben unvertrauenswürdige JSON-Feldwerte.
- Keine automatische Wiederholung von UNKNOWN, keine Gewichtspromotion.
- Reale Fold7-Meilensteine bleiben NOT_RUN bis zu tatsächlichem Zugriff.

## Gezielte Prüfrisiken

1. Bildschirmbeobachtung passt als volle JSON-Eingabe, überschreitet aber das
   ungenutzte native Toolantwortformat: frischer Turn muss starten.
2. Dieselbe Beobachtung bei echter Toolfortsetzung: weiterhin vor Engine-Aufruf
   verweigern, kein stiller Wechsel in ein weniger strenges Format.
3. Kleine Toolantwort mit später mehrsprachiger Verneinung: vollständig und genau
   einmal im vorgesehenen nativen Toolkanal übergeben.
4. Keine neue Toolbeobachtung: vollständigen vorhandenen Prompt streamen.
5. Widerruf/STOP/UNKNOWN: bestehende Sperren dürfen durch die Übergabe nicht
   umgangen werden; Regressionen im Gesamtpaket ausführen.

## Aufgabe 1: Bildschirm-/Toolübergabe minimal reparieren

Dateien: `localmodel/LocalToolContinuation.kt`, tatsächlicher Aufrufer
`AgentActivity.kt`, `localmodel/LocalToolContinuationTest.kt`.

Schnittstelle: `submit(runtime: LocalModelRuntime, prompt: String,
forceFresh: Boolean, responses: () -> List<LocalToolResponse>,
callback: LocalInferenceCallback)` im bestehenden Helfer.

- [ ] Vorhandene Auswahl ohne Verhaltensänderung in den Helfer ziehen, damit
  der reale Produktionszweig getestet wird; übergroße Beobachtung reproduzieren.
- [ ] RED: neue Hosttests zeigen den unnötigen Kodierungsabbruch im frischen
  Bildschirm-Turn; Log, Diff und Ausgangs-SHA sichern.
- [ ] GREEN: bei `forceFresh` unmittelbar den bereits vollständig geprüften
  Prompt verwenden und die ungenutzte Toolantwort nicht erst kodieren.
- [ ] Positive/negative Kontrollen wie oben ausführen; dieselbe Aktivität muss
  den Helfer tatsächlich aufrufen. Keine native Inferenz behaupten.
- [ ] Relevante Gradle-/Node-Suiten, Rohlogs, exakten getesteten SHA und Grenzen
  dokumentieren; committen und pushen.

## Aufgabe 2: Geräteabnahme und CCC1-Einstieg am echten Pfad

Dateien: bestehendes Fold7-Protokoll als historischen Beleg erhalten; neues
`docs/research/omega/SOL-MAX-FOLD7-AUTONOMY-ACCEPTANCE-20261103.md`.

- [ ] Modellkatalog, LiteRT und optionalen GGUF/Termux-Pfad anhand echter
  Aufrufer prüfen; Zugriff, Gewichte und installierte Version nicht erraten.
- [ ] Bestehende In-App-Modelltest-, Chat-, ToolGate- und CCC1-Einstiege mit
  konkreten PASS/FAIL- und NOT_RUN-Kriterien angeben.
- [ ] Falls Gerät erreichbar wird: Besitzer-/Betriebssystemrechte einhalten,
  reale Antworten und Quittungen erfassen, anschließend erst CCC1-Feuerprobe.
- [ ] Ohne Gerät keine Ersatzdemo bauen; die offenen Geräteabnahmen und den
  präzisen nächsten Besitzer-/KI-Schritt dokumentieren.

## Aufgabe 3: Freigabefähige Forschungsübergabe

- [ ] Vollständige relevante Hostprüfungen auf eingefrorenem Quellstand ausführen.
- [ ] Frischen unabhängigen Review anfordern, berechtigte Befunde mit RED→GREEN
  beheben, offene Befunde ausdrücklich erhalten.
- [ ] Handoff, Rohlogs, Commitlinks und Draft-PR aktualisieren und pushen.
- [ ] Abschluss trennt SOURCE_INTEGRATED, HOST_VERIFIED, DEVICE_VERIFIED,
  RESEARCH_SHADOW und NOT_RUN; keine pauschale Fortschrittszahl.
