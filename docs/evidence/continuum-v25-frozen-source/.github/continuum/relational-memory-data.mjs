// Personal-memory retrieval data: facts about people and things, asked about
// with different words than the stored fact uses ("Wann hat Anna Geburtstag?"
// -> "Anna ist am 3. Mai geboren."). Deterministic, synthetic, no private data.
//
// Splits never share people, objects or values:
//   TRAIN  training pairs (also bundled with the app as curriculum)
//   DEV    model and threshold selection
//   TEST   sealed: used only for the final measurement. Each TEST fact is asked
//          once with a question form seen in TRAIN and once with a held-out form
//          (QH) that never appears in TRAIN or DEV. Unanswerable questions ask for
//          a relation that is not stored, an unknown person or something unrelated.
import fs from 'node:fs';
import crypto from 'node:crypto';

// {P} person/thing, {V} value, {T} pet kind. F: fact forms, Q: question forms
// used in TRAIN/DEV/TEST, QH: question forms used ONLY in TEST.
export const RELATIONS = {
  birthday: {
    F: ['{P} hat am {V} Geburtstag.', '{P} ist am {V} geboren.', 'Der Geburtstag von {P} ist am {V}.', '{P} feiert jedes Jahr am {V}.'],
    Q: ['Wann hat {P} Geburtstag?', 'An welchem Tag wurde {P} geboren?', 'Wann feiert {P} Geburtstag?'],
    QH: ['Was ist das Geburtsdatum von {P}?', 'Wann ist {P} auf die Welt gekommen?']
  },
  residence: {
    F: ['{P} wohnt in {V}.', '{P} lebt seit Jahren in {V}.', 'Das Zuhause von {P} ist in {V}.', '{P} ist nach {V} gezogen.'],
    Q: ['Wo wohnt {P}?', 'In welcher Stadt lebt {P}?', 'Wo ist {P} zu Hause?'],
    QH: ['Wohin ist {P} umgezogen?', 'Was ist der Wohnort von {P}?']
  },
  job: {
    F: ['{P} arbeitet als {V}.', '{P} ist von Beruf {V}.', 'Beruflich ist {P} {V}.'],
    Q: ['Was arbeitet {P}?', 'Welchen Beruf hat {P}?', 'Was macht {P} beruflich?'],
    QH: ['Als was ist {P} tätig?', 'Womit verdient {P} sein Geld?']
  },
  phone: {
    F: ['Die Telefonnummer von {P} ist {V}.', '{P} ist unter {V} erreichbar.', 'Die Handynummer von {P} lautet {V}.'],
    Q: ['Wie kann ich {P} anrufen?', 'Unter welcher Nummer erreiche ich {P}?', 'Was ist die Nummer von {P}?'],
    QH: ['Wie erreiche ich {P} telefonisch?', 'Wie lautet der Anschluss von {P}?']
  },
  food: {
    F: ['{P} isst am liebsten {V}.', 'Das Lieblingsessen von {P} ist {V}.', '{P} mag {V} besonders gern.'],
    Q: ['Was isst {P} gern?', 'Welches Gericht mag {P} am liebsten?', 'Was soll ich für {P} kochen?'],
    QH: ['Was ist das Leibgericht von {P}?', 'Womit kann ich {P} beim Abendessen eine Freude machen?']
  },
  allergy: {
    F: ['{P} ist allergisch gegen {V}.', '{P} verträgt keine {V}.', '{P} hat eine Allergie gegen {V}.'],
    Q: ['Wogegen ist {P} allergisch?', 'Was verträgt {P} nicht?', 'Welche Allergie hat {P}?'],
    QH: ['Was darf {P} auf keinen Fall essen?', 'Worauf reagiert {P} empfindlich?']
  },
  pet: {
    F: ['{P} hat einen {T} namens {V}.', 'Der {T} von {P} heißt {V}.', '{P} hält einen {T}, der {V} heißt.'],
    Q: ['Wie heißt das Haustier von {P}?', 'Welches Tier hat {P}?', 'Was für ein Haustier besitzt {P}?'],
    QH: ['Wie heißt der Vierbeiner von {P}?', 'Welchen tierischen Mitbewohner hat {P}?']
  },
  car: {
    F: ['{P} fährt einen {V}.', 'Das Auto von {P} ist ein {V}.', '{P} ist mit einem {V} unterwegs.'],
    Q: ['Welches Auto fährt {P}?', 'Was für einen Wagen hat {P}?', 'Mit welchem Fahrzeug ist {P} unterwegs?'],
    QH: ['Welche Automarke fährt {P}?', 'Was steht bei {P} in der Garage?']
  },
  hobby: {
    F: ['{P} spielt in der Freizeit {V}.', 'Das Hobby von {P} ist {V}.', '{P} verbringt die Wochenenden mit {V}.'],
    Q: ['Was macht {P} in der Freizeit?', 'Welches Hobby hat {P}?', 'Womit verbringt {P} das Wochenende?'],
    QH: ['Wofür begeistert sich {P}?', 'Was unternimmt {P} nach Feierabend gern?']
  },
  language: {
    F: ['{P} spricht fließend {V}.', 'Die Muttersprache von {P} ist {V}.', '{P} kann sehr gut {V}.'],
    Q: ['Welche Sprache spricht {P}?', 'Was ist die Muttersprache von {P}?', 'In welcher Sprache kann ich mit {P} reden?'],
    QH: ['Welche Sprachkenntnisse hat {P}?', 'Mit welcher Sprache ist {P} aufgewachsen?']
  },
  storage: {
    F: ['{P} liegt in {V}.', '{P} bewahre ich in {V} auf.', '{P} befindet sich in {V}.'],
    Q: ['Wo ist {p}?', 'Wo finde ich {p}?', 'Wo habe ich {p} hingelegt?'],
    QH: ['Wo bewahre ich {p} auf?', 'Wo steckt {p}?']
  }
};

