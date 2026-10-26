import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Independent, pre-implementation authored oracles. No model or candidate import.
const directory = path.dirname(fileURLToPath(import.meta.url));
const templates = [];
const add = (name, category, query, records, expected, scope = 'in_scope', extra = {}) =>
  templates.push({name, category, query, records, expected, scope, ...extra});

add('named-home', 'same_subject_relation', 'Wo wohnt Lethra?', [
  ['home', 'Lethra wohnt in Norenau.'],
  ['work', 'Lethra arbeitet in Farnwinkel.'],
  ['other', 'Kovrin wohnt in Schilfdorf.'],
], ['home'], 'in_scope', {legacyPositiveControl: true});
add('inverted-home', 'inverted_positive', 'Wo wohnt Kovrin?', [
  ['home', 'In Farnwinkel wohnt Kovrin.'],
], ['home']);
add('lives-synonym', 'paraphrased_positive', 'Wo lebt Nelvo?', [
  ['home', 'Nelvo wohnt in Seidenhof.'],
  ['work', 'Nelvo arbeitet in Mohnried.'],
], ['home']);
add('named-work', 'same_subject_relation', 'Wo arbeitet Marzel?', [
  ['work', 'Marzel arbeitet in Weidenfels.'],
  ['home', 'Marzel wohnt in Halmstedt.'],
  ['other', 'Zudra arbeitet in Schilfdorf.'],
], ['work'], 'in_scope', {legacyPositiveControl: true});
add('inverted-work', 'inverted_positive', 'Wo arbeitet Besrin?', [
  ['work', 'In Wiesenkranz arbeitet Besrin.'],
], ['work']);
add('work-adverb', 'paraphrased_positive', 'Wo arbeitet Sordel?', [
  ['work', 'Sordel arbeitet beruflich in Rohrmark.'],
], ['work']);
add('unknown-name', 'unknown_subject', 'Wo wohnt Derpin?', [
  ['other', 'Welsin wohnt in Hauchdorf.'],
  ['work', 'Derpin arbeitet in Silbertor.'],
], []);
add('short-id-prefix', 'short_identifier', 'Wo wohnt Q7?', [
  ['exact', 'Q7 wohnt in Tannhafen.'],
  ['suffix', 'Q70 wohnt in Sandwacht.'],
  ['prefix', 'AQ7 wohnt in Halmstedt.'],
], ['exact']);
add('hyphen-id', 'hyphen_identifier', 'Wo wohnt NEX-58?', [
  ['exact', 'NEX-58 wohnt in Kieselort.'],
  ['collapsed', 'NEX58 wohnt in Blattheim.'],
  ['underscore', 'NEX_58 wohnt in Mohnried.'],
], ['exact']);
add('underscore-id', 'underscore_identifier', 'Wo arbeitet TAL_93?', [
  ['exact', 'TAL_93 arbeitet in Erlenwacht.'],
  ['collapsed', 'TAL93 arbeitet in Hauchdorf.'],
  ['hyphen', 'TAL-93 arbeitet in Kieselort.'],
], ['exact']);
add('mixed-id', 'mixed_identifier', 'Wo wohnt rU6x?', [
  ['exact', 'rU6x wohnt in Flockenau.'],
  ['confusable', 'rU6y wohnt in Tannhafen.'],
  ['suffix', 'rU6x9 wohnt in Sandwacht.'],
], ['exact']);
add('single-letter', 'short_identifier', 'Wo wohnt J?', [
  ['exact', 'J wohnt in Dornwiese.'],
  ['suffix', 'JK wohnt in Schilfdorf.'],
  ['prefix', 'AJ wohnt in Mohnried.'],
], ['exact']);
add('two-identifiers', 'all_explicit_identifiers', 'Wo wohnt ADEL-74 im Vorgang BLATT_96?', [
  ['both', 'ADEL-74 wohnt in Hauchdorf. Dies gilt für den Vorgang BLATT_96.'],
  ['wrong-context', 'ADEL-74 wohnt in Kieselort. Dies gilt für den Vorgang BLATT_97.'],
  ['wrong-subject', 'ADEL-75 wohnt in Erlenwacht. Dies gilt für den Vorgang BLATT_96.'],
], ['both']);
add('split-identifiers', 'all_explicit_identifiers', 'Wo arbeitet VEX-17 im Auftrag STROM_61?', [
  ['subject-only', 'VEX-17 arbeitet in Moosfeld. Dies gilt für den Auftrag STROM_60.'],
  ['context-only', 'VEX-18 arbeitet in Dornwiese. Dies gilt für den Auftrag STROM_61.'],
], []);
add('negation-only', 'negated_relation', 'Wo wohnt Ulven?', [
  ['negative', 'Ulven wohnt nicht in Lerchenort.'],
], []);
add('negated-with-distractors', 'negated_relation', 'Wo wohnt Q5?', [
  ['negative', 'Q5 wohnt nicht in Seidenhof.'],
  ['other', 'Q6 wohnt in Rohrmark.'],
  ['wrong-relation', 'Q5 arbeitet in Silbertor.'],
], []);
add('positive-and-negative', 'negation_distractor', 'Wo wohnt Felra?', [
  ['positive', 'Felra wohnt in Talgruen.'],
  ['negative', 'Felra wohnt nicht in Rosenanger.'],
  ['other', 'Selvin wohnt in Rosenanger.'],
], ['positive']);
add('home-does-not-prove-work', 'relation_mismatch', 'Wo arbeitet Torven?', [
  ['home', 'Torven wohnt in Reetwacht.'],
  ['other', 'Velnik arbeitet in Reetwacht.'],
], []);
add('work-does-not-prove-home', 'relation_mismatch', 'Wo wohnt Veldra?', [
  ['work', 'Veldra arbeitet in Flockenau.'],
  ['other', 'Kenril wohnt in Flockenau.'],
], []);
add('same-record-wrong-binding', 'subject_relation_binding', 'Wo wohnt C8?', [
  ['wrong-binding', 'D4 wohnt in Sommerborn. C8 arbeitet in Sommerborn.'],
  ['other', 'C80 wohnt in Norenau.'],
], []);
add('two-qualified-hits', 'multiple_positive_records', 'Wo lebt Nedrin?', [
  ['direct', 'Nedrin lebt in Silbertor.'],
  ['inverted', 'In Silbertor wohnt Nedrin.'],
  ['other', 'Nedron lebt in Blattheim.'],
], ['direct', 'inverted']);
add('three-identifiers', 'all_explicit_identifiers', 'Wo arbeitet KEL-29 im Projekt HAFEN_84 fuer die Phase P6?', [
  ['all', 'KEL-29 arbeitet in Grauweide. Projekt HAFEN_84, Phase P6.'],
  ['wrong-phase', 'KEL-29 arbeitet in Grauweide. Projekt HAFEN_84, Phase P60.'],
  ['wrong-project', 'KEL-29 arbeitet in Grauweide. Projekt HAFEN_840, Phase P6.'],
], ['all']);
add('home-wohnhaft', 'paraphrased_positive', 'Wo lebt NF_48?', [
  ['home', 'NF_48 ist in Windanger wohnhaft.'],
  ['other', 'NF_480 ist in Blattheim wohnhaft.'],
], ['home']);
add('work-negated-other-clause', 'negation_distractor', 'Wo arbeitet Felsin?', [
  ['positive', 'Felsin arbeitet in Blauheide. Felsin wohnt nicht in Blauheide.'],
  ['negative', 'Felsin arbeitet nicht in Mondhafen.'],
], ['positive']);

