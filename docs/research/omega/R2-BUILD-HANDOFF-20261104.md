# R2 Build – Signing-Abweisung und Android-Prüfweg

Stand: 04.11.2026. Arbeitszweig `repair/round2-build-local`.
Status: `NODE_REGRESSION_PASS / INDEPENDENT_FIX_REVIEW_PASS`.
Eigener Android-Lauf: `NOT_RUN`.

## Ergebnis und Quellbindung

Die vorhandene Preflight-Testdatei prüft jetzt jede der sechs bekannten
Signing-Umgebungsvariablen im gewöhnlichen und im Owner-QA-Modus. Alle zwölf
Fälle verwenden ausschließlich den harmlosen Wert `fixture-not-signing-material`.
Die Fixture entfernt zuvor sämtliche geerbten Signing-Variablen, erstellt einen
eigenen temporären Git-Checkout und ersetzt `curl` durch einen lokalen Canary.
Die Prüfungen verlangen Exit 69, den konkreten Signing-Fehler und das Ausbleiben
von Downloadversuch, Build-Arbeitsverzeichnis und QA-Protokollverzeichnis.

Die Reparatur selbst stammt vom Koordinator und wird hier nicht erneut erzeugt:

- Offizieller Commit: `84cd8cfc05dedef5d4feb28ecfa103530e95f7ff`.
- Lokaler identischer Fix: `e0714c5f2999346ef4e258e2ffaf224d58cfdc9a`.
- Geprüfter Skript-Blob: `9ea8eae2011504b5283a0cf4e62231f0aaa7c39b`.
- Ursprünglicher Arbeitsstand: `63dec3b1296b1d4257310296de3781d75b7a266a`.

Für GREEN wurde die exakte Skriptdatei aus dem lokalen Fix in den eigenen
Arbeitsstand übernommen. Nach dem Lauf wurde sie zurückgesetzt; anschließend
wurde der Arbeitszweig ausschließlich per Fast-forward auf den vorhandenen
Koordinator-Commit gebracht. Der eigene Änderungssatz ergänzt Tests und Belege,
keine zweite Produktionskorrektur.

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis | Exit |
| --- | --- | --- |
| Erweiterter Preflight auf unverändertem alten Skript | 43 Fälle; 41 PASS, genau 2 FAIL | 1 |
| Sechs vorhandene Build-/Release-Vertragssuiten mit erweitertem Preflight auf Root-Fix | 81 Fälle; 81 PASS, 0 FAIL, 0 SKIP | 0 |
| Einzeldatei-Probe `cp --reflink=always` an vorhandener AGP-8.10.0-JAR | `Operation not supported` | 1 |

Die beiden roten Fälle sind ausschließlich
`CONTINUUM_MENTOR_SIGNING_P12_BASE64` in Modus `0` und `1`.
Die vorhandenen Versionsfälle, positiven kanonischen Versionen sowie die
Owner-/Service-/Host-/Quellfreigabesperren wurden unverändert mitgeprüft.

Reproduktion auf einem Stand mit dem oben genannten Root-Fix:

```bash
node --test --test-reporter=tap \
  scripts/continuum-owner-continuous-qa-preflight.test.mjs \
  scripts/continuum-owner-catalog-contract.test.mjs \
  scripts/continuum-owner-continuous-release.test.mjs \
  scripts/continuum-one-tap-updater-contract.test.mjs \
  scripts/continuum-owner-v38-release.test.mjs \
  scripts/continuum-release-host-preflight.test.mjs
```

Rohdaten und SHA-256-Bindungen liegen unter
`docs/evidence/three-repair-bots-round2-20261104/build/`:
`signing-red.tap`, `signing-green.tap`, `reflink-probe.json`, `receipt.json`.

## Unabhängiges Review der Produktionskorrektur

Ergebnis: keine offene Beanstandung im untersuchten Ein-Zeilen-Diff.
Der fehlende sechste Schlüssel wird in derselben vorhandenen Schleife wie die
fünf anderen ausgewertet. Ein nichtleerer Wert stoppt den Einstieg vor
Verzeichnisanlage und Download. Der Wert selbst wird nicht ausgegeben.
Die Erweiterung lockert weder Versionsprüfung noch Owner-, Host-, Service-,
Branch- oder Quell-SHA-Prüfung. Die echten Ausführungsproben bestätigen die
Abweisung in beiden unterstützten Modi. Das Review ist auf diesen Fix und seine
Preflight-Wirkung begrenzt; es ersetzt keine vollständige Render-/Release-Abnahme.

