# Omega: Logik und Android-Routing · 11.11.2026

## Auftrag und Basis

- Teilauftrag P2 aus `docs/handoff/OMEGA-WEITERBEARBEITUNG-20261111.md`: #767 / #759 / #765.
- Basis PR #768: `3cb8df27c42fbc6db03d05b0f9993a8650be43af`.
- Agent-B-Reproduktion `231b8bad207b6571f4b43d9b6bc8bb25ff0113e1` unverändert übernommen. Lokaler Commit `b1e9d59fff0d0e9cf2afee9c46da573169c6addc`, veröffentlicht als `4c79229e20bd3e9a7ad432f3bf129d539a417372`; identischer Tree `8d80543414ccf30e42fc8a37c95ba9feaf253fe6`.
- Branch `work/omega-logic-20261111`, Draft #772 gegen `integrate/omega-apk54-20261111` / #769. Kein Merge nach main, keine Signierung oder Installation.

## Belegte Ursachen

1. BootstrapBrain behandelte jedes Änderungsverb als ausgeführten Geräteauftrag, auch Negation, Vorschlag oder ausdrücklich spätere Eigentümerfreigabe. Die gemeinsame alte Antwort vermischte Rollenmanipulation und Geräteänderung.
2. Systemdiagnose erkannte nur wenige feste Formulierungen. Natürliche Analyseaufträge konnten zum Modellpfad fallen. „Cage“ war kein eng begrenztes Cache-Vorschaukommando.
3. App-Erkennung kannte keine allgemeine Verb-Ende-Form. Eine Antwort nur mit dem App-Namen nach einer präzisen Rückfrage hatte keinen begrenzten Rückfragezustand.

## Stand der Reparatur

- Deterministische gemeinsame Klassifikation für Lesen, Vorschlag, spätere Freigabe, verneinte Änderung, echte Änderung und Rollenmanipulation. Diese Klassifikation erteilt keine Ausführungsfreigabe.
- Unterschiedliche, sachliche Ablehnungsgründe; ein Änderungsauftrag erhält den Hinweis auf separate Bestätigung und den lesenden Diagnosepfad.
- Sprachdiagnose wird dem vorhandenen `diagnose_device` zugeordnet. Der bestehende `ToolGate.actualStart` erzeugt die sichtbare Arbeitsbühne über Lease und `presentToolScene`; kein zweiter Renderer und kein vorgetäuschter Toolstart.
- Cache-/Cage-Reinigungssprache wird nur im eindeutigen Kontext als `preview_own_cache` erkannt; Bereinigung bleibt separat freizugeben.
- Verb-Ende-Form führt in die bestehende App-Suche. Treffer und Start benötigen weiterhin den vorhandenen Zustimmungsfluss. UNKNOWN wird nicht wiederholt.
- App-Namensrückfrage: einmaliger Zustand aus Sitzung, Eigentümer-Eingaberevision und ursprünglicher Öffnen/Suchen-Absicht. Nur eine passende Namensantwort setzt die bestehende App-Suche fort. STOP, Pause, Sitzungs-/Botwechsel und jede andere akzeptierte Eingabe löschen den Zustand. Ein fehlender expliziter App-Treffer fragt ebenfalls nach genau einem korrigierten Namen. Die Antwort erteilt keine Startfreigabe.
- Die Namensantwort wird erst am vorhandenen App-Routingpunkt ausgeführt. Systemdiagnose, Vorschau, interne Navigation, Worker- und Webbefehle behalten Priorität. Eine adressierte Antwort „Jarvis, WhatsApp“ wird nicht durch Bot-Selektion verschluckt.
- Der Rollencheck steht vor dem Korrektur-Schnellpfad und der Modell-/Direktroute. Bereits extrahierte Bot-Dialogtexte und die vorhandene LOCAL_TOOLS-Teamauftragsaufnahme verwenden denselben Check.
- Unabhängiges Review fand zunächst drei weitere Fehlergruppen: Objektnegation statt Handlungsnegation, adressierte Rollenheaders und Fremdsubjekte/Fragen in der Verb-Ende-Form. Diese sind durch eigene RED-Belege und anschließend grüne Regressionen gesichert. Der zweite Review-Pass fand die Korrektur-/Navigationspriorität; auch diese wurde angepasst.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis | Beleg |
| --- | --- | --- |
| Originale Agent-B-Reproduktion vor Reparatur | 13 Fälle, 9 FAIL, 4 PASS | `evidence/logic/baseline-red/` |
| Erste Reparatur mit benachbarten Verträgen | 87 Fälle, 87 PASS | `evidence/logic/guards-green3/` |
| Review-Holdouts vor Korrektur | 10 Fälle, 3 FAIL, 7 PASS | `evidence/logic/holdouts-red/` |
| Endgültige reine Regression | 94 Fälle, 94 PASS | `evidence/logic/final-regression/` |
| Vollständiger Android-/Activity-Lauf | NOT_RUN durch diesen Agenten | Nur nach Abstimmung; gemeinsames Gradle-Lock |
| Fold7 / echte Android-Laufzeit | NOT_RUN | Keine Gerätebeobachtung behauptet |

