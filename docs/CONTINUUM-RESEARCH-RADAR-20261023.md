# Forschungsradar 23.10.2026 (Meilenstein F): Ideen gegen gemessene Engpässe

**Grundsatz**
- Eine Idee kommt nur auf die Liste, wenn sie einen Engpass angeht, den wir selbst gemessen haben.
- „Besser“ gilt erst nach einer eigenen, vorregistrierten Messung. Die Behauptung eines Papers reicht dafür nie.
- Fremder Code wird nur mit geprüfter Lizenz übernommen. Sonst wird nur die Idee genutzt und der Code selbst geschrieben.

**Wie geprüft wurde**
- Das Radar-Werkzeug `research-radar.mjs` erreicht `export.arxiv.org` aus dieser Cloud-Umgebung nicht. Deshalb ist dieser Stand von Hand per Websuche erstellt.
- Titel, Ort und Lizenz wurden auf Konferenz-, Projekt- oder Dokumentationsseiten geprüft. `arxiv.org` selbst war hier nicht abrufbar; arXiv-Nummern stammen aus Suchergebnissen und Projektseiten.

## 1. Gemessene Engpässe

| Nr. | Engpass | Beleg (eigene Läufe) |
|---|---|---|
| E1 | Das Zählmodell schlägt das neuronale Modell | LM1-TEST: LM1-GM 2,439 BPB gegen KN-7 2,150. NFC1: Byte-minGRU 2,41–2,47 gegen KN-7 1,98, Mischung 1,93 (NF-6b-lite). |
| E2 | Keine brauchbare Sprachausgabe | LM1 Schritt 7: Schleifen, Anweisungen auf Zufallsniveau |
| E3 | Die Absicht des Besitzers ist mehrdeutig | NF-5b: 1 falsche Bestätigung. NF-5c: 12 von 13 Mehrdeutigkeiten erkannt, 9 von 11 beantworteten Rückfragen richtig aufgelöst. |
| E4 | Kandidaten, die die Vorprüfung nicht ausführen kann, waren unsichtbar | Erster echter Worker-Lauf: `ccc1-envelope#canonical` mit `ERR_ACCESS_DENIED`. Behoben in Vorprüfung v2 (LM1-Bericht, Abschnitt 10). |
| E5 | Node 22 sperrt das Netz im Permission Model nicht | Seit v2 sperrt der Prüfprozess das Netz selbst, zur Laufzeit. |
| E6 | Zu wenige geprüfte Erfahrungen, um daraus zu lernen | Erfahrungsprotokoll des Workers: 3 Datensätze |
| E7 | Nichts ist auf ARM64 gemessen | Alle Zeiten stammen von x86_64. |

## 2. Priorisierte Ideen

### Priorität 1 (nächste Schritte)

**(a) ∞-Gramm mit Suffix-Array als Zählmodell und Mischpartner (E1)**
- **Quelle:** Liu, Min, Zettlemoyer, Choi, Hajishirzi: *Infini-gram: Scaling Unbounded n-gram Language Models to a Trillion Tokens*, COLM 2024, arXiv 2401.17377. Code: `github.com/liujch1998/infini-gram`, Apache-2.0.
- **Warum:** Bei uns ist das Zählmodell das stärkste Modell. Ein n-Gramm ohne feste Länge greift auf den längsten passenden Kontext zurück, mit Backoff. Der Suffix-Array eines Korpus von 0,5 MB ist auch auf dem Telefon klein.
- **Übernahme:** nur die Idee. Suffix-Arrays sind Lehrbuchstoff, der Code wird selbst geschrieben.
- **Nächster Schritt:** NF-8 auf NFC2 VALIDATION vorregistrieren: ∞-Gramm gegen KN-7, allein und als Mischpartner. Dazu Speicher und Zeit je Byte messen.

**(b) Netzsperre des Node-Permission-Models nutzen, wo verfügbar (E5)**
- **Quelle:** Node.js-Dokumentation zu `--allow-net`: hinzugefügt in v25.0.0, Stabilität 1.1. Ohne den Schalter verweigert `--permission` den Netzzugriff mit `ERR_ACCESS_DENIED`.
- **Übernahme:** Laufzeitfunktion, kein Fremdcode. Die Vorprüfung übergibt `--allow-net` nie. Ab Node 25 greift die Sperre also zusätzlich zur eigenen Laufzeitsperre.
- **Nächster Schritt:** auf dem Gerät die Node-Version unter Termux prüfen und einen Test ergänzen, der unter Node ≥ 25 beide Sperren nachweist.

