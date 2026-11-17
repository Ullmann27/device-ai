# Code59: gezielte Paket-Sichtbarkeitsausnahme

Prüfung: 14.11.2026. Veröffentlichte Quellbasis: `46e935987fcb8933377867eca9d7d97f17bc3633`.
Die hier geprüften Inventurpfade bleiben im Folgestand unverändert.

## Bestehender Funktionsumfang

- `BootstrapBrain.decide` führt bejahte Inventurfragen zu `list_installed_apps`.
  `ToolRegistry` klassifiziert das Werkzeug als lesend. `AgentActivity.createToolGate`
  verwendet den vorhandenen `DeviceToolExecutorRunner`.
- `DeviceToolExecutor.execute` behandelt diese Inventur vor der Shizuku-Prüfung mit
  `PackageManager.getInstalledApplications(0)`. `userOnly` ist standardmäßig wahr;
  falsch schließt Systempakete ein. Es gibt keinen Launcher-Filter. Die Ausgabe
  bleibt auf 400 sichtbare Pakete begrenzt und verspricht keine Vollständigkeit.
- `DeviceToolExecutor.resolvePackage` und `CommandInterpreter.resolvePackage`
  verwenden dieselbe Inventur für unbekannte App-Namen. `MainActivity.handleInput`
  ruft den Interpreter auf; dessen vorhandene Aktionsbestätigungen bleiben bestehen.
- `AndroidInstalledAppLookup.search` ist enger: `ACTION_MAIN` / `CATEGORY_LAUNCHER`,
  ausschließlich Suchvorschläge, keine Startberechtigung. Für diesen einzelnen
  Pfad wäre eine passende Intent-Abfrage ausreichend.

## Entscheidung und Belege

Android filtert ab Ziel-API 30 unter anderem `getInstalledApplications`,
`getPackageInfo` und `queryIntentActivities` nach Paketsichtbarkeit:
[Package visibility filtering](https://developer.android.com/training/package-visibility).

Gezielte `<queries>` erfassen bekannte Paketnamen, passende Intent-Signaturen oder
Provider. Die vorhandene Inventur ist dagegen unabhängig von solchen Komponenten.
Eine Launcher-Abfrage oder feste Paketliste deckt insbesondere unbekannte Pakete
ohne Launcher nicht vollständig ab. Deshalb bleibt die bereits vorhandene
Berechtigung erhalten; die dokumentierte Ausnahme für komponentenunabhängige
Paketabfragen ist hier technisch einschlägig:
[Declare package visibility needs](https://developer.android.com/training/package-visibility/declaring).

Nur das bestehende Permission-Element erhält
`tools:ignore="QueryAllPackagesPermission"`. Android unterstützt dieses Attribut
für einzelne XML-Elemente und entfernt Tool-Attribute beim Build:
[Tools attributes reference](https://developer.android.com/studio/write/tool-attributes).

Es werden keine Berechtigungen ergänzt, keine Tool-/Aktionsfreigaben geändert und
keine globalen Lint-Prüfungen deaktiviert. Eine Google-Play-Freigabe wird damit nicht
behauptet; Android beschreibt diese Veröffentlichungsgenehmigung gesondert.

## Prüfgrenze

Dieser Vermerk dokumentiert die Quell- und Dokumentationsprüfung. Ausführungsergebnisse
sind jeweils an ihren tatsächlichen Commit gebunden und in
[PR #848](https://github.com/Ullmann27/device-ai/pull/848) dokumentiert.