// Separate challenge denominator: these are intentionally beyond direct
// subject/relation matching and must remain visible even if unsupported.
add('definition', 'definition_challenge', 'Was bedeutet Eldritbogen?', [
  ['definition', 'Ein Eldritbogen ist ein erfundenes Messgeraet fuer Blattwinkel.'],
  ['other', 'Ein Nebelrad ist ein erfundenes Transportgeraet.'],
], ['definition'], 'challenge', {legacyPositiveControl: true});
add('unresolved-pronoun', 'pronoun_challenge', 'Wo wohnt sie?', [
  ['unbound', 'Telsa wohnt in Glimmerau.'],
], [], 'challenge');
add('resolved-pronoun', 'pronoun_challenge', 'Gemeint ist Ruvin. Wo wohnt sie?', [
  ['home', 'Ruvin wohnt in Tauwinkel.'],
], ['home'], 'challenge');
add('house-chain', 'multihop_challenge', 'In welchem Ort steht das Haus, das Wurdel besitzt?', [
  ['chain', 'Wurdel besitzt das Haus FAR_71. Das Haus FAR_71 steht in Windklee.'],
], ['chain'], 'challenge');
add('alias-chain', 'multihop_challenge', 'Wo wohnt der Bote?', [
  ['chain', 'Der Bote heisst Zirlek. Zirlek wohnt in Uferkamm.'],
], ['chain'], 'challenge');
add('elimination', 'negation_entailment_challenge', 'Wo wohnt Omril?', [
  ['elimination', 'Omril wohnt entweder in Lerchental oder in Taumoos, aber nicht in Lerchental.'],
], ['elimination'], 'challenge');

