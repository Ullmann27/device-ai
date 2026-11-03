#!/usr/bin/env python3
"""Author-owned development examples, never described as independent or blind."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'docs/evidence/breakthrough-neural-20261103/development'

def digest(text):
    return hashlib.sha256(text.encode()).hexdigest()

def utf16(text):
    return len(text.encode('utf-16-le')) // 2

def create():
    cases = []
    def add(category, question, texts, answer=None, index=0):
        cid = f'NEURALDEV{len(cases)+1:03d}'
        sources = [dict(sourceId=f'{cid}-s{i}', revision=i+1, text=text, sha256=digest(text))
                   for i, text in enumerate(texts)]
        expected = None
        if answer:
            s = sources[index]
            start = s['text'].index(answer)
            expected = {k: s[k] for k in ['sourceId', 'revision', 'sha256']}
            expected.update(text=answer, start=utf16(s['text'][:start]), endExclusive=utf16(s['text'][:start]+answer))
        cases.append(dict(id=cid, split='ADVERSARIAL', category=category, question=question,
                          sources=sources, expected=expected))
    for person, other, city, elsewhere in [('Névara', 'Qerim', 'Sølvik', 'Bréval'),
                                         ('Želina', 'Þórin', 'Łomira', 'Dun-Mara')]:
        add('unknown_names', f'Wo wohnt {person}?', [f'{person} wohnt in {city}.'], city)
        add('role_swap', f'Wo wohnt {person}?', [f'{other} wohnt in {city}. {person} wohnt in {elsewhere}.'], elsewhere)
        add('unknown_entity', f'Wo wohnt {person}?', [f'{other} wohnt in {city}.'])
        add('entity_as_location', f'Wo wohnt {person}?', [f'{other} wohnt in {person}.'])
        add('mixed_relation', f'Wo wohnt {person}?', [f'{person} lebt in {city}. {other} wohnt in {elsewhere}.'])
        add('conflicting_sources', f'Wo wohnt {person}?', [f'{person} wohnt in {city}.', f'{person} wohnt in {elsewhere}.'])
        add('explicit_correction', f'Wo wohnt {person}?', [f'{person} wohnt in {city}. Korrektur: {person} wohnt in {elsewhere}.'], elsewhere)
        add('explicit_revocation', f'Wo wohnt {person}?', [f'{person} wohnt in {city}. Diese Aussage wurde widerrufen.'])
        add('negative', f'Wo wohnt {person}?', [f'{person} wohnt nicht in {city}.'])
        add('unrelated_negative_source', f'Wo wohnt {person}?', [f'{person} wohnt in {city}.', f'{other} wohnt nicht in {elsewhere}.'], city)
        add('unrelated_negative_clause', f'Wo wohnt {person}?', [f'{person} wohnt in {city}. {other} wohnt nicht in {elsewhere}.'], city)
        add('question_as_evidence', f'Wo wohnt {person}?', [f'{person} wohnt in {city}?'])
        add('past_only', f'Wo wohnt {person}?', [f'{person} wohnte früher in {city}.'])
        add('unicode_city', f'Wo wohnt {person}?', [f'{person} wohnt in {city} Nord.'], f'{city} Nord')
        add('natural_word_order', f'Wo wohnt {person}?', [f'In {city} wohnt {person}.'], city)
        add('natural_question', f'In welchem Ort wohnt {person}?', [f'{person} wohnt in {city}.'], city)
        add('apostrophe_city', f'Wo wohnt {person}?', [f"{person} wohnt in L’{city}."], f'L’{city}')
        add('lowercase_place_connector', f'Wo wohnt {person}?', [f'{person} wohnt in {city} am See.'], f'{city} am See')
        add('four_word_place', f'Wo wohnt {person}?', [f'{person} wohnt in Neu Groß {city} Nord.'], f'Neu Groß {city} Nord')
        add('unicode_offset_prefix', f'Wo wohnt {person}?', [f'🧭. {person} wohnt in {city}.'], city)
    controls = []
    for mutation in ['hash_mismatch', 'text_changed', 'revision_zero', 'duplicate_source_id']:
        source = dict(sourceId='corruption-a', revision=1, text='Névara wohnt in Sølvik.', sha256=digest('Névara wohnt in Sølvik.'))
        ss = [source]
        if mutation == 'hash_mismatch': source['sha256'] = '0'*64
        elif mutation == 'text_changed': source['text'] = 'Névara wohnt in Bréval.'
        elif mutation == 'revision_zero': source['revision'] = 0
        else: ss.append(dict(source))
        controls.append(dict(id='CORRUPTION_'+mutation, question='Wo wohnt Névara?', sources=ss))
    return dict(cases=cases), dict(cases=controls)

if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=False)
    corpus, controls = create()
    files = {'corpus.json': corpus, 'inputs.json': {'cases': [{k:c[k] for k in ['id','question','sources']} for c in corpus['cases']]},
             'corruption-inputs.json': controls}
    for name, value in files.items():
        (OUT/name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n')
    manifest = dict(schema='BREAKTHROUGH_NEURAL_DEVELOPMENT_V1', author='neural implementer',
                    contamination='AUTHOR_KNOWN_DEVELOPMENT_ONLY', cases=len(corpus['cases']),
                    semantics='Exact source spans for explicit corrected/latest statements; unresolved conflicts abstain. Natural-language positives may exceed frozen static grammar.',
                    hashes={name: hashlib.sha256((OUT/name).read_bytes()).hexdigest() for name in files})
    (OUT/'FREEZE.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(manifest, indent=2))
