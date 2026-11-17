// CRE1 external-style relational challenge v2.
// Hand-authored on 2026-10-16 BEFORE first model evaluation. Never used for
// training, DEV threshold calibration or automatic product activation.
// This is a fixed synthetic challenge, not evidence of open-domain language AI.
import crypto from 'node:crypto';
export const CRE_CHALLENGE_VERSION = 2;
export const MEMORIES = Object.freeze([
 {id:'c2-selin-birthday',subject:'Selin',relation:'birthday',text:'Selin hat am 8. Februar Geburtstag.'},
 {id:'c2-emir-residence',subject:'Emir',relation:'residence',text:'Emir ist vor kurzem nach Lustenau gezogen.'},
 {id:'c2-frida-job',subject:'Frida',relation:'job',text:'Frida verdient ihr Geld als Floristin.'},
 {id:'c2-nora-phone',subject:'Nora',relation:'phone',text:'Nora ist unter 0711 882 940 erreichbar.'},
 {id:'c2-milan-food',subject:'Milan',relation:'food',text:'Milan isst am liebsten Gemüselasagne.'},
 {id:'c2-ida-allergy',subject:'Ida',relation:'allergy',text:'Ida verträgt keine Sesamsamen.'},
 {id:'c2-dario-pet',subject:'Dario',relation:'pet',text:'Dario hält einen Terrier namens Fips.'},
 {id:'c2-yara-car',subject:'Yara',relation:'car',text:'Yara fährt einen Honda Civic.'},
 {id:'c2-rafael-hobby',subject:'Rafael',relation:'hobby',text:'Rafael verbringt seine Freizeit mit Klettern.'},
 {id:'c2-zora-language',subject:'Zora',relation:'language',text:'Zora spricht fließend Dänisch.'},
 {id:'c2-hausschluessel-storage',subject:'Haustürschlüssel',relation:'storage',text:'Den Haustürschlüssel bewahre ich in der grünen Dose beim Bett auf.'},
 {id:'c2-alma-residence',subject:'Alma',relation:'residence',text:'Alma lebt in Perchtoldsdorf.'},
 {id:'c2-kenan-birthday',subject:'Kenan',relation:'birthday',text:'Kenan ist am 27. November geboren.'},
 {id:'c2-livia-job',subject:'Livia',relation:'job',text:'Livia arbeitet als Biochemikerin.'},
 {id:'c2-luis-car',subject:'Luis',relation:'car',text:'Das Auto von Luis ist ein Dacia Sandero.'},
 {id:'c2-rike-allergy',subject:'Rike',relation:'allergy',text:'Rike hat eine Allergie gegen Haselnüsse.'}
]);
const pairs=[
 ['c2-selin-birthday','An welchem Kalendertag kam Selin zur Welt?','Welches Datum ist für den Geburtstag von Selin notiert?'],
 ['c2-emir-residence','Wo hat Emir nach dem Umzug sein neues Zuhause?','An welchem Ort ist Emir inzwischen sesshaft geworden?'],
 ['c2-frida-job','Welche Erwerbstätigkeit übt Frida aus?','Wodurch bestreitet Frida ihren Lebensunterhalt?'],
 ['c2-nora-phone','Unter welcher Rufnummer kann man Nora kontaktieren?','Welche Zahlen wähle ich, um Nora telefonisch zu erreichen?'],
 ['c2-milan-food','Mit welchem Essen macht man Milan eine besondere Freude?','Welches Gericht bevorzugt Milan beim Mittagessen?'],
 ['c2-ida-allergy','Welche Zutat muss Ida wegen einer Unverträglichkeit meiden?','Wovon bekommt Ida möglicherweise eine allergische Reaktion?'],
 ['c2-dario-pet','Wie lautet der Rufname von Dario seinem Haustier?','Was für einen tierischen Begleiter besitzt Dario?'],
 ['c2-yara-car','Welches Fahrzeugmodell hat Yara angeschafft?','Welche Automarke steht bei Yara in der Einfahrt?'],
 ['c2-rafael-hobby','Welche Beschäftigung macht Rafael zum Ausgleich nach der Arbeit?','Womit verbringt Rafael am liebsten seine freie Zeit?'],
 ['c2-zora-language','Über welche Sprachkenntnisse verfügt Zora?','Welche Sprache beherrscht Zora mühelos?'],
 ['c2-hausschluessel-storage','An welchem Ort ist der Haustürschlüssel verstaut?','Wo kann ich den Haustürschlüssel wiederfinden?'],
 ['c2-alma-residence','Wo befindet sich derzeit der Wohnsitz von Alma?','In welcher Gemeinde hat Alma ihr Zuhause?'],
 ['c2-kenan-birthday','An welchem Datum kam Kenan auf die Welt?','Wann hat Kenan seinen Ehrentag?'],
 ['c2-livia-job','Was ist Livias hauptberufliche Tätigkeit?','In welcher Branche ist Livia beruflich tätig?'],
 ['c2-luis-car','Welche Art Wagen fährt Luis?','Welches Automodell gehört Luis?'],
 ['c2-rike-allergy','Welche Nussart darf Rike nicht vertragen?','Auf welches Nahrungsmittel reagiert Rike allergisch?']
];
export const QUESTIONS=Object.freeze(pairs.flatMap(([target,...forms])=>
 forms.map((text,index)=>({text,target,form:'heldout-form',variant:index+1}))));
export const UNANSWERABLE=Object.freeze([
 {text:'Welches Haustier besitzt Selin?',target:null},
 {text:'Wo wohnt Frida?',target:null},
 {text:'Welche Automarke fährt Emir?',target:null},
 {text:'Wann feiert Nora Geburtstag?',target:null},
 {text:'Welche Telefonnummer hat Rike?',target:null},
 {text:'Womit verdient Alma ihren Lebensunterhalt?',target:null},
 {text:'Was ist das Lieblingsessen von Kenan?',target:null},
 {text:'Spricht Dario fließend Deutsch?',target:null},
 {text:'Wo lebt Alina?',target:null},
 {text:'Wann hat Tarik Geburtstag?',target:null},
 {text:'Was kostet die nächste Zugfahrt nach Wien?',target:null},
 {text:'Wie schnell rotiert der Mars um seine Achse?',target:null}
]);
export const CHALLENGE=Object.freeze({
 memories:MEMORIES,questions:QUESTIONS,unanswerable:UNANSWERABLE
});
export const FINGERPRINT=crypto.createHash('sha256')
 .update(JSON.stringify(CHALLENGE)).digest('hex');
