# Activity-Fallback: Identitätsvertrag

Status: **FOCUSED_RED_GREEN_PASSED**. Testcommit `dbdcc8fdc695ab40a8eabe194bf4660465b3e7c0`, [PR585](https://github.com/Ullmann27/device-ai/pull/585), gestapelt auf unverändertem PR582 `951a23a2c11e156f5bf98a9936c4243b17ecfda4`.

## Nachgewiesener Pfad und Grenze

Der echte `AgentActivity.runLocal`-Pfad aus `c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e` wird in Robolectric mit kontrolliertem LocalModelRuntime-Port aufgerufen. Im Fixture ist der State beim Kontext-Snapshot null; am echten optionalen Advisory-Modellrand wird eine gültige synthetische Identität bereitgestellt. Der anschließend erzeugte finale Prompt enthält nachweislich den Fallback-Auftrag und keinen Compiler-Identitätsabschnitt. Er nennt fälschlich Continuum statt der dann vorhandenen Identität Jarvis.

Das ist ein **Identitätsvertrag unter kontrolliertem Zustandswechsel**, kein beobachteter Recovery-Race oder Gerätehäufigkeitsbeleg. `AiContextCompiler.compile` ist nonnull; seine Fehler werden hier nicht abgefangen. Reguläre State-Schreibvorgänge liegen außerdem auf demselben lokalen Executor. Die gegenteilige Compiler-Recovery-Annahme aus Review discussion_r4497468810 ist durch diese Quelle nicht belegt.

## Kleinster Anschluss

Bot1 bleibt einziger Activity-Writer. Im vorhandenen `botTask == null`-Fallback soll allein das erste Argument von `LocalAssistantIdentity.renderPrompt` durch `continuumState?.archive?.identityState` ersetzt werden. Der vorhandene Renderer prüft den Identitätsintegritätshash, begrenzt Namen und erlaubt nur bekannte Profilwerte; kein ungefiltertes Gedächtnis wird angeschlossen. Kein neuer Catch, keine Recovery, keine Rechteausweitung.

## Reale RED-Abnahme

Kotlin 2.4.0 / JDK17 / JUnit4.13.2 / Robolectric4.14.1 / Android-API28; Testquelle frisch mit 256-MiB-Compiler kompiliert. Runtime offline, 1024-MiB-Heap-Obergrenze, vorhandene reale SDK-Klassen und Ressourcen. Keine Ersatz-Activity, kein nativer Modelllauf, keine Gradleausführung.

Vier Fälle: **4 Tests / 1 Fehler / Exit 1**. Exakter Fehler: erwartet `assistant_name="Jarvis"`, tatsächlich `assistant_name="Continuum"`. Bereits grün: Standardidentität bei dauerhaft fehlendem State, Ownername-/Profilausschluss im tatsächlich eingereichten LOCAL_TOOLS-Botauftrag sowie echter `MANDATORY_CONTEXT_EXCEEDS_BUDGET` ohne irgendeinen Modellaufruf.

Der positive Test verlangt zusätzlich erlaubte Profilwerte, genaue geladene Modell-ID/Version/SHA und Ausschluss synthetischer Archiv-Memory-/Profil-Payloads. Diese nachfolgenden Assertions wurden im GREEN gemeinsam bestanden.

## Weiterarbeit

Die identische bereits kompilierte Testklasse bestand **GREEN: 4 Tests / 0 Fehler / Exit 0** in 3,514 Sekunden. Keine Teständerung zwischen dem endgültigen RED und GREEN. Bot1s `final-activity-classes` stand vor den echten Basis-SDK-Klassen. AgentActivity-Quellhash unabhängig gelesen: `b3551528e76061191a29d2cbaf9be7ff9d183dea9d3757e3c22ab7091bd25e38`. Der passende SDK-Compilerbeleg, sämtliche Overlay-Klassenhashes und unveränderte Test-Klassenhashes sind gesichert.

Das Overlay enthält außerdem Bot1s andere autorisierte Routingkorrekturen und Roots PDF-Quellen; deren exakte Quellhashes stehen im übernommenen Compilerbeleg. Dies ist kein isolierter Gesamtbuild dieser Test-PR. PR585 selbst ändert keine Activity und muss mit Bot1s Produktionsanschluss gemeinsam integriert werden. Root führt anschließend die vollständige aktuelle SDK-Abnahme aus.

`run-ui.py` enthält den ausgeführten Hostaufruf mit absoluten Originalpfaden; Commands/Exitcodes und Source-/Klassenhashes liegen daneben. Die Roh-GREEN-Ausgabe und der finale Receipt sind dauerhaft enthalten.

Vor dem gültigen RED wurden zwei reine Fixturefehler korrigiert: fehlende abstrakte unload-Methode und ein Text, der VERIFY_ONLY statt Advisory auslöste. Beide sind keine Appfehler und nicht das hier berichtete RED.

EXP-02/EXP-07; bestehende Owner-/Bot-/Modell-/Privacy-Grenzen erhalten. Produktquellen in diesem Testzweig unverändert. Rückfall: nur den neuen Test und seine Belege entfernen; PR582 und fremde Activity-Arbeit erhalten.
