# UI-Fixture: geposteter Klick und Gedächtnisdialog – 11.11.2026

**Status: konkrete RED-Befunde und Quellenreview belegt; neuer Android-Lauf NOT_RUN.**

Basis ist `c163641b400f1fcdd02c1d2952fde1c8e76a0ad1`. Eigener Worktree und Branch:
`omega-ui-click-fixture` / `work/omega-ui-click-fixture-20261111`. Root-Quelle und
laufende Prüfungen wurden nicht verändert. Nur zwei UI-Testdateien werden korrigiert.

## Chat-Touch: Callback erst auf dem UI-Looper

Der gemeinsame Lauf `1b8747f8-3a67-44c1-9be3-1f920a79c520` meldet bei allen neun
Planning-Fällen die neue Assertion `CHAT` gegen `START` direkt nach dem Touch.
Der echte Pfad ist `ShellActionView` → `openJarvisTarget` →
`navigateDashboardTargetNow` → `JarvisMenuNavigator` → `LABEL_THINKING` →
`navigateWorkArea(CHAT)` → `focusChatComposer`. Er setzt ausdrücklich `CHAT` und
`conversationActive=true`; ein dauerhaftes START-Overlay ist hier nicht vorgesehen.

Die zeitliche Annahme der vorherigen Fixturekorrektur war falsch:
`JarvisHomeTestRoutes.touch` sendet native DOWN/UP-Ereignisse. Der SDK28-Androidpfad
`View.onTouchEvent` postet danach `mPerformClick`; nur wenn das Posting fehlschlägt,
ruft er `performClickInternal` synchron auf. Ein angenommenes Touch-Ereignis beweist
daher noch keinen ausgeführten Listener. Der Test verwendet einen pausierten Looper.

Die Fixture wartet jetzt unmittelbar nach dem echten Touch mit ihrem vorhandenen
`until` auf beide Zustände. Die bisherige Zehn-Sekunden-Frist sowie die anschließenden
strikten Zustands- und fachlichen Assertions bleiben erhalten. Kein künstlicher
Zustandswechsel und kein direkter Aufruf privater Navigation ersetzt den Touch.

## Namensrückfrage: legitimen Dialog vor Folgeeingabe schließen

Der STOP-/Navigationsfall scheitert nun erst an der letzten globalen Dialogprüfung.
Alle vorherigen Generation-/Flow-/Active-/Launch-Assertions wurden erreicht.
Das Originaljournal benennt weder Schleifeniteration noch Dialogidentität.

Der Produktionspfad der Navigationsiteration erzeugt bewusst einen Dialog:
`sendCurrentInput("Gedächtniszentrum")` → `addUserMessage` (CHAT) →
`showMemoryCenter` → `showCenter` → `ContinuumBentoCockpit.show`.
Der Home-Arbeitsfensterpfad setzt START voraus und greift hier nicht. `centerDialog`
hält deshalb den legitimen Gedächtnisdialog, der ohne Schließen sichtbar bleiben darf.

Die Fixture verifiziert nun erst eine tatsächlich scharfe App-Namensrückfrage und
liest danach rein reflektiv `pending == null` — direkt nach STOP/Navigation und
**vor jedem Dialogschließen**. In der Navigationsiteration verlangt sie zusätzlich
die konkrete `centerDialog`-Identität, denselben neuesten Dialog und den sichtbaren
Text `Gedächtniszentrum`. Sie schließt genau diesen Dialog über dessen vorhandenen
`SCHLIESSEN`-Knopf und wartet mit der unveränderten Fünf-Sekunden-Frist auf Schließung.

Erst danach folgt „Signal“. Alle ursprünglichen Prüfungen bleiben: richtiger
Invalidierungsschritt, kein weiterer Lookup, kein Flow, Lookup inaktiv, null Launches
und global kein sichtbarer Dialog. Der Dialogfehler nennt nun die Schleifenphase.
Der nächste echte Lauf muss diese konkreten Zustands-/Identitätsbehauptungen bestätigen.

## Belege und Gates

`docs/evidence/omega-ui-click-fixture-20261111/` enthält die zehn unveränderten
Fehlerdatensätze dieses Arbeitspakets, Quellen-/Testhashes, den kurzen relevanten
PerformClick-Bytecodeausschnitt sowie JAR- und Klassen-SHA256. Das vollständige
Android-Disassembly bleibt außerhalb des Repositories. Die Analyse verwendete
ausschließlich `javap`, keinen Test- oder Compilerlauf.

Unabhängiger Quellenreview durch `/root/extensions`: OK für Planning-SHA256
`f11e2056f674b40e5b619bb8b1104d77fe6e936a070f550ff1e7ec7ea56195c0` und STOP-SHA256
`8e8f5b78125bdeb242df151aa6568bd24fa292b040b866f8d5f178346b8eb790`.
Der Reviewer bestätigte auch den View-Klassenhash, ohne Tests auszuführen.

Keine Produktion, Timeouts oder fachlichen Guards geändert. Memory korrigiert den
gleichen geposteten Einstieg separat. Der Integrator prüft danach wieder alle drei
vollständigen UI-Klassen und anschließend seinen vollständigen Scope. Hier weiterhin
**NOT_RUN**. MCP bleibt pausiert.
