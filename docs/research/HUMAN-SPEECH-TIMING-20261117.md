# Zeitliche Struktur menschlicher Gespräche – Grundlage für das Sprechtiming (17.11.2026)

Zweck: Die Zeitwerte in `cognitive/HumanSpeechTiming.kt` stammen aus veröffentlichten Messungen statt aus
frei gewählten Verzögerungen. Diese Notiz hält fest, welcher Wert woher kommt und wie belastbar er ist.

Belegstufen: **wörtlich** = Zahl im gelesenen Originaltext gefunden · **Kurzfassung** = Zahl aus dem Abstract ·
**zweiter Hand** = Zahl nur als Zitat in einer anderen Arbeit gelesen · **nicht geprüft** = Original nicht lesbar.

## 1. Sprecherwechsel

| Größe | Wert | Quelle | Stufe |
|---|---|---|---|
| Typische Lücke beim Sprecherwechsel (Modus) | ca. 200 ms | Levinson & Torreira 2015, Abschn. 5.1 | wörtlich |
| Anteil der Wechsel unter 200 ms | 51–55 % | ebd., Abschn. 4 | wörtlich |
| Anteil der Wechsel unter 500 ms | 70–82 % | ebd., Abschn. 4/5.1 (nach Heldner & Edlund 2010) | wörtlich (dort zitiert) |
| Hauptbereich der Wechsel | −100 bis +500 ms | ebd., Abschn. 2 | wörtlich |
| Deutsch, geometrisches Mittel (Verbmobil) | 363 ms | ebd., Abschn. 5.1 (nach Weilhammer & Rabold 2003) | wörtlich (dort zitiert) |
| Lücke, ab der Ablehnung/Zögern gehört wird | ab 600–700 ms | ebd., Abschn. 2 (Roberts et al. 2011; Kendrick & Torreira 2015) | wörtlich (dort zitiert) |
| Lücke über der Norm senkt die Erwartung einer Zusage | > 300 ms | ebd., Abschn. 2 | wörtlich |
| Fortsetzung desselben Sprechers | ca. 140 ms (25 %) später als ein Sprecherwechsel | ebd., Abschn. 2 | wörtlich |
| Planung eines neuen Wortes | ca. 600 ms und mehr | ebd., Einleitung | wörtlich |
| Wahl zwischen zwei vorbereiteten Antworten | 350 ms | ebd., Abschn. 4 | wörtlich |
| Überlappungen | unter 5 % des Sprachstroms | ebd., Abschn. 4 | wörtlich |
| Sprachen liegen im Mittel dicht beieinander | innerhalb von 250 ms um den Gesamtmittelwert | Stivers et al. 2009 | Kurzfassung |

Folgerung der Autoren: Weil die Lücke (≈ 200 ms) kürzer ist als die Planungszeit (≥ 600 ms), beginnen Menschen
ihre Antwort zu planen, **während** der andere noch spricht.

## 2. Pausen innerhalb eines Redebeitrags

| Größe | Wert | Quelle | Stufe |
|---|---|---|---|
| Pausenklassen | kurz < 200 ms · mittel 200–1000 ms · lang > 1000 ms | Campione & Véronis 2002 | Kurzfassung |
| Lange Pausen | nur in spontaner, nicht in gelesener Sprache | ebd. | Kurzfassung |
| Verteilung | dreigipflig | ebd. | Kurzfassung |
| Feste Stille-Schwelle 500 ms als Äußerungsende | erfasst nur etwa die Hälfte der Wechsel, aber auch über die Hälfte der Denkpausen | Heldner & Edlund 2010 | zweiter Hand (Suchergebnis); Original **nicht geprüft** |

## 3. Sprechtempo

| Größe | Wert | Quelle | Stufe |
|---|---|---|---|
| Mittel über 17 Sprachen (gelesen) | 6,63 Silben/s, SD 1,15 | Coupé et al. 2019, Ergebnisse | wörtlich |
| Spanne einzelner Sprecher | ca. 4,3 bis 9,1 Silben/s | ebd., Einleitung | wörtlich |
| Informationsrate | 39,15 bit/s, SD 5,10 | ebd., Ergebnisse | wörtlich |
| Wert speziell für Deutsch | – | ebd. | **nicht gefunden** im gelesenen Text |

## 4. Was daraus im Code wurde