const SPLIT_VALUES = {
  train: {
    people: ['Anna', 'Lukas', 'Sophie', 'Jonas', 'Marie', 'Felix', 'Lea', 'Paul', 'Clara', 'Elias', 'Mia', 'Noah', 'Emma', 'Leon'],
    things: [['Der Reisepass', 'den Reisepass'], ['Der Ersatzschlüssel', 'den Ersatzschlüssel'], ['Die Taschenlampe', 'die Taschenlampe'], ['Das Ladekabel', 'das Ladekabel'], ['Der Werkzeugkasten', 'den Werkzeugkasten'], ['Die Garantiekarte', 'die Garantiekarte']],
    birthday: ['3. Mai', '17. August', '9. Januar', '28. Oktober', '12. März', '5. Juni', '21. Dezember'],
    residence: ['Linz', 'Graz', 'Salzburg', 'Innsbruck', 'Klagenfurt', 'Bregenz', 'Wels'],
    job: ['Lehrerin', 'Elektriker', 'Krankenpfleger', 'Architektin', 'Bäcker', 'Programmiererin', 'Tischler'],
    phone: ['0664 123 4567', '0676 555 0192', '0699 812 3344', '0650 217 9087', '0664 908 1123', '0681 404 2210', '0677 330 4512'],
    food: ['Spinatknödel', 'Gulasch', 'Lasagne', 'Kaiserschmarrn', 'Gemüsecurry', 'Wiener Schnitzel', 'Linsensuppe'],
    allergy: ['Erdnüsse', 'Pollen', 'Laktose', 'Katzenhaare', 'Hausstaub', 'Weizen', 'Nüsse'],
    pet: [['Hund', 'Bello'], ['Kater', 'Felix'], ['Hamster', 'Krümel'], ['Papagei', 'Coco'], ['Hund', 'Rex'], ['Kater', 'Mogli'], ['Hasen', 'Hoppel']],
    car: ['VW Golf', 'Skoda Octavia', 'Toyota Yaris', 'Ford Fiesta', 'Opel Corsa', 'Renault Clio', 'Seat Ibiza'],
    hobby: ['Schach', 'Tennis', 'Gitarre', 'Volleyball', 'Klavier', 'Fußball', 'Badminton'],
    language: ['Italienisch', 'Französisch', 'Spanisch', 'Kroatisch', 'Englisch', 'Türkisch', 'Polnisch'],
    storage: ['der obersten Schublade im Flur', 'dem Schrank im Schlafzimmer', 'der blauen Kiste im Keller', 'dem Regal in der Garage', 'der Kommode im Wohnzimmer', 'dem Handschuhfach']
  },
  dev: {
    people: ['Hannah', 'Ben', 'Laura', 'Tobias', 'Julia', 'Simon'],
    things: [['Der Impfpass', 'den Impfpass'], ['Die Fahrradpumpe', 'die Fahrradpumpe'], ['Das Nähzeug', 'das Nähzeug']],
    birthday: ['14. Februar', '30. Juli', '2. November'],
    residence: ['Steyr', 'Villach', 'Leoben'],
    job: ['Polizistin', 'Koch', 'Zahnärztin'],
    phone: ['0664 771 2290', '0699 140 5562', '0676 903 7781'],
    food: ['Pizza', 'Käsespätzle', 'Fischsuppe'],
    allergy: ['Sellerie', 'Bienenstiche', 'Soja'],
    pet: [['Hund', 'Luna'], ['Kater', 'Tiger'], ['Hamster', 'Pünktchen']],
    car: ['Mazda 3', 'Kia Ceed', 'Hyundai i30'],
    hobby: ['Klettern', 'Schwimmen', 'Malen'],
    language: ['Ungarisch', 'Portugiesisch', 'Tschechisch'],
    storage: ['dem Kasten im Vorzimmer', 'der Lade unter dem Fernseher', 'dem Karton am Dachboden']
  },
  test: {
    people: ['Theresa', 'Moritz', 'Valentina', 'Florian', 'Katharina', 'Dominik', 'Magdalena', 'Sebastian'],
    things: [['Der Mietvertrag', 'den Mietvertrag'], ['Die Ersatzbrille', 'die Ersatzbrille'], ['Das Fieberthermometer', 'das Fieberthermometer'], ['Der Zweitschlüssel fürs Auto', 'den Zweitschlüssel fürs Auto']],
    birthday: ['6. April', '23. September', '11. Juli', '19. November'],
    residence: ['Krems', 'Dornbirn', 'Eisenstadt', 'Kufstein'],
    job: ['Physiotherapeutin', 'Installateur', 'Steuerberaterin', 'Busfahrer'],
    phone: ['0664 309 8812', '0676 220 4471', '0699 556 1209', '0650 813 6624'],
    food: ['Marillenknödel', 'Ramen', 'Tafelspitz', 'Falafel'],
    allergy: ['Penicillin', 'Kiwi', 'Meeresfrüchte', 'Eier'],
    pet: [['Hund', 'Balu'], ['Kater', 'Simba'], ['Papagei', 'Kiki'], ['Hasen', 'Flocke']],
    car: ['BMW 3er', 'Peugeot 208', 'Volvo V60', 'Fiat Panda'],
    hobby: ['Yoga', 'Fotografie', 'Segeln', 'Tischtennis'],
    language: ['Japanisch', 'Rumänisch', 'Slowenisch', 'Griechisch'],
    storage: ['der Schreibtischlade im Arbeitszimmer', 'dem Safe im Kleiderschrank', 'der Holzkiste auf dem Balkon', 'dem Fach neben der Haustür']
  }
};

