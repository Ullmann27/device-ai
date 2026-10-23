# Übernahme von PR #72 (I1/I2 + neuerer Span-Kern) – Prüfung und Entscheidung (23.10.2026)

**Auftrag (Master-Auftrag §4):** nicht übernommene Teile älterer Zweige auf echten Zusatznutzen prüfen, wertvolle Tests
und Belege nicht verlieren, nur passend zur aktuellen Architektur übernehmen. Ausgangslage laut
`CONTINUUM-ACTIVE-CHECKPOINT-20261023.md`: 34 Dateien von PR #72 (`opus/continuum-i2-selective-risk-20261022` @
`1c72f8a2`) fehlten im Integrationsstand; „erst API-/Holdout-/Checkpoint-Kompatibilität nachweisen, dann selektiv
übernehmen“.

## Befund

- Der Integrationsstand enthielt eine **ältere** Fassung von `ContinuumResidualSpanCore.kt` (aus 21365a4). I1/I2
  bauen auf der **neueren** Fassung mit Merkmalsvarianten (`Features`, Checkpoint v2, Kandidat V8) auf.
- Nutzer des Kerns im aktuellen Baum: `ResidualSpanResearchAdapter`, `ContinuumCounterfactualCurriculum`, deren Tests
  und `ContinuumResidualSpanCoreChecks`.
- Die I1/I2-Experimente trainieren aus festem Seed; sie brauchen keine gespeicherten Gewichte.

## Nachweise (`docs/evidence/i1-i2-adoption-20261023/verification.txt`)

1. **Schnittstelle:** Haupt- und Testquellen kompilieren mit allen heutigen Nutzern (`scripts/android-kotlin-unit-check.sh`).
2. **Tests:** JUnit **852/852** (vorher 827; +25 aus I1, I2, Varianten, Abstinenz-Prüfstand), 4 Prüfprogramme bestanden.
3. **Standardverhalten:** `ContinuumResidualSpanCoreChecks` gibt mit altem und neuem Kern **dieselben 13 Zeilen** aus,
   einschließlich Gradientenfehler `1.5443709852624998E-10` und Loss `1.2136851176488221 → 0.6067638047117935`. Der
   Adapter verhält sich also unverändert.
4. **Ergebnisse:** `ContinuumSelectiveRiskExperiment` und `ContinuumSpanAbstentionExperiment certify` reproduzieren die
   PR-#72-Belege **bytegleich** (SHA-256 `431da1fd…`, `af097d3c…`; Letzteres ist auch das I1-Meilensteinlog).

## Entscheidung

**Übernommen** (PR #99, `e9714f70` Code, Folgecommit Doku):

- **Code:** `ContinuumResidualSpanCore.kt` (neuer), `ContinuumCertifiedAbstention.kt` (I1),
  `ContinuumSelectiveRiskCertification.kt` (I2).
- **Tests und Experimente:** 8 Dateien unter `app/src/test/.../learning/span/`.
- **Dokumente:**
  - `CONTINUUM-CSX1-FIRST-TRAINED-MILESTONE-20261022.md` (Kern; seine Zahlen stimmen mit den Checks überein)
  - `CONTINUUM-CSX1-ABSTENTION-OPUS-20261022.md`
  - `CONTINUUM-INTELLIGENCE-MILESTONE-I1-20261022.md`
  - `CONTINUUM-I2-SELECTIVE-RISK-OPUS-20261022.md`
  - `CONTINUUM-INTELLIGENCE-LAB-20261022.md` (dokumentiert den bereits vorhandenen, blob-identischen
    `ContinuumIntelligenceAudit.kt`)
- **Belege:** `docs/evidence/csx1-abstention-20261022/` (10) und `docs/evidence/i2-selective-risk-20261022/` (6).

**Nicht übernommen** (bleiben auf PR #72, Zweig nicht löschen):

- `CONTINUUM-OPUS-INTELLIGENCE-RESUME-20261022.md`: verbindliche Übergabe mit veralteten PR-/SHA-Angaben, würde
  irreführen.
- `CONTINUUM-EXP-OMEGA-GEMINI-REVIEW-20261022.md`: Review eines nicht umgesetzten Vorschlags.
- `CONTINUUM-CSX1-ARGON-REVIEW-20261022.md`: Architektur-Review mit Übergabeteil; der übernommene Code ist durch die
  Meilenstein-Dokumente beschrieben.

Bekannter toter Verweis (schon auf PR #72): `CONTINUUM-CSX1-ABSTENTION-OPUS-20261022.md` nennt
`docs/CONTINUUM-OPUS-HANDOFF-20261022.md`, das es in keinem Zweig gibt.

## Einordnung und Grenzen

- I1/I2 sind **Forschung**: Im App-Code gibt es keinen Aufrufer, der Chat wird nicht verändert, kein Gewicht und kein
  Checkpoint ändert sich. Sie liefern genau die kalibrierte Antwortsperre, die dem Forschungspfad des Adapters fehlt
  (CSX-Konfidenz ohne unabhängige Kalibrierung ist kein Zertifikat).
- Ein `Certificate` ist weiterhin von Hand konstruierbar (kein kryptographisch attestiertes Kalibrierergebnis).
- Gradle-Build, Lint, APK und Gerät: **nicht ausgeführt** (kein SDK in dieser Umgebung).

**Rücksetzweg:** `git revert` der beiden Übernahme-Commits stellt den älteren Kern und den vorherigen Dateibestand
wieder her; PR #72 bleibt unverändert als Ursprung.
