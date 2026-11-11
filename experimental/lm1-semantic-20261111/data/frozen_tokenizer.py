"""Independent frozen-tokenizer diagnostic; native parity remains mandatory."""
from __future__ import annotations

import hashlib
from pathlib import Path
import struct
import unicodedata
import zipfile

TOKENIZER_SHA256 = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"


def pretokenize(text):
    words, index = [], 0
    while index < len(text):
        start = index
        if text[index] == " " and index + 1 < len(text) and not text[index + 1].isspace():
            index += 1
        category = unicodedata.category(text[index])[0]
        if category == "L":
            index += 1
            while index < len(text) and unicodedata.category(text[index])[0] == "L":
                index += 1
        elif category == "N":
            index += 1
        elif not text[index].isspace():
            index += 1
            while index < len(text) and not text[index].isspace() and unicodedata.category(text[index])[0] not in {"L", "N"}:
                index += 1
        else:
            index += 1
            while index < len(text) and text[index].isspace():
                index += 1
        words.append(text[start:index])
    return words


class Tokenizer:
    def __init__(self, data):
        if hashlib.sha256(data).hexdigest() != TOKENIZER_SHA256:
            raise ValueError("tokenizer identity mismatch")
        magic, count = struct.unpack_from(">ii", data)
        if magic != 0x4C4D4250 or len(data) != 8 + 8 * count:
            raise ValueError("tokenizer format mismatch")
        self.merges = [struct.unpack_from(">ii", data, 8 + 8 * i) for i in range(count)]
        self.rank = {pair: i for i, pair in enumerate(self.merges)}
        self.bos, self.eos = 256 + count, 257 + count
        self.cache = {}

    def encode(self, text):
        output = []
        for word in pretokenize(text):
            if word in self.cache:
                output.extend(self.cache[word])
                continue
            symbols = list(word.encode())
            while len(symbols) > 1:
                ranks = [self.rank.get(pair, 10**9) for pair in zip(symbols, symbols[1:])]
                best = min(ranks)
                if best == 10**9:
                    break
                pair, following, index = self.merges[best], [], 0
                while index < len(symbols):
                    if index + 1 < len(symbols) and tuple(symbols[index:index + 2]) == pair:
                        following.append(256 + best)
                        index += 2
                    else:
                        following.append(symbols[index])
                        index += 1
                symbols = following
            self.cache[word] = tuple(symbols)
            output.extend(symbols)
        return output


def load_tokenizer(repo: Path):
    archive = repo / "experimental/weight-space-20261110/artifacts/resume-checkpoints.zip"
    with zipfile.ZipFile(archive) as source:
        data = source.read("original132k/tokenizer.lmbp")
    return Tokenizer(data)
