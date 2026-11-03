# Continuum Alpha Squad: vier Rollen auf dem echten CCC1-Worker

SOURCE_ONLY, 03.11.2026: Code und Tests gepusht. Kein auf diesem Commit durchgeführter Android-, PC- oder Fold7-Test.

Die vier logischen Rollen sind: ARCHITECT (CCC1-Preflight und Plan), TEST-FORGER (echte RED-Basistests der festen Profile), CODER (bestehende CCC1-Reparaturstrategien, optional freigegebener lokaler Modelladapter) und STATIC-GUARD (zweiter unabhängiger Worktree/Verifier). Das sind noch keine vier unabhängigen aktiven KI-Instanzen.

## Terminal auf einem eingeschalteten eigenen Rechner

Nur im vollständigen, sauberen Git-Checkout des freigegebenen, isolierten Entwicklungsbranches. Android-Testprofile benötigen eine separat geprüfte Toolchain. Kein kostenpflichtiger API-Schlüssel ist für diese reine Node-CCC1-Variante erforderlich.

Schritt 1 – nur prüfen:

    PROJECT="$(git rev-parse --show-toplevel)"
    STATE="$HOME/.local/state/continuum-ccc1"
    node .github/continuum/alpha-squad-ccc1.mjs plan --project "$PROJECT" --state "$STATE" --repair node

READY_FOR_OWNER_SUBMISSION ist nur eine Preflight-Aussage, kein erfolgreicher Patch.

Schritt 2 – nach tatsächlicher eigener Entscheidung den autorisierten Reparaturauftrag anlegen:

    node .github/continuum/ccc1-cli.mjs submit --project "$PROJECT" --state "$STATE" --repair node

Schritt 3 – genau einen vorhandenen, autorisierten CCC1-Auftrag weiterführen:

    HEAD_SHA="$(git rev-parse HEAD)"
    node .github/continuum/alpha-squad-ccc1.mjs run-once --project "$PROJECT" --state "$STATE" --repair node --expected-head "$HEAD_SHA" --owner-confirmed true

Schritt 4 – echtes Ergebnis anzeigen:

    node .github/continuum/ccc1-cli.mjs status --state "$STATE" --format json

Die Run-Schnittstelle ist bei fehlender Bestätigung, abweichender SHA, mehrdeutigen offenen Aufträgen, Kettenverwechslung oder fehlender CCC1-Preflight blockiert. Sie erzeugt keinen neuen Auftrag, führt keine Modell-Kommandos als Shell aus und führt keinen Merge oder Release durch. Bei erfolgreich geprüftem Patch: Besitzerprüfung, keine automatische Installation.

## Grenzen und Tests

Git-Worktree bedeutet Versionsisolierung, NICHT sichere Prozess-Sandbox. Für nicht vertrauenswürdigen Code muss die bestehende isolierte Ausführungsumgebung verwendet werden. Es gibt kein gefälschtes grünes Ergebnis, keine erfundene 20-Bot-Parallelität und keine geänderten Besitzerdateien.

Gezielte neue Tests:

    node --test .github/continuum/alpha-squad-ccc1.test.mjs

Zusätzlich: vorhandene gesamte CCC1-/Node-Regressionssuite, vollständige Android-JUnit, Lint und Debug-Build vor Übernahme. Auf dem Fold7 native Gemma-Laufzeit gesondert prüfen. Die vier tatsächlich separaten Chief-Personas und ihre Android-/PC-Laufzeitverbindungen bleiben weitere Integrationsarbeiten.
