# Continuum Max – drei Kontrollen und vollständige reale SDK-Prüfung

**Gemeinsamer geprüfter Quell-/Releasekandidat: ec28d661e27090c91a3eb83707bf5f46376b97b5.**
Start: Opus d0f0892e156b9bcfa6a9a80d6fcd515173a62565. Reiner Dokumentations-/Belegabschluss folgt danach; keine stillschweigende neue Codefreigabe.

## Ergebnis

| Tatsächlich ausgeführt auf ec28d661 | Ergebnis |
|---|---|
| Alle .github/continuum und scripts Node-Tests |1099/1099,0 Fehler/Skips |
| Zusätzliche Node-Standard-Discovery |252/252,0 Fehler/Skips |
| Backend + Syntaxchecks |60/60,0 Fehler/Skips |
| Groq-Verträge / Workshop / Autogenesis |26/26 /34/34 /30/30,0 Fehler/Skips |
| Kotlin/Javac/Android-Gradle, alle SDK-JUnit |940/940 in189 Klassen,0 Fehler/Skips |
| Enthaltene echte Robolectric-Widgettests |26/26; kein physischer Gerätetest |
| Android lintDebug |0 Befunde im vorhandenen NewApi-Profil; kein Claim aller Lint-Regeln |
| assembleDebug mit deviceAiPreview=true |BUILD SUCCESSFUL4m29s,58 Tasks |
| Vier vorhandene Checks, SWB2-/CRE1-Parität |alle Exit0 |

Die Teilmengen/Discovery überschneiden sich; Zahlen nicht als disjunkte Gesamtsumme addieren.
[summary.json](final-ec28d661/summary.json), vollständige TAP-/SDK-/Check-Ausgaben und25 `*.identity.json` liegen unter `final-ec28d661/`.
Jede Jobidentität bestätigt HEAD vor/nach, keine getrackten Änderungen, exakte Argumentliste, Laufdauer und Exit0.
189 originale JUnit-XML im Archiv `android-junit-xml.tar.gz`, originales Lint-XML daneben. JDK17/Kotlin2.4/Gradle8.11.1/AGP8.10/SDK36, echte Android- und LiteRT-Abhängigkeiten, keine Kompilierstubs.
Erster Dependency-Resolve benötigte Netzwerk; Host Linux x64, keine Fold7-/ARM64-Termux-Behauptung. Bestehende Compilerwarnungen und nicht gestrippte liblitertlm_jni.so bleiben im Rohlog sichtbar. Vier Checks/Parität enthalten synthetische temporäre DEV-Trainingsprüfungen; keine Besitzergewichte und kein neuer Forschungsnachweis.

## Drei Kontrollen und Regressionen

1. Architektur/Git/Schnittstellen: zehn tatsächliche Vorfahren in `ancestry.json`, Opus-Start und Main remote erneut geprüft. Vorhandene Worker/Pläne/Receipts wiederverwendet. Kein konkurrierender Mechanismus, kein Main-Merge.
2. Echte RED→GREEN-Reparaturen: expliziter neuer Chat verliert alten Baselinebezug; drei Builder-Dialogrennen; später Zielbindung für Abbruchdialog/-antwort und Lebenszeitkontrolle für verzögerten Reset; abgeschnittene Releasebefehle im Pinbericht; fehlendes Widget-Releasegate. Alle Zwischenfehler sind als solche archiviert.
3. [Unabhängiger Quellenreview](INDEPENDENT-SOURCE-REVIEW.md): elf abweichende Pins und zehn zusätzliche APK-Quellen gegen echte attestierte Blobs geprüft. Review fand die beiden zusätzlichen Rennen, sie wurden im selben Reparaturdurchgang behoben. Finale unabhängige Reset-Reproduktion2→2, volle Endsuite danach.

Opus01cf6f80: alter Wiring-Code reproduziert SyntaxError; Originaltest kontrolliert Projektwahl nach Recreation. Echte SDK-Mutation allein des SavedState-Guards: Kontrolle1/1 grün, Mutation1/1 rot; ursprünglicher Snapshot/Quellhash in `opus01cf-mutation/manifest.json` (Testcheckpoint3018524a, vor finalem gemeinsamen Lauf). Derselbe unveränderte Guard wird auf ec28d661 in der vollständigen Suite geprüft.
Opus0e51aff0: sieben ursprüngliche Berichttests grün; entfernte Erfassung unpinnter Quellen wird vom Test erkannt; zusätzlicher echter Befund verlor Fortsetzungszeilen, repariert und8/8 grün.
Opus4f6cc98f: Originalquelle unverändert; exakter Negationsfall mit Guard1/1 grün, ausschließlich entfernter wanted.isNotEmpty-Guard1/1 rot mit NoSuchElementException. Kein TEST3- oder neuer Blindvergleich.