## Android: verfügbarer Pfad und aktuelle Grenze

Der zuvor fehlende AGP-Stand ist inzwischen tatsächlich lokal vorhanden.
Der erfolgreiche Integrationsprüfweg liegt unter
`/workspace/scratch/822e72396ed9/build-env`:

| Bestandteil | Tatsächlich vorgefundener Pfad / Stand |
| --- | --- |
| JDK | `jdk17` → `/workspace/scratch/b7d23de0e715/build-toolchain/jdk17`; Temurin 17.0.20.1+1 |
| Gradle | `gradle-8.11.1` → `/workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1` |
| Android SDK | `android-sdk` → `/workspace/scratch/b6ee64d418cf/toolchain/android-sdk`; API 36, Plattformrevision 2 |
| Abhängigkeiten | `gradle-cache/caches/modules-2`; gemessen 841 MiB |
| Gradle-Versionscache | `gradle-cache/caches/8.11.1`; gemessen 616 MiB |
| Vorheriges App-Buildverzeichnis | `/workspace/scratch/822e72396ed9/continuum-combined/app/build`; gemessen 389 MiB |

Zum eigenen Check waren 1,8 GiB frei. Eine gewöhnliche Kopie beider Cacheteile
zuzüglich eines vergleichbaren Buildverzeichnisses würde ungefähr 1,8 GiB
belegen, ohne ausreichende Reserve für temporäre Dateien und parallele Arbeit.
Die geprüfte Reflink-Alternative scheitert bereits an einer einzelnen bekannten
AGP-JAR. Ihr eigenes temporäres Ziel wurde nach der Probe entfernt.
Es wurde kein Cache kopiert, kein fremder Checkout beschrieben und kein
Gradle-Build gestartet. Ein fremder aktiv beschreibbarer Cache wurde auch nicht
als unveränderlicher `GRADLE_RO_DEP_CACHE` ausgegeben.

Der bereits erfolgreiche 822e-Lauf bleibt ein eigenständiger Beleg. Sein
`combined-gradle.log` bindet den Lauf an lokalen Commit
`377ac199b1e360cecefbe1e04bf43e68e94695b9`, Baum
`2be53764ed72e00e780c7b87ff1401c195ac0a50`, `--offline`, `-Xmx1g` und die
benannten Testklassen. Der Koordinator hat dessen Quellidentität und XML-Belege
separat geprüft und in der Runde-2-Abnahme gesichert. Dieser frühere Lauf wird
hier nicht zu einem eigenen Lauf oder zu einer Prüfung späterer Änderungen
umetikettiert.

## Anschluss, Erhaltung und Rückfall

1. Den Testcommit auf den Koordinatorstand mit dem Signing-Fix übernehmen und
   dieselben sechs Node-Suiten unabhängig ausführen.
2. Für neue Android-Änderungen einen ausreichend großen eigenen Dependency-
   Snapshot bereitstellen oder einen ausdrücklich unveränderlichen Cache
   verwenden. JDK/Gradle/SDK können lesend wiederverwendet werden. Dann nur die
   betroffene UI-Klasse, Temporal-Klasse und nötige Anschlussfälle offline mit
   einem Worker und begrenztem Heap ausführen; echte XML/Exitcodes sichern.
3. Fold7-/GPU-/Audio-/Installationsabnahme bleibt offen. Es wurde keine APK
   gebaut, signiert, veröffentlicht oder installiert.

EXP-00, EXP-07 und EXP-08 sowie ihre Schnittstellen bleiben erhalten. Keine
UI-/Retriever-Produktionsänderung, kein Versions-, Paket-, Signer- oder
Updatekanalwechsel, kein Main-Merge, keine Änderung an Owner/Offline/CCC1.
Rückfall: ausschließlich den eigenen Test-/Belegcommit zurücknehmen; der
bereits unabhängig reparierte Produktionsschutz bleibt bestehen.

Die Veröffentlichung erfolgt ausschließlich durch den Koordinator in PR505;
dieser Bot nimmt keine Remote-Mutation vor.