/** 32-bit LCG, identical in Kotlin (deterministic data and training). */
export class Lcg {
  constructor(seed) { this.s = seed >>> 0; }
  next() { this.s = (Math.imul(this.s, 1664525) + 1013904223) >>> 0; return this.s / 4294967296; }
  int(n) { return Math.min(n - 1, Math.floor(this.next() * n)); }
  pick(xs) { return xs[this.int(xs.length)]; }
  shuffle(xs) { const a = xs.slice(); for (let i = a.length - 1; i > 0; i--) { const j = this.int(i + 1); [a[i], a[j]] = [a[j], a[i]]; } return a; }
}

const fill = (t, p, v, k) => t.replaceAll('{P}', p).replaceAll('{p}', p).replaceAll('{V}', v).replaceAll('{T}', k ?? '');
const PERSON_RELATIONS = Object.keys(RELATIONS).filter(r => r !== 'storage');

function buildSplit(name, seed, factForms, questionCount) {
  const values = SPLIT_VALUES[name], rng = new Lcg(seed);
  const used = Object.fromEntries(Object.keys(RELATIONS).map(r => [r, 0]));
  const takeValue = r => { const list = values[r]; const v = list[used[r] % list.length]; used[r]++; return v; };
  const memories = [], questions = [], unanswerable = [];
  const stored = new Map();
  for (const person of values.people) {
    const relations = rng.shuffle(PERSON_RELATIONS).slice(0, 4 + rng.int(2));
    stored.set(person, new Set(relations));
    for (const relation of relations) {
      const raw = takeValue(relation), [kind, value] = Array.isArray(raw) ? raw : [null, raw];
      const forms = factForms(relation);
      const text = fill(rng.pick(forms), person, value, kind);
      const id = `${name}-${person.toLowerCase()}-${relation}`;
      memories.push({ id, text, subject: person, relation, value });
      for (const q of questionCount(relation, rng)) questions.push({ text: fill(q.form, person, value, kind), target: id, form: q.kind, relation });
    }
    const missing = PERSON_RELATIONS.filter(r => !stored.get(person).has(r));
    if (missing.length) {
      const relation = rng.pick(missing);
      unanswerable.push({ text: fill(rng.pick(RELATIONS[relation].Q), person, ''), target: null, form: 'unstored-relation', relation });
    }
  }
  for (const [cap, lower] of values.things) {
    const value = takeValue('storage');
    const text = fill(rng.pick(factForms('storage')), cap, value);
    const id = `${name}-${lower.split(' ').slice(1).join('-').toLowerCase()}-storage`;
    memories.push({ id, text, subject: lower, relation: 'storage', value });
    for (const q of questionCount('storage', rng)) questions.push({ text: fill(q.form, lower, value), target: id, form: q.kind, relation: 'storage' });
  }
  return { memories, questions, unanswerable };
}