| Regel | Umsetzung | Begründung |
|---|---|---|
| Stille nach dem Nutzer nie länger als die Toleranzgrenze | erstes kurzes Lebenszeichen nach 450 ms, nur solange das Modell wirklich noch rechnet (`FIRST_CUE_DELAY_MS`, `TROUBLE_GAP_MS = 600`) | ab 600–700 ms wird Stille als Zögern gehört |
| Höchstens zwei Lebenszeichen | zweites nach 7 s, kein drittes | ein Füllwort signalisiert Verzögerung; mehr wäre Gerede |
| Pausen zwischen gesprochenen Teilen | 200–950 ms: Teilsatz ≈ 230, Satz ≈ 420, Frage ≈ 560, Absatz ≈ 780 ms | mittlere Pausenklasse; nie über 1 s, weil lange Pausen Zögern bedeuten |
| Pausen nie gleich lang | ±18 % deterministische Streuung | Menschen wiederholen keine Pausenlänge |
| Tempo folgt dem Ton | ±6 % um das Grundtempo | bleibt ein erkennbarer Sprecher, weit innerhalb der Sprecherspanne |
| Denkpause ist kein Satzende | endet der erkannte Text hörbar offen („… weil“, „… und“, „ähm“, offenes Komma), wird weiter zugehört und angefügt (`OwnerTurnCompletion`, höchstens zwei Fortsetzungen, 20 s) | eine feste Stille-Schwelle trennt Denkpausen und Gesprächsende nicht; lange Pausen gehören zur spontanen Sprache |

## 5. Grenzen und offene Punkte

1. Ein Mensch antwortet nach etwa 200 ms, weil er vorausplant. Das lokale Modell braucht Sekunden. Das
   Lebenszeichen überbrückt die Stille, verkürzt aber nicht die Zeit bis zur eigentlichen Antwort. Dafür wäre
   satzweises Sprechen während der Erzeugung nötig; das widerspricht derzeit der Regel, nur die geprüfte
   Endantwort zu sprechen, und ist deshalb eine Besitzerentscheidung.
2. Das Ende einer Nutzeräußerung erkennt die App weiterhin an 3 s Stille. Neu ist nur die Prüfung, ob der
   erkannte Text offen endet; sie ist bewusst eng (deutsche Sätze enden oft auf Verbpartikel oder Pronomen)
   und erkennt keine inhaltlich unfertigen Sätze mit geschlossenem Ende. Die 3 s selbst zu verkürzen wäre der
   größere Schritt zu einem flüssigen Wechsel, braucht aber Gerätetests.
3. Alle Werte stammen aus Gesprächen zwischen Menschen, teils aus anderen Sprachen und aus Telefon- oder
   Aufgabendialogen. Ob sie sich für eine Sprachausgabe im Auto oder am Telefon gut anfühlen, ist am Gerät
   nicht gemessen (NOT_RUN).
4. Mit der Online-Stimme (Maya) gelten Tempo, Pausen und Lebenszeichen nur eingeschränkt: Pausen zwischen
   Antwortteilen ja, Tempo und Lebenszeichen nein (kostenpflichtige Netzstimme mit eigener Latenz).

## Quellen

- Levinson, S. C. & Torreira, F. (2015). Timing in turn-taking and its implications for processing models of
  language. *Frontiers in Psychology* 6:731. https://pmc.ncbi.nlm.nih.gov/articles/PMC4464110
- Stivers, T. et al. (2009). Universals and cultural variation in turn-taking in conversation. *PNAS* 106(26),
  10587–10592. https://pubmed.ncbi.nlm.nih.gov/19553212/
- Campione, E. & Véronis, J. (2002). A large-scale multilingual study of silent pause duration. *Speech Prosody
  2002*, 199–202. https://www.isca-archive.org/speechprosody_2002/campione02_speechprosody.html
- Coupé, C., Oh, Y. M., Dediu, D. & Pellegrino, F. (2019). Different languages, similar encoding efficiency.
  *Science Advances* 5(9). https://pmc.ncbi.nlm.nih.gov/articles/PMC6984970
- Heldner, M. & Edlund, J. (2010). Pauses, gaps and overlaps in conversations. *Journal of Phonetics* 38(4),
  555–568. doi:10.1016/j.wocn.2010.08.002 (Original in dieser Runde nicht lesbar)
