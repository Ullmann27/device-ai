/**
 * Author-written SWB1 transfer probes frozen before any score computation.
 * Kept strictly separate from TRAIN/VALIDATION/AUDIT; for reporting only.
 * Label refers to phrase equivalence, not truth of a fetched web page.
 */
export const TRANSFER_SUITE_V1=Object.freeze({
  intent:Object.freeze([
  {
    "text": "schön dich hier zu sehen",
    "label": 0
  },
  {
    "text": "einen herzlichen guten abend zusammen",
    "label": 0
  },
  {
    "text": "hi miteinander",
    "label": 0
  },
  {
    "text": "grüße von hier",
    "label": 0
  },
  {
    "text": "hey ihr zwei",
    "label": 0
  },
  {
    "text": "willkommen in unserer runde",
    "label": 0
  },
  {
    "text": "hallo an diesem schönen tag",
    "label": 0
  },
  {
    "text": "guten morgen lieber gast",
    "label": 0
  },
  {
    "text": "ich wünsche einen guten abend",
    "label": 0
  },
  {
    "text": "freut mich dich zu begrüßen",
    "label": 0
  },
  {
    "text": "warum wird mein akku plötzlich heiß",
    "label": 1
  },
  {
    "text": "welcher commit enthält die korrektur",
    "label": 1
  },
  {
    "text": "kann diese sicherung wiederhergestellt werden",
    "label": 1
  },
  {
    "text": "wann wird das gerät geladen",
    "label": 1
  },
  {
    "text": "wie lassen sich tests wiederholen",
    "label": 1
  },
  {
    "text": "ist das ergebnis ausreichend belegt",
    "label": 1
  },
  {
    "text": "welche rechte braucht das programm",
    "label": 1
  },
  {
    "text": "wohin wurden die dateien kopiert",
    "label": 1
  },
  {
    "text": "warum verlangsamt sich der arbeitsprozess",
    "label": 1
  },
  {
    "text": "lässt sich die antwort überprüfen",
    "label": 1
  }
]),
  semantic:Object.freeze([
  {
    "left": "programm wird aktualisiert",
    "right": "die anwendung erhält eine neuere version",
    "similar": true
  },
  {
    "left": "datei wird gesichert",
    "right": "eine kopie schützt die gespeicherten daten",
    "similar": true
  },
  {
    "left": "server antwortet nicht",
    "right": "der dienst ist aktuell nicht erreichbar",
    "similar": true
  },
  {
    "left": "test erkennt einen fehler",
    "right": "eine prüfung findet eine falsche berechnung",
    "similar": true
  },
  {
    "left": "benutzer stimmt zu",
    "right": "die person hat die aktion ausdrücklich genehmigt",
    "similar": true
  },
  {
    "left": "strom ist ausgefallen",
    "right": "die versorgung mit elektrischer energie ist unterbrochen",
    "similar": true
  },
  {
    "left": "zug fährt zu spät ab",
    "right": "der abfahrtstermin des zuges verzögert sich",
    "similar": true
  },
  {
    "left": "wasser gefriert",
    "right": "flüssiges wasser wird bei kälte zu eis",
    "similar": true
  },
  {
    "left": "gerät wird heiß",
    "right": "die temperatur der hardware steigt deutlich",
    "similar": true
  },
  {
    "left": "konto hat keine berechtigung",
    "right": "der zugriff auf die geschützten daten wird verweigert",
    "similar": true
  },
  {
    "left": "antwort ist ungewiss",
    "right": "für die behauptung liegen keine sicheren belege vor",
    "similar": true
  },
  {
    "left": "modell wird zurückgesetzt",
    "right": "die bisher gelernten gewichte werden neu initialisiert",
    "similar": true
  },
  {
    "left": "speicher ist voll",
    "right": "für zusätzliche daten ist nicht genug platz vorhanden",
    "similar": true
  },
  {
    "left": "nachricht wird verschlüsselt",
    "right": "der text wird vor unbefugtem lesen geschützt",
    "similar": true
  },
  {
    "left": "termin wird verschoben",
    "right": "die vereinbarte uhrzeit wird auf später verlegt",
    "similar": true
  },
  {
    "left": "sicherung wird geprüft",
    "right": "die gespeicherte kopie wird auf unversehrtheit kontrolliert",
    "similar": true
  },
  {
    "left": "programm wird aktualisiert",
    "right": "der computer verwendet ein anderes netzkabel",
    "similar": false
  },
  {
    "left": "datei wird gesichert",
    "right": "die datei wird ohne rückfrage entfernt",
    "similar": false
  },
  {
    "left": "server antwortet nicht",
    "right": "der dienst liefert die angeforderten daten",
    "similar": false
  },
  {
    "left": "test erkennt einen fehler",
    "right": "eine prüfung bestätigt alle ergebnisse",
    "similar": false
  },
  {
    "left": "benutzer stimmt zu",
    "right": "die person lehnt den vorgang ausdrücklich ab",
    "similar": false
  },
  {
    "left": "strom ist ausgefallen",
    "right": "alle steckdosen liefern weiterhin spannung",
    "similar": false
  },
  {
    "left": "zug fährt zu spät ab",
    "right": "die bahn startet genau nach fahrplan",
    "similar": false
  },
  {
    "left": "wasser gefriert",
    "right": "das eis verwandelt sich in flüssiges wasser",
    "similar": false
  },
  {
    "left": "gerät wird heiß",
    "right": "die hardware kühlt rasch auf raumtemperatur ab",
    "similar": false
  },
  {
    "left": "konto hat keine berechtigung",
    "right": "der nutzer darf sämtliche daten öffnen",
    "similar": false
  },
  {
    "left": "antwort ist ungewiss",
    "right": "die behauptung wurde mehrfach zuverlässig bestätigt",
    "similar": false
  },
  {
    "left": "modell wird zurückgesetzt",
    "right": "der vorhandene trainingsstand bleibt unverändert",
    "similar": false
  },
  {
    "left": "speicher ist voll",
    "right": "es steht sehr viel ungenutzter speicher bereit",
    "similar": false
  },
  {
    "left": "nachricht wird verschlüsselt",
    "right": "der text wird öffentlich im klartext angezeigt",
    "similar": false
  },
  {
    "left": "termin wird verschoben",
    "right": "der termin findet unverändert wie geplant statt",
    "similar": false
  },
  {
    "left": "sicherung wird geprüft",
    "right": "die sicherungsdatei wird ungeprüft gelöscht",
    "similar": false
  }
])
});
