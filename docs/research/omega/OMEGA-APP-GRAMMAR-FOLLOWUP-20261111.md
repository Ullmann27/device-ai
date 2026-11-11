# App-Grammatik – Review-Fortsetzung 11.11.2026

Status: **WIP / UNVERIFIED**. Produktionscode noch unverändert; Regressionsfälle vorbereitet.

- Auftrag: PR #769, Review `discussion_r4539999051`.
- Basis: `c36258a853bbc6dacd8fbfa0dbae64f0f95a0d14`.
- Zweig: `work/omega-app-grammar-followup-20261111`.
- Integration: `/root`, Ziel `integrate/omega-apk54-20261111` / #769.

## Ursache und kleinster Reparaturpfad

`OwnerAppLookupIntent.verbFinal` erkennt gewöhnliche Aussagen wie „Wir starten“,
„Lass uns jetzt starten“ und „Morgen wollen wir das Projekt starten“ als App-Ziel.
Die Ausschlüsse berücksichtigen Plural-/Aufforderungsformen unvollständig. Für
verbleibende mehrdeutige Namensformen fehlt außerdem `requireInstalledCandidate`.

`AgentActivity.runExplicitAppLookup` hat bereits den passenden Pfad: Nur eine
verifizierte Suche ohne exakten installierten Treffer kehrt zum Gespräch zurück.
Dieser Pfad liegt vor der App-Namensrückfrage. Kein zweiter Router erforderlich.

Geplant: belegte Nicht-Aufträge ausschließen und ausschließlich den tatsächlich
gewählten Verb-am-Ende-Pfad an vorhandene exakte Installationsevidenz binden.
Direkte/polite Öffnungsaufträge, Werkzeugfreigabe und UNKNOWN-Sperre bleiben erhalten.
Eine bestehende Namensrückfrage muss bei einem neuen gewöhnlichen Satz verfallen.

## Prüfungen

- Zehn neue reine Kotlin/JUnit-Fälle vorbereitet: **NOT_RUN**.
- Drei echte Activity-Regressionen vorbereitet: **NOT_RUN**, kein eigener Gradle.
- Pure Kotlin/Node ausschließlich unter `/tmp/continuum-omega-gradle.lock` nach
  dem gemeinsamen Node-Lauf. RED vor Produktionsänderung, danach GREEN plus Nachbarn.
- Keine Geräte-, Signier-, Installations- oder APK-Abnahme.

## Nächster Schritt / Rückfall

Unter freiem Lock RED mit unveränderter Produktion erfassen; kleine Reparatur,
gezielte Nachbarklassen, genaue Quellen und Ergebnisse sichern. Danach Integration
durch `/root` und gemeinsame Android-Abnahme. Bei Rückfall nur diesen isolierten
Patch zurücknehmen; keine Sicherheits- oder Testgrenze lockern.
