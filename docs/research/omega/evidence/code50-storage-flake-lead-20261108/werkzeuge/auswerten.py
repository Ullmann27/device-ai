"""Wertet die JUnit-XML-Rohdaten dieses Evidenzordners aus und schreibt summary.json.

Aufruf: python3 werkzeuge/auswerten.py            (Ordner = Elternordner dieses Skripts)
        python3 werkzeuge/auswerten.py <Ordner>

Reine Auswertung vorhandener Dateien: keine Netzwerkzugriffe, es werden keine Tests ausgeführt.
Variante A = unveränderter Stand cc8ee5f (Fixture-Timeout 500 ms),
Variante B = Kandidat c7fe719 (nur Fixture-Timeout 1500 ms, siehe kandidat/).
"""
import json
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

WURZEL = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent


def lese(pfad):
    faelle, fehler = 0, []
    for tc in ET.parse(pfad).getroot().iter("testcase"):
        faelle += 1
        for art in ("failure", "error"):
            e = tc.find(art)
            if e is not None:
                zeilen = (e.get("message") or e.text or "").strip().splitlines()
                fehler.append({"name": (tc.get("name") or "")[:110], "meldung": zeilen[0][:110] if zeilen else ""})
                break
    return {"datei": str(pfad.relative_to(WURZEL)), "faelle": faelle, "fehlgeschlagen": len(fehler), "fehler": fehler}


def fisher_einseitig(n_a, k_a, n_b, k_b):
    """Einseitiger exakter Fisher-Test: Wahrscheinlichkeit, dass A mindestens k_a der k_a+k_b Fehlrunden trägt."""
    n, k = n_a + n_b, k_a + k_b
    return sum(math.comb(k, x) * math.comb(n - k, n_a - x) for x in range(k_a, min(k, n_a) + 1)) / math.comb(n, n_a)


def gruppe(name, pfade_a, pfade_b):
    a, b = [lese(p) for p in pfade_a], [lese(p) for p in pfade_b]
    k_a, k_b = sum(r["fehlgeschlagen"] > 0 for r in a), sum(r["fehlgeschlagen"] > 0 for r in b)
    return {
        "gruppe": name,
        "A_runden": len(a), "A_runden_mit_fehlschlag": k_a, "A_fehlgeschlagene_faelle": sum(r["fehlgeschlagen"] for r in a),
        "B_runden": len(b), "B_runden_mit_fehlschlag": k_b, "B_fehlgeschlagene_faelle": sum(r["fehlgeschlagen"] for r in b),
        "p_einseitig_fisher_A_schlechter": round(fisher_einseitig(len(a), k_a, len(b), k_b), 4),
        "runden_A": a, "runden_B": b,
    }


def pfade(muster, anzahl):
    return [WURZEL / muster.format(i=i) for i in range(1, anzahl + 1)]


isoliert = gruppe("isoliert (ohne künstliche Last)", pfade("isoliert-ab/A-{i}.xml", 6), pfade("isoliert-ab/B-{i}.xml", 6))
stress = gruppe("Stress (3 begrenzte CPU-Burner, Dateien S-*)", pfade("stress-ab/S-A-{i}.xml", 6), pfade("stress-ab/S-B-{i}.xml", 6))
harness = gruppe("Harness critical (Lastprofil im Rohlog nicht festgehalten)", pfade("harness-ab/A-{i}/storage-agent.xml", 5), pfade("harness-ab/B-{i}/storage-agent.xml", 5))

a_gesamt = stress["runden_A"] + harness["runden_A"]
b_gesamt = stress["runden_B"] + harness["runden_B"]
ka, kb = sum(r["fehlgeschlagen"] > 0 for r in a_gesamt), sum(r["fehlgeschlagen"] > 0 for r in b_gesamt)
gepoolt = {
    "gruppe": "gepoolt Stress + Harness (ohne isolierte Läufe)",
    "A_runden": len(a_gesamt), "A_runden_mit_fehlschlag": ka,
    "B_runden": len(b_gesamt), "B_runden_mit_fehlschlag": kb,
    "p_einseitig_fisher_A_schlechter": round(fisher_einseitig(len(a_gesamt), ka, len(b_gesamt), kb), 4),
}

mech_log = []
for nr, zeile in enumerate((WURZEL / "mechanismus" / "rejections.log").read_text(encoding="utf-8").splitlines(), 1):
    abgelehnt = [e for e in json.loads(zeile) if not e.get("ok")]
    arten = set()
    for e in abgelehnt:
        if e.get("name") == "TimeoutError" and e.get("t") == 500:
            arten.add("500-ms-Fixture-Timeout")
        elif e.get("name") == "TimeoutError" and e.get("t") == 50:
            arten.add("dedizierter 50-ms-Timeout-Test")
        elif e.get("message") == "NODE_HTTP_507":
            arten.add("NODE_HTTP_507 (Quota-Test)")
        else:
            arten.add("sonstige")
    mech_log.append({"zeile": nr, "abgelehnt": abgelehnt, "art": sorted(arten)})

ergebnis = {
    "schema": "continuum.code50-storage-flake-summary.v1",
    "quellstand": "cc8ee5fe2dffd98f41c505c01b5d9ffdae4d8cf9",
    "status": "WIP / UNVERIFIED (Kandidat nicht angewendet; Stichprobe klein)",
    "hinweis": "A = Original (500 ms), B = Kandidat (1500 ms). Vorab-Daten des Hauptagenten; die vorregistrierte Entscheidungsregel gilt für NEUE Runden des Agenten 1.",
    "kritischer_fehlschlag_cc8ee5f": lese(WURZEL / "critical-cc8ee5f-FAIL" / "storage-agent.xml"),
    "gruppen": [isoliert, stress, harness],
    "gepoolt": gepoolt,
    "mechanismus": {
        "lauf_1": lese(WURZEL / "mechanismus" / "lauf-1.xml"),
        "lauf_2": lese(WURZEL / "mechanismus" / "lauf-2.xml"),
        "abgelehnte_replikate": mech_log,
    },
}
ziel = WURZEL / "summary.json"
ziel.write_text(json.dumps(ergebnis, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(f"geschrieben: {ziel}")
for g in (isoliert, stress, harness, gepoolt):
    print(f"{g['gruppe']}: A {g['A_runden_mit_fehlschlag']}/{g['A_runden']} Fehlrunden, B {g['B_runden_mit_fehlschlag']}/{g['B_runden']}, p_einseitig={g['p_einseitig_fisher_A_schlechter']}")
