// Small authored probes. Validation selects checkpoints; AUDIT is report-only.
const intents=(greetings,questions)=>[...greetings.map(text=>({text,label:0})),...questions.map(text=>({text,label:1}))];
const pairs=(positive,negative)=>[...positive.map(([left,right])=>({left,right,similar:true})),...negative.map(([left,right])=>({left,right,similar:false}))];
export const TRAIN={
 intent:intents(
 ['hallo','servus miteinander','guten morgen','schönen abend euch','grüß dich','willkommen zurück','guten tag mein freund','hallo zusammen','servus heinz','liebe grüße','guten abend zusammen','herzlich willkommen','schönen guten morgen','hey guten tag','moin mein freund','grüße an alle','mahlzeit zusammen','hallo lieber freund','servus und willkommen','guten morgen miteinander'],
 ['was bedeutet das','wie funktioniert das','kannst du mir helfen','warum ist das so','wo liegt wien','wann beginnt der sommer','welche sprache sprichst du','wie lernt ein neuronales netz','woher kommt der wind','was weißt du über pflanzen','kann ich eine frage stellen','warum brauchen wir licht','welcher weg führt nach hause','wie lange dauert das','was kostet die reparatur','wann kommt der bus','wo finde ich die datei','wie speichert man erinnerungen','welche quelle belegt das','kannst du das erklären']),
 semantic:pairs([
 ['pflanze licht energie','pflanzen nutzen sonnenlicht für energie'],['motor fahrzeug bewegung','antrieb bewegt ein fahrzeug'],['sprache grammatik','grammatik beschreibt sprachliche strukturen'],['musik melodie klang','melodien und rhythmus in der musik'],['planet sterne universum','sterne und planeten im weltraum'],['gehirn lernen gedächtnis','erinnerungen entstehen durch lernen'],['wasser fluss meer','flüsse transportieren wasser zum meer'],['baum wald blätter','bäume mit blättern wachsen im wald'],['computer daten speicher','ein rechner speichert informationen'],['replay neuronales lernen','ältere beispiele beim training wiederholen'],['katze haustier fell','ein tier mit fell miaut'],['vogel flügel fliegen','vögel bewegen sich mit flügeln'],['haus mauer dach','ein gebäude besitzt wände und ein dach'],['brot mehl backen','aus mehl entsteht gebackenes brot'],['wärme temperatur energie','hohe temperatur bedeutet thermische energie'],['lesen buch geschichte','ein roman erzählt eine geschichte']
 ],[
 ['pflanze licht energie','motor getriebe fahrzeug'],['astronomie planeten','grammatik satzbau'],['musik harmonie','plattentektonik erdbeben'],['katze haustier fell','computer daten speicher'],['wasser fluss meer','lesen buch geschichte'],['baum wald blätter','motor fahrzeug bewegung'],['vogel flügel fliegen','brot mehl backen'],['haus mauer dach','replay neuronales lernen'],['musik melodie klang','computer daten speicher'],['planet sterne universum','brot mehl backen'],['gehirn lernen gedächtnis','haus mauer dach'],['wärme temperatur energie','sprache grammatik'],['computer daten speicher','baum wald blätter'],['lesen buch geschichte','wasser fluss meer'],['motor fahrzeug bewegung','katze haustier fell'],['brot mehl backen','vogel flügel fliegen']
 ])
};
export const VALIDATION={
 intent:intents(['moin zusammen','einen schönen tag','herzliche grüße','mahlzeit allerseits','hallo ihr lieben','guten morgen euch allen','schönen abend miteinander','servus lieber heinz','willkommen mein freund','grüß euch'],['ist morgen feiertag','dürfen wir starten','welche zeit ist es','woher kommt silizium','wie entstehen erinnerungen','warum scheint die sonne','was bedeutet gedächtnis','wann beginnt die schule','wo liegt österreich','kannst du diese frage beantworten']),
 semantic:pairs([
 ['sonne licht pflanzen','pflanzen brauchen licht'],['fahrzeug antrieb','motor bewegt fahrzeug'],['wörter sätze sprache','sprachliche sätze und wörter'],['klavier melodien','musik mit melodien'],['sterne im all','planeten im universum'],['lernen und erinnern','gedächtnis und erinnerungen'],['wasser im fluss','fluss mündet ins meer'],['wald und bäume','bäume tragen blätter'],['rechner und daten','computer speichert daten'],['training mit replay','alte lernbeispiele wiederholen']
 ],[
 ['sonne pflanzen','getriebe automobil'],['musik rhythmus','planet umlaufbahn'],['zelle biologie','roman literatur'],['miauen und katzen','rechner und daten'],['wasser im fluss','klavier melodien'],['wald und bäume','fahrzeug antrieb'],['vögel am himmel','brot im backofen'],['wände und dächer','training mit replay'],['sterne im all','mehl und teig'],['lernen und erinnern','wasser im fluss']
 ])
};
export const AUDIT={
 intent:intents(['hallo und einen guten morgen','liebe grüße an dich','servus ihr alle','schönen guten abend','willkommen bei uns','guten tag zusammen','hey hallo miteinander','grüß dich mein freund','mahlzeit ihr lieben','moin und willkommen'],['wie kann ich dir etwas beibringen','wo bleiben die erinnerungen','warum braucht eine pflanze wasser','was ist ein sprachmuster','wann startet dein training','welches thema untersuchst du','kannst du meinen text verstehen','woher kennst du die antwort','wie zuverlässig ist die quelle','was passiert beim lernen']),
 semantic:pairs([
 ['pflanzen wandeln licht um','sonnenenergie für grüne pflanzen'],['fahrzeuge mit motoren','ein motor treibt ein auto an'],['grammatik der wörter','sprache mit grammatischen regeln'],['melodien auf instrumenten','harmonische klänge und musik'],['ferne planeten und sterne','das universum voller sterne'],['erlernte erinnerungen','im gedächtnis behaltenes wissen'],['fließendes wasser','wasser in einem flussbett'],['blätter der waldbäume','bäume bilden einen wald'],['gespeicherte computerdaten','informationen im rechnerspeicher'],['wiederholung früherer beispiele','replay von alten trainingsdaten']
 ],[
 ['pflanzen wandeln licht um','gespeicherte computerdaten'],['fahrzeuge mit motoren','melodien auf instrumenten'],['grammatik der wörter','ferne planeten und sterne'],['erlernte erinnerungen','fließendes wasser'],['blätter der waldbäume','fahrzeuge mit motoren'],['gebackener brotteig','ferne planeten und sterne'],['miauende haustiere','grammatik der wörter'],['flügel eines vogels','gespeicherte computerdaten'],['mauern eines gebäudes','wiederholung früherer beispiele'],['melodien auf instrumenten','blätter der waldbäume']
 ])
};

// Existing Android probes are a protected validation slice, never training data.
export const ANDROID_VALIDATION={
 intent:VALIDATION.intent.filter((_,i)=>i<4||(i>=10&&i<14)),
 semantic:VALIDATION.semantic.filter((_,i)=>[0,1,2,10,11,12].includes(i))
};
