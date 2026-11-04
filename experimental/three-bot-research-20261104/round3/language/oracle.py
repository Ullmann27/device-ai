#!/usr/bin/env python3
"""Independent text interpreter for one valid owner binding, without a learned model."""
from __future__ import annotations

import re

PATTERNS = {
    'owner_late': re.compile(r'Der Gegenstand ([A-Za-zÄÖÜäöüß]+) gehört Person ([ABCD])\.\nBesitzer:'),
    'owner_early': re.compile(r'Person ([ABCD]) besitzt den Gegenstand ([A-Za-zÄÖÜäöüß]+)\.\nBesitzer:'),
}


def interpret(prompt: str) -> dict:
    if not isinstance(prompt, str) or len(prompt) > 240:
        raise ValueError('invalid prompt envelope')
    matched = []
    for template, pattern in PATTERNS.items():
        match = pattern.fullmatch(prompt)
        if match:
            obj, owner = match.groups() if template == 'owner_late' else tuple(reversed(match.groups()))
            matched.append({'object': obj, 'owner': owner, 'template': template,
                            'lastMentionedPerson': owner, 'bindingCount': 1})
    if len(matched) != 1:
        raise ValueError('not exactly one recognized binding')
    return matched[0]


def verify(prompt: str, answer: str) -> dict:
    parsed = interpret(prompt)
    if answer not in 'ABCD' or len(answer) != 1 or parsed['owner'] != answer:
        raise ValueError('wrong owner label')
    return parsed


def self_check() -> dict:
    positive = [
        ('Der Gegenstand Tasse gehört Person A.\nBesitzer:', 'A'),
        ('Der Gegenstand Gürtel gehört Person D.\nBesitzer:', 'D'),
        ('Person B besitzt den Gegenstand Buch.\nBesitzer:', 'B'),
        ('Person C besitzt den Gegenstand Würfel.\nBesitzer:', 'C'),
    ]
    for prompt, answer in positive:
        verify(prompt, answer)
    negative = [
        (positive[0][0], 'B'), (positive[1][0], 'DD'),
        ('Der Gegenstand Tasse gehört Person E.\nBesitzer:', 'A'),
        ('Person A besitzt den Gegenstand Buch. Besitzer:', 'A'),
        ('Person A besitzt den Gegenstand Buch.\nBesitzer: A', 'A'),
        ('Person A besitzt den Gegenstand Buch\nBesitzer:', 'A'),
        ('Person A besitzt den Gegenstand Buch. Person B besitzt den Gegenstand Tasse.\nBesitzer:', 'A'),
        ('Person A besitzt den Gegenstand Buch.\nBesitzer:\n', 'A'),
    ]
    for prompt, answer in negative:
        try:
            verify(prompt, answer)
        except ValueError:
            continue
        raise AssertionError('invalid example accepted')
    return {'positive': len(positive), 'negative': len(negative), 'passed': True}


if __name__ == '__main__':
    import json
    print(json.dumps(self_check(), indent=2))
