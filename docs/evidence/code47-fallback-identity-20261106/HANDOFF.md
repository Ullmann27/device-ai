# Activity-Fallback: Identitätsvertrag

Status: **RED_REPRODUCED; GREEN_PENDING**. Testcommit `dbdcc8fdc695ab40a8eabe194bf4660465b3e7c0`, [PR585](https://github.com/Ullmann27/device-ai/pull/585), gestapelt auf unverändertem PR582 `951a23a2c11e156f5bf98a9936c4243b17ecfda4`.

## Nachgewiesener Pfad und Grenze

Der echte `AgentActivity.runLocal`-Pfad aus `c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e` wird in Robolectric mit kontrolliertem LocalModelRuntime-Port aufgerufen. Im Fixture ist der State beim Kontext-Snapshot null; am echten optionalen Advisory-Modellrand wird eine gültige synthetische Identität bereitgestellt. Der anschließend erzeugte finale Prompt enthält nachweislich den Fallback-Auftrag und keinen Compiler-Identitätsabschnitt. Er nennt fälschlich Continuum statt der dann vorhandenen Identität Jarvis.

Das ist ein **Identitätsvertrag unter kontrolliertem Zustandswechsel**, kein beobachteter Recovery-Race oder Gerätehäufigkeitsbeleg. `AiContextCompiler.compile` ist nonnull; seine Fehler werden hier nicht abgefangen. Reguläre State-Schreibvorgänge liegen außerdem auf demselben lokalen Executor. Die gegenteilige Compiler-Recovery-Annahme aus Review discussion_r4497468810 ist durch diese Quelle nicht belegt.

## Kleinster Anschluss

Bot1 bleibt einziger Activity-Writer. Im vorhandenen `botTask == null`-Fallback soll allein das erste Argument von `LocalAssistantIdentity.renderPrompt` durch `continuumState?.archive?.identityState` ersetzt werden. Der vorhandene Renderer prüft den Identitätsintegritätshash, begrenzt Namen und erlaubt nur bekannte Profilwerte; kein ungefiltertes Gedächtnis wird angeschlossen. Kein neuer Catch, keine Recovery, keine Rechteausweitung.

## Reale RED-Abnahme

Kotlin 2.4.0 / JDK17 / JUnit4.13.2 / Robolectric4.14.1 / Android-API28; Testquelle frisch mit 256-MiB-Compiler kompiliert. Runtime offline, 1024-MiB-Heap-Obergrenze, vorhandene reale SDK-Klassen und Ressourcen. Keine Ersatz-Activity, kein nativer Modelllauf, keine Gradleausführung.

Vier Fälle: **4 Tests / 1 Fehler / Exit 1**. Exakter Fehler: erwartet `assistant_name="Jarvis"`, tatsächlich `assistant_name="Continuum"`. Bereits grün: Standardidentität bei dauerhaft fehlendem State, Ownername-/Profilausschluss im tatsächlich eingereichten LOCAL_TOOLS-Botauftrag sowie echter `MANDATORY_CONTEXT_EXCEEDS_BUDGET` ohne irgendeinen Modellaufruf.

Der positive Test verlangt zusätzlich erlaubte Profilwerte, genaue geladene Modell-ID/Version/SHA und Ausschluss synthetischer Archiv-Memory-/Profil-Payloads. Diese nachfolgenden Assertions werden erst beim GREEN gemeinsam abgenommen.

## Weiterarbeit

Die identische bereits kompilierte Testklasse gegen Bot1s tatsächlich neu kompilierte Activity ausführen; den Overlaypfad vor die Basis-Produktionsklassen setzen. Bot1s Quellhash und Klassenhashes archivieren. `run-ui.py` enthält den ausgeführten Hostaufruf mit absoluten Originalpfaden; Commands/Exitcodes und Source-/Klassenhashes liegen daneben. Danach Receipt aktualisieren und Roh-GREEN dauerhaft sichern.

Vor dem gültigen RED wurden zwei reine Fixturefehler korrigiert: fehlende abstrakte unload-Methode und ein Text, der VERIFY_ONLY statt Advisory auslöste. Beide sind keine Appfehler und nicht das hier berichtete RED.

EXP-02/EXP-07; bestehende Owner-/Bot-/Modell-/Privacy-Grenzen erhalten. Produktquellen in diesem Testzweig unverändert. Rückfall: nur den neuen Test und seine Belege entfernen; PR582 und fremde Activity-Arbeit erhalten.
