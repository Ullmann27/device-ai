# Device AI Continuum – Wiederherstellungspunkte (17.10.2026)

Diese Datei dokumentiert **verifizierte GitHub-Refs**. Die hier genannten Sicherungsbranches zeigen zum Zeitpunkt der Einrichtung exakt auf die angegebenen unveränderlichen Git-Commitobjekte. Der Sicherungsbranch selbst ist **änderbar und nicht GitHub-branchgeschützt**. Ein zweiter Branch im selben Repository ist kein unabhängiges externes Backup.

## Geprüfte Ausgangspunkte

| Rolle | Ursprünglicher Branch | Sicherungsbranch | Verifizierter Commit |
| --- | --- | --- | --- |
| Default-/Produktionszweig | `main` | `backup/continuum-main-20261017-190f5eab` | `190f5eabaa60fc59292b2b80d3086aafd7743a80` |
| Hauptentwicklung, PR #19 | `copilot/pr-12-working-first-vertical-slice` | `backup/continuum-pr19-20261017-ec4b4cae` | `ec4b4caecd64835e56af924dae1f7ad825059bfc` |
| Stabilisierung, PR #28 | `continuum/argon-safety-review-20261017` | `backup/continuum-pr28-20261017-4c4b3fd8` | `4c4b3fd8b9cdb5f67dd79b799026b9002197ff05` |

Die ursprünglichen Zweige und beide Pull Requests wurden bei der Sicherung **nicht verändert**. PR #28 ist weiterhin ein Draft und zielt auf den Entwicklungszweig von PR #19, nicht auf `main`.

## Wiederherstellung ohne destruktive Ref-Operation

Zunächst das Repository frisch klonen oder in einer vorhandenen vertrauenswürdigen Arbeitskopie `git fetch origin` ausführen. Beispiel für den Stand von PR #28:

```bash
git clone https://github.com/Ullmann27/device-ai.git device-ai-recovery
cd device-ai-recovery
git fetch origin
git switch --create restore/continuum-pr28-20261017 \
  origin/backup/continuum-pr28-20261017-4c4b3fd8
git rev-parse HEAD
git status --short
```

`git rev-parse HEAD` **muss** `4c4b3fd8b9cdb5f67dd79b799026b9002197ff05` ergeben. Entsprechend sind für die anderen Sicherungspunkte die GitHub-Refs und SHA-Werte aus der Tabelle zu verwenden.

**Nicht** `git reset --hard`, `git push --force`, `git merge` nach `main` oder einen Backup-Branch umhängen, um eine Wiederherstellung zu beginnen. Stattdessen auf einem neuen `restore/*`-Branch prüfen und Änderungen per Review-Pull-Request vornehmen.

## Empfohlene zusätzliche Absicherung

1. Für `main` in **GitHub → Repository Settings → Rules → Rulesets** das Löschen und Force-Pushes untersagen, Pull-Request-Reviews erzwingen und Bypass-Rechte restriktiv halten. Die Abfrage vom 17.10.2026 meldete `protected=false`; eine Ruleset-Änderung ist **nicht** Bestandteil dieser GitHub-Commits.
2. Auch die Backup-Branches gegen Löschen und Force-Pushes schützen. Die Sicherung schützt derzeit primär vor einem versehentlichen normalen Branch-Wechsel, nicht vor allen Administratoraktionen.
3. Ein echtes unabhängiges Backup auf einem kontrollierten Rechner erzeugen, zum Beispiel mit `git clone --mirror` oder `git bundle create`; Zugang sichern und die Wiederherstellung testweise ausführen. Eine solche externe Kopie wurde hier **nicht** erstellt.
4. Vor jeder Integration Node-, Backend-, Kotlin-/Android-Tests, APK-Artefakt und SHA-256-Bericht für den konkreten Review-Head prüfen. Fehlen Testschritte oder eine APK, ist keine Buildfreigabe belegt.

## Aktueller Validierungsumfang

Die lokalen vier M4-Prüfer-Kontrakttests und `bash -n` für `scripts/local-m4-android.sh` wurden nach der letzten Korrektur bestanden. Die betroffenen Testfälle simulieren die Buildwerkzeuge; sie sind **kein** vollständiger Android-Gradle-Test. Ein erfolgreicher Android-APK-Build auf dem aktuellen PR-#28-Head ist weiterhin nicht nachgewiesen. Die beiden zuletzt beobachteten GitHub-Action-Läufe hatten keine ausgeführten Testschritte.