**(c) Rückfrage als Ja/Nein-Testvorschlag (E3)**
- **Quelle:** Lahiri, Fakhoury u. a.: *Interactive Code Generation via Test-Driven User-Intent Formalization*, arXiv 2208.05950 (TiCoder). Erweiterte Fassung mit Nutzerstudie: arXiv 2404.10100.
- **Warum:** Heute fragt der Agent „Welches Ergebnis ist gewollt?“, und der Besitzer muss ein Beispiel tippen. Ein vorgeschlagener Test, etwa `(1.5) → "1.5"?`, lässt sich mit einem Tipp bestätigen oder ablehnen.
- **Übernahme:** nur die Idee.
- **Nächster Schritt:** Neben dem ABSTAIN-Text kommt ein strukturiertes Feld `proposedTests`. Die Knöpfe in der App baut Astra; die Schnittstelle ist in der Übergabe beschrieben.

**(d) Selbst erzeugte, prüfbare Aufgaben aus dem Code-Index (E6)**
- **Quelle:** eigene Idee auf Basis des Aufgaben-Miners aus NF-5c und des Code-Index. Verwandt: AlphaCode gruppiert Kandidatenprogramme nach ihrem Verhalten (Li u. a., *Competition-level code generation with AlphaCode*, Science 2022, doi 10.1126/science.abq1158).
- **Warum:** Für eine lernende Kandidatenrangfolge fehlen geprüfte Beispiele. Aus Fingerabdrücken lassen sich Aufgaben mit verdecktem Funktionsnamen erzeugen, die der echte Coding-Job selbst prüft.
- **Übernahme:** eigener Code.
- **Nächster Schritt:** einen Generator mit neuer Datentrennung bauen. Gelernt wird ausschließlich aus Quittungen.

### Priorität 2

**(e) Residual-Training gegen das Zählmodell (E1)**
- **Stand:** NF-7 ist gelaufen, beide Regeln sind **widerlegt** (NF-Bericht, Abschnitt 10.11). Das Residual-Training macht die Mischung um 0,010–0,017 BPB schlechter als ein unabhängig trainiertes Netz. Deshalb wird es nicht weiterverfolgt.
- **Quelle:** Li, Cai, Xu, Watanabe: *N-gram Is Back: Residual Learning of Neural Text Generation with n-gram Language Model*, Findings of EMNLP 2022, S. 1523–1533, arXiv 2210.14431.
- **Lizenz:** Das Repository `ghrua/NgramRes` nennt keine Lizenz. Deshalb nur die Idee, eigener Code.

**(f) Mehrdeutigkeit durch Ausführung erkennen (E3)**
- **Stand:** in NF-5b und NF-5c umgesetzt und eigens gemessen.
- **Quellen:**
  - Mu u. a.: *ClarifyGPT: A Framework for Enhancing LLM-based Code Generation via Requirements Clarification*, FSE 2024, arXiv 2310.10996. Mehrere Lösungen laufen auf erzeugten Eingaben; weichen die Ausgaben ab, wird nachgefragt.
  - Jha u. a., ICSE 2010 (unterscheidende Eingaben).
  - Verbruggen u. a., ICLR 2025 (ausführungsgeleitete Suche).
- **Unterschied bei uns:** Verglichen werden vorhandene Funktionen des Repositorys, keine erzeugten Lösungen. Bestätigt wird erst durch den unabhängigen Vertragstest.

**(g) Mischung vieler Kontextmodelle mit online gelerntem Mischer (E1)**
- **Quelle:** das Kompressionsprogramm cmix (Byron Knoll).
- **Lizenz:** GPL-3.0; laut Paketquellen uneinheitlich, ob „only“ oder „or later“. Kein Code wird übernommen; ob GPL zur App-Lizenz passt, ist ungeklärt.
- **Nächster Schritt:** frühestens nach NF-8. In NF-6b-lite brachte ein einfaches, gelerntes Gate keinen Gewinn gegenüber der festen Mischung.

### Zurückgestellt
- **ARM64-Messungen (E7):** brauchen das Gerät, also eine APK von Astra oder Termux. Gemessen werden sollen die LM1-Runtime, die Vorprüfung und der Code-Index.
- **Große Sprachmodelle (≥ 1 Mrd. Parameter):** nicht auf dem Gerät. Kostenpflichtige externe KI-APIs bleiben ausgeschlossen.
- **Sprachausgabe (E2):** erst angehen, wenn E1 ein Modell liefert, das das Zählmodell schlägt.

## 3. Ausdrücklich nicht übernommen
- fertige Coding-Agenten (Vorgabe des Besitzers);
- Code ohne Lizenzangabe (NgramRes) und GPL-Code (cmix).
