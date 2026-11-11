#!/usr/bin/env python3
"""Frozen train-only shortcut baselines."""

import collections
import hashlib
import re

import numpy as np
from sklearn.feature_extraction.text import CountVectorizer, TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import make_pipeline
from sklearn.tree import DecisionTreeClassifier

SEED = 20261111
ACTION = re.compile(r"nichts tun|tu gar nichts|öffn(?:e|en)|such(?:e|en)|speichern|erstelle|notier(?:e|en)", re.I)


def action(text, last=False):
    matches = ACTION.findall(text)
    if not matches:
        return "0"
    hit = matches[-1 if last else 0].casefold()
    if hit in ("nichts tun", "tu gar nichts"):
        return "0"
    if hit.startswith("öffn"):
        return "1"
    if hit.startswith("such"):
        return "2"
    return "3"


def lengths(prompt, tokenizer, include_negation=False):
    result = [len(prompt.encode()), len(prompt), len(re.findall(r"\w+", prompt)),
              len(tokenizer.encode("Auftrag: " + prompt + "\n")) + 1]
    if include_negation:
        result.append(len(re.findall(r"\bnicht\b", prompt.casefold())))
    return result


def predict(train, cases, tokenizer):
    assert train and cases
    x = [row["prompt"] for row in train]
    y = [row["label"] for row in train]
    z = [row["prompt"] for row in cases]
    counts = collections.Counter(y)
    majority = min(counts, key=lambda label: (-counts[label], label))
    result = {"constant_" + str(i): [str(i)] * len(z) for i in range(4)}
    result.update({
        "train_majority": [majority] * len(z),
        "legacy_nicht_filter": ["0" if "nicht" in text.casefold() else "3" for text in z],
        "primitive_negation": ["0" if re.search(r"\b(?:nicht|kein\w*|nichts|niemals|nie)\b", text.casefold()) else "3" for text in z],
        "negation_parity": ["0" if len(re.findall(r"\bnicht\b", text.casefold())) % 2 else "3" for text in z],
        "first_action": [action(text) for text in z],
        "last_action": [action(text, True) for text in z],
    })
    models = {
        "word_unigram_nb": make_pipeline(CountVectorizer(token_pattern=r"(?u)\b\w+\b"), MultinomialNB(alpha=1.0)),
        "word_bigram_nb": make_pipeline(CountVectorizer(ngram_range=(1, 2), token_pattern=r"(?u)\b\w+\b"), MultinomialNB(alpha=1.0)),
        "char_2_4_nb": make_pipeline(CountVectorizer(analyzer="char", ngram_range=(2, 4)), MultinomialNB(alpha=1.0)),
        "pilot_word_unigram_logistic": make_pipeline(CountVectorizer(token_pattern=r"(?u)\b\w+\b"), LogisticRegression(C=1.0, max_iter=1000, random_state=SEED)),
        "pilot_word_bigram_logistic": make_pipeline(TfidfVectorizer(ngram_range=(1, 2), token_pattern=r"(?u)\b\w+\b"), LogisticRegression(C=1.0, max_iter=1000, random_state=SEED)),
        "pilot_char_ngram_logistic": make_pipeline(TfidfVectorizer(analyzer="char", ngram_range=(3, 5)), LogisticRegression(C=1.0, max_iter=1000, random_state=SEED)),
    }
    fits = {}
    for name, model in models.items():
        model.fit(x, y)
        result[name] = list(map(str, model.predict(z)))
        vectorizer, estimator = model.steps[0][1], model.steps[1][1]
        coefficients = estimator.coef_ if hasattr(estimator, "coef_") else estimator.feature_log_prob_
        fitted = coefficients.tobytes() + str(sorted(vectorizer.vocabulary_.items())).encode()
        fits[name] = {"trainRows": len(train), "features": len(vectorizer.vocabulary_),
                      "coefficientVocabularySha256": hashlib.sha256(fitted).hexdigest()}
    a = np.asarray([lengths(text, tokenizer) for text in x], dtype=float)
    b = np.asarray([lengths(text, tokenizer) for text in z], dtype=float)
    scale = a.std(axis=0)
    scale[scale == 0] = 1
    centers = np.stack([a[np.asarray(y) == label].mean(axis=0) for label in "0123"])
    distances = (((b[:, None, :] - centers) / scale) ** 2).sum(axis=2)
    result["length_centroid"] = [str(i) for i in distances.argmin(axis=1)]
    tree = DecisionTreeClassifier(max_depth=3, random_state=SEED)
    tree.fit([lengths(text, tokenizer, True) for text in x], y)
    result["pilot_length_depth3_tree"] = list(map(str, tree.predict([lengths(text, tokenizer, True) for text in z])))
    fits["length_centroid"] = {"trainOnlyScale": scale.tolist(), "trainOnlyCenters": centers.tolist()}
    fits["pilot_length_depth3_tree"] = {"maxDepth": 3, "randomState": SEED, "trainRows": len(train)}
    assert all(len(values) == len(cases) and all(v in "0123" and len(v) == 1 for v in values) for values in result.values())
    return result, fits
