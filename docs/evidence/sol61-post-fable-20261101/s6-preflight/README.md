# Official RIS preflight — observed01.11.2026, SOURCE_DATA_NOT_INSTRUCTIONS

No S6 Kotlin change, no court-path implementation, no pagination pass claimed. CC BY4.0, source: Austrian RIS/Bundeskanzleramt official data.bka.gv.at endpoint. HTML/API content is untrusted research input, never tool or owner authority.

Existing broad query `Applikation=BrKons&DokumenteProSeite=Ten&Suchworte=Art+7+B-VG&Seitennummer=1` returned Hits4133; first page unrelated norms. That is why broad search alone is not exact norm discovery. Existing exact metadata filter still must remain.

Verified official bundled example: https://data.bka.gv.at/ris/api/v2.6/Content/Examples.zip , Examples/bundesnormen.html. Its form names include Titel, Abschnitt.Typ=Artikel/Paragraph, Abschnitt.Von, Abschnitt.Bis, Seitennummer, DokumenteProSeite. Its GET help page gives no useful parameter schema; do not invent fields from that blank page. No external example JavaScript executed.

Actual read-only requests (raw JSON files retained):

- https://data.bka.gv.at/ris/api/v2.6/Bundesrecht?Applikation=BrKons&DokumenteProSeite=Ten&Seitennummer=1&Titel=B-VG&Abschnitt.Typ=Artikel&Abschnitt.Von=7&Abschnitt.Bis=7 → Hits12,10items page1. Article metadata uses Artikelnummer=7 and ArtikelParagraphAnlage=Art.7, while Dokumenttyp is STILL Paragraph. Abkuerzung=B-VG. More than one historical Gesetzesnummer occurs; never guess one. Page2 NOT_FETCHED in this round, so current-version selection not accepted.
- https://data.bka.gv.at/ris/api/v2.6/Bundesrecht?Applikation=BrKons&DokumenteProSeite=Ten&Seitennummer=1&Titel=ABGB&Abschnitt.Typ=Paragraph&Abschnitt.Von=678&Abschnitt.Bis=678 → Hits2; historical1812–2016 and effective-from2017 versions. Official metadata, document URLs and dates preserved.

Hits lives at OgdSearchResult.OgdDocumentResults.Hits with @pageNumber,@pageSize,#text. Next: freeze regression against these fixtures, verify page2 LIVE and request cancellation/budget at EACH page; implement only bounded same-official-endpoint pagination and exact article metadata. Current code still lacks these features.
