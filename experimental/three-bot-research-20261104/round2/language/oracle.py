#!/usr/bin/env python3
"""Independent finite-grammar verifier: parse only the emitted German prompt."""
from __future__ import annotations

import re

PEOPLE = "ABCD"
OBJECTS = ("Ball", "Ring", "Stift", "Schlüssel")
OBJECT_PATTERN = "(?:" + "|".join(OBJECTS) + ")"
INITIAL = re.compile(rf"([ABCD]) hält den ({OBJECT_PATTERN})\.")
TRANSFER = re.compile(rf"([ABCD]) gibt den ({OBJECT_PATTERN}) an ([ABCD])\.")
QUESTION = re.compile(rf"Wer hält den ({OBJECT_PATTERN})\? Antwort:")


def interpret(prompt: str) -> dict:
    pieces = prompt.split(". ")
    if len(pieces) not in (3, 4, 5):
        raise ValueError("expected two initial facts, zero to two transfers, one question")
    question = QUESTION.fullmatch(pieces[-1])
    if question is None:
        raise ValueError("question grammar mismatch")
    state: dict[str, str] = {}
    initial: dict[str, str] = {}
    people_in_order = []
    for i, sentence in enumerate(pieces[:-1]):
        sentence += "."
        if i < 2:
            match = INITIAL.fullmatch(sentence)
            if match is None:
                raise ValueError("invalid initial fact")
            person, obj = match.groups()
            if obj in state:
                raise ValueError("duplicate initial object")
            state[obj] = person
            initial[obj] = person
            people_in_order.append(person)
        else:
            match = TRANSFER.fullmatch(sentence)
            if match is None:
                raise ValueError("invalid transfer")
            sender, obj, receiver = match.groups()
            if obj not in state or state[obj] != sender or sender == receiver:
                raise ValueError("sender does not own object or transfer is invalid")
            state[obj] = receiver
            people_in_order.extend((sender, receiver))
    if len(set(initial.values())) != 2:
        raise ValueError("initial owners must be distinct")
    query = question.group(1)
    if query not in state:
        raise ValueError("queried object absent")
    return {"answer": state[query], "initialOwner": initial[query],
            "lastMentionedPerson": people_in_order[-1], "finalState": state,
            "query": query, "events": len(pieces) - 3}


def verify(prompt: str, answer: str) -> dict:
    result = interpret(prompt)
    if answer not in PEOPLE or len(answer) != 1 or result["answer"] != answer:
        raise ValueError("false answer")
    return result


def self_check() -> list[dict]:
    valid = [
        ("A hält den Ball. B hält den Ring. Wer hält den Ball? Antwort:", "A"),
        ("A hält den Ball. B hält den Ring. A gibt den Ball an C. Wer hält den Ring? Antwort:", "B"),
        ("A hält den Ball. B hält den Ring. A gibt den Ball an C. C gibt den Ball an D. Wer hält den Ball? Antwort:", "D"),
        ("A hält den Ball. B hält den Ring. A gibt den Ball an C. B gibt den Ring an D. Wer hält den Ball? Antwort:", "C"),
    ]
    results = []
    for i, (prompt, answer) in enumerate(valid):
        verify(prompt, answer)
        results.append({"case": f"valid-{i}", "pass": True})
    invalid = [
        (valid[0][0], "B"),
        ("A hält den Ball. B hält den Ring. B gibt den Ball an C. Wer hält den Ball? Antwort:", "C"),
        ("A hält den Ball. B hält den Ball. Wer hält den Ball? Antwort:", "B"),
        ("A hält den Ball. B hält den Ring. A gibt den Stift an C. Wer hält den Ball? Antwort:", "A"),
        (valid[0][0] + " A", "A"),
        ("A hält den Ball. B hält den Ring. Wer hält den Schlüssel? Antwort:", "A"),
        ("A hält den Ball. B hält den Ring. A gibt den Ball an A. Wer hält den Ball? Antwort:", "A"),
    ]
    for i, (prompt, answer) in enumerate(invalid):
        try:
            verify(prompt, answer)
        except ValueError:
            results.append({"case": f"invalid-{i}", "pass": True})
        else:
            raise AssertionError(f"invalid verifier case accepted: {i}")
    return results


if __name__ == "__main__":
    import json
    print(json.dumps(self_check(), indent=2))