const UNKNOWN = {
  dev: ['Wo wohnt Bernhard?', 'Welches Auto fährt Gerlinde?', 'Wie geht es meiner Tante in Kanada?', 'Wie wird das Wetter morgen?', 'Wer hat die Fußball-WM 1954 gewonnen?'],
  test: ['Wann hat Rupert Geburtstag?', 'Was arbeitet Ingrid?', 'Wo ist meine Bohrmaschine?', 'Wie spät ist es in Tokio?', 'Wie hoch ist der Großglockner?', 'Welche Farbe hat der Himmel bei Sonnenuntergang?']
};

export function buildData() {
  const train = buildSplit('train', 101, r => RELATIONS[r].F.slice(0, 2),
    r => RELATIONS[r].Q.map(form => ({ form, kind: 'train-form' })));
  const dev = buildSplit('dev', 202, r => RELATIONS[r].F,
    (r, rng) => rng.shuffle(RELATIONS[r].Q).slice(0, 2).map(form => ({ form, kind: 'seen-form' })));
  const test = buildSplit('test', 303, r => RELATIONS[r].F,
    (r, rng) => [{ form: rng.pick(RELATIONS[r].Q), kind: 'seen-form' }, { form: rng.pick(RELATIONS[r].QH), kind: 'heldout-form' }]);
  for (const [split, list] of [['dev', dev], ['test', test]])
    for (const text of UNKNOWN[split]) list.unanswerable.push({ text, target: null, form: 'unknown', relation: null });
  return { schema: 1, train, dev, test };
}

export const fingerprint = value => crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');

export const CURRICULUM_FILE = 'app/src/main/assets/continuum/relational-curriculum-v1.json';
export const EVAL_FILE = 'app/src/test/resources/continuum/retrieval/relational-eval-v1.json';

export function files() {
  const data = buildData();
  const curriculum = { schema: 1, name: 'relational-curriculum-v1', synthetic: true,
    memories: data.train.memories.map(({ id, text }) => ({ id, text })),
    pairs: data.train.questions.map(({ text, target }) => ({ query: text, memory: target })) };
  const evaluation = { schema: 1, name: 'relational-eval-v1', synthetic: true, dev: data.dev, test: data.test,
    sealed: { testFingerprint: fingerprint(data.test), note: 'TEST is used only for the final measurement, never for model or threshold selection.' } };
  return { curriculum, evaluation };
}

if (process.argv[1]?.endsWith('relational-memory-data.mjs')) {
  const { curriculum, evaluation } = files();
  if (process.argv[2] === 'write') {
    for (const [file, value] of [[CURRICULUM_FILE, curriculum], [EVAL_FILE, evaluation]]) {
      fs.mkdirSync(file.slice(0, file.lastIndexOf('/')), { recursive: true });
      fs.writeFileSync(file, JSON.stringify(value, null, 1) + '\n');
    }
  }
  const d = buildData();
  for (const s of ['train', 'dev', 'test'])
    console.log(s, 'memories', d[s].memories.length, 'questions', d[s].questions.length, 'unanswerable', d[s].unanswerable.length);
}