Die reinen Kotlin/JUnit-Läufe kompilieren die geänderten Produktionsquellen und Tests mit Kotlin 2.4.0 gegen vorhandene echte Produktionsklassen aus dem Workspace. Kein Parser-Mock und keine Geräteausführung. `inputs.json` hält Quell-Hashes und Testklassen fest. Das ersetzt die gemeinsame Android-Neukompilierung und Robolectric-Prüfung nicht. Maximal ein aktiver Prozessor, 512 MiB Heap.

## Grenzen und Integration

Der Integrator besitzt Gesamtregister und APK-Gates. Änderungen in AgentActivity bleiben auf Routing/App-Rückfrage und Lifecycle-Invalidierung begrenzt; der Gedächtnis-Agent besitzt OBJECT_STATE_RECALL und den lokalen Promptaufbau. Testbehauptungen werden nicht abgeschwächt; der alte Ablehnungstextvertrag wird in Rollen- und Änderungsgrund aufgeteilt und um Kein-Tool-/Kein-Recall-Assertions ergänzt.

## Übergabe an die gemeinsame Android-Prüfung

Geänderte Produktionsquellen: `BootstrapBrain`, `OwnerDeviceRequestSafety`, `SystemIntelligenceCommands` in `SystemDiagnosis.kt`, `OwnerAppLookupIntent`, `OwnerAppClarification`, eng begrenzte Activity-Routing-/Lifecycle-Blöcke.

Die 16 reinen Testklassen stehen vollständig in `evidence/logic/final-regression/inputs.json`. Besonders neu: `BootstrapBrainOwnerIntentSafetyTest`, `SpokenDiagnosisRoutingTest`, `OwnerAppOpenRoutingTest`, `OwnerDeviceRequestSafetyTest`, `OwnerAppClarificationTest`. Der alte Fold7-Recall-Ablehnungstextvertrag wurde präzisiert; seine fachlichen Assertions bleiben erhalten.

Folgende **Activity-Testquellen wurden ergänzt, hier aber nicht ausgeführt**:

- `SystemIntelligenceUiTest`: kurze Sprachanalyse erreicht normale Android-Probe und vorhandenes Arbeitsfenster aus CHAT; Inferenzaufrufe werden gezählt und müssen null bleiben. Cage-Satz erzeugt nur eigene Cache-Vorschau; vorher angelegter technischer Bericht bleibt erhalten, kein Cleanup-Ergebnis.
- `OwnerAppActionWorkSceneUiTest`: „Öffne eine App“ → Name → Treffer → ausdrückliche Startbestätigung; korrigierter Name nach leerer Suche; einmaliger Verbrauch und Invalidierung durch STOP/interne Navigation; Systemdiagnose behält Vorrang vor einer offenen Namensfrage.
- `OwnerAppPlanningContextUiTest`: direkter/adressierter Rollenheader und Korrekturpräfix dürfen das bereite Modell, App-/Systemwerkzeuge oder Dokumentauswahl nicht erreichen. Für den adressierten Fall wird ein vorhandener Bot aufgelöst; vor jedem Fall wird die alte sichtbare Antwort entfernt.

Benachbarte bestehende Klassen mit ausführen: `SystemIntelligenceUiTest`, `OwnerAppActionWorkSceneUiTest`, `OwnerAppPlanningContextUiTest` sowie die 16 reinen Klassen aus dem Manifest. Echte Fold7-Laufzeit, Android-ICU und APK-Abnahme bleiben separate Gates. Neue Aktionsverbgrenzen verwenden explizite Unicode-Klassen; eine reine JVM-Prüfung wird nicht als Geräteevidenz dargestellt.