const cases = templates.flatMap((template, index) => ['USER_FACT', 'SOURCE_TEXT'].map(kind => {
  const prefix = kind === 'USER_FACT' ? 'UF' : 'ST';
  const memories = template.records.map(([label, text]) => ({
    id: `${prefix}-${String(index + 1).padStart(2, '0')}-${label}`, kind, text,
    provenance: {synthetic: true, fixtureVersion: 1},
  }));
  const expectedIds = template.expected.map(label => memories.find(m => m.id.endsWith(`-${label}`)).id).sort();
  return {
    id: `${prefix}-${String(index + 1).padStart(2, '0')}-${template.name}`,
    scope: template.scope, category: template.category, kind,
    polarity: expectedIds.length === 0 ? 'negative' : expectedIds.length === memories.length ? 'positive' : 'mixed',
    query: template.query, memories, expectedIds,
    expectedSourceTermEvidenceById: Object.fromEntries(memories.map(m => [m.id, expectedIds.includes(m.id)])),
    legacyPositiveControl: template.legacyPositiveControl === true,
  };
}));
const fixture = {
  schema: 'CONTINUUM_INDEPENDENT_GROUNDING_DECISIONS_V1',
  fixtureId: 'grounding-subject-relation-de-v1-20261026',
  authoredAtUtc: '2026-10-26T02:51:09Z',
  baselineSha: 'c391b83244bd2cc945a1b37bcdd829da27e15ba6',
  candidateSha: '4bf74b1b827aab56bbe03e032c21c03c508484c8',
  state: 'FROZEN_BEFORE_CANDIDATE_IMPLEMENTATION_INSPECTION',
  syntheticOnly: true,
  authoredWithout: ['candidate implementation', 'candidate predictions', 'model inference', 'private owner data'],
  inputContract: 'rankMemories(query, memories, {semantic:null, limit:allInputRecords}); memory fields id,kind,text; no semantic model or learned weights.',
  decisionContract: [
    'Judge the sorted SET of returned record IDs, not ranking order or generated answers.',
    'For in-scope location/work questions a qualified record must positively state the requested subject AND requested relation. Residence and work are separate relations.',
    'Identifiers are exact whole identifiers: added prefixes/suffixes and changed hyphen/underscore spelling identify different subjects. Mixed letter case is preserved in both query and matching evidence; no case-only distinction is tested.',
    'All explicitly requested identifiers must appear in the same qualified record. Matching one requested identifier across separate records is insufficient.',
    'Negation-only location/work statements do not positively establish the requested location and therefore do not qualify. An unrelated negated clause must not suppress a positive requested relation.',
    'Multiple records that each qualify must all be returned; use a limit at least the number of input records.',
    'USER_FACT cases are synthetic already-authorized owner assertions. SOURCE_TEXT cases are untrusted factual evidence with no tool authority. This benchmark does not test authorization or ownership validation.',
    'Definition, pronoun and multihop/negation-entailment controls are separately labeled challenge and excluded from the in-scope headline. Publish their complete predictions and failures anyway.',
    'Direct sourceTermEvidence is evaluated as a separate per-record diagnostic, not substituted for rankMemories.',
    'Capture full hit objects and scores for both revisions. Compare raw objects on predeclared legacyPositiveControl cases; report disagreements without tuning fixtures or candidate.',
  ],
  scoring: {
    exact: 'JSON string equality of sorted unique expectedIds and sorted returned IDs, with no semantic credit or hidden discarded cases.',
    falseHit: 'Each returned ID outside expectedIds; report total and per case.',
    missedPositive: 'Each expected ID absent from returned IDs; report total and per case.',
    positiveRetention: 'For each revision, expected IDs returned / all expected IDs; also report candidate retention of baseline true-positive IDs.',
    errors: 'Capture exceptions as ERROR, preserve them in total-case denominator and do not convert them into empty predictions.',
    split: 'Publish in_scope and challenge separately, plus kind/category/polarity breakdowns and complete raw predictions.',
  },
  cases,
};
if (cases.length !== 60 || new Set(cases.map(c => c.id)).size !== cases.length) throw Error('Fixture case count/identity drift');
for (const c of cases) {
  if (c.memories.length > 3 || !c.memories.every(m => ['USER_FACT', 'SOURCE_TEXT'].includes(m.kind))) throw Error('Invalid records');
  if (c.expectedIds.some(id => !c.memories.some(m => m.id === id))) throw Error('Invalid oracle ID');
  if (/ORION|Mira|KIRA|Luro|Tavo/.test(c.query + JSON.stringify(c.memories))) throw Error('Consumed entity');
}
fs.writeFileSync(path.join(directory, 'grounding-decision-fixtures-v1.json'), JSON.stringify(fixture, null, 2) + '\n');