## Freigabe und APKs

[Quellprüfung](release-source-audit-ec28d661.json), [21 Einzeldiffs + Releasegate](source-diffs/), [Vorschlag ohne Freigabe](proposed-source-pins-NOT-APPROVED.txt).
Alle22 bisherigen APPROVED_RELEASE_SOURCES-Zeilen unverändert.11 passen,11 verlangen Zustimmung;10 zusätzliche APK-Quellen sind separat vollständig ausgewiesen. Das strengere Releasegate verlangt nun die tatsächlichen Widgettests.
**OWNER_SIGNING_BLOCKED** bis zur ausdrücklichen Besitzerfreigabe. Kein Besitzer-Key gelesen, kein Render-Environment geändert oder Deploy gestartet.

Tatsächlich gebaute APK dient allein Host-QA: `at.heinz.deviceai.preview`,0.3.3/code6 (bestehende Preview-Konfiguration),55.837.205 Bytes,
SHA256 `b3d179ea7c89a8d01495a37e06b78a68594c2926bba00d0458b854a5c7429616`, Standard-Debugzertifikat `e4d1291879e6d079e3e3d5f798ed77b0881199d9a5a0390186a23a5de07ace70`.
Unabhängig apksigner/aapt2 geprüft, nicht installiert, nicht als Besitzerupdate angeboten. APK liegt nur im erzeugten lokalen Build-/Artefaktbereich; kein v19-Download behauptet. Sie lässt sich aus dem öffentlichen Source reproduzieren; kein privater Schlüssel oder Sitzungsartefakt als Codeabhängigkeit.

Bestehende0.3.17/code18: vollständiger tatsächlicher HTTPS-Download55.735.626 Bytes, erwarteter SHA256 `0ef181f011990cae1ec7b0b71c09fddf7ab0824dd132e80a702e37363a72e998`, ein v2-Signierer mit Besitzerzertifikat `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`, Paket/Version unabhängig geprüft.
Identität/URL in `candidate-identity.json`, öffentliche BUILD-INFO und echte aapt/apksigner-Ausgaben in `rollback/`. Keine Deinstallation oder Datenrücksicherung. Android-Downgrade nach code19 nicht geprüft.
Alte M4-APKs beider Vorlagen erneut mit bestehendem CCC1-Verifier + Signer/Paket geprüft, unveränderte Hashes in `m4-preserved-artifacts.txt`; historische Builds bleiben historisch.

## Wiederholung ohne private Umgebung

Echte öffentliche Toolchain laut `toolchain.json` installieren; JAVA_HOME, ANDROID_HOME/SDK_ROOT und PATH auf die eigene Installation setzen. Alle Abhängigkeiten aus den bestehenden Google/Maven-Repositories. Backend mit `npm --prefix backend ci --ignore-scripts --no-audit --no-fund`.
Exakte vollständige Befehle sind in den25 Jobidentitäten, portable Hauptbefehle:

```sh
env -u NODE_TEST_CONTEXT -u CONTINUUM_REPAIR_DEPTH -u JAVA_TOOL_OPTIONS node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
env -u NODE_TEST_CONTEXT -u CONTINUUM_REPAIR_DEPTH node --test --test-reporter=tap --test-concurrency=2 backend/test/*.test.js
env -u NODE_TEST_CONTEXT -u CONTINUUM_REPAIR_DEPTH node --test --test-reporter=tap --test-concurrency=2
gradle --no-daemon --max-workers=2 --console=plain -PdeviceAiPreview=true -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Die Flags sind erforderlich, damit dieselbe isolierte Preview inklusive Widgettests geprüft wird. Das ist kein Owner-Signierbefehl.
Zusätzliche Groq-/Workshop-/Autogenesis-/Paritäts-/Checks-Befehle stehen vollständig in den Identitäten und im unveränderten v19-Gate. Keine identischen GitHub-Actions-Fehlstarts wiederholt.
