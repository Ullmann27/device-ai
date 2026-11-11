# Personal voice learning — data contract and diagnostic corpus

This directory extends the existing LM1 research infrastructure at PR #728. It
contains **204 newly authored public synthetic text records**: 128 contrast
TRAIN, 32 open DEV, 32 supervised open/search replay, and 12 rich pipeline
examples. No owner conversation, family information, microphone recording or
external model output was collected or used. These records are not evidence of
the owner's actual speech distribution or Android ASR error rate.

## Two different corrections

`recognized_transcript` holds the ASR hypothesis, or explicitly synthetic text.
`corrected_transcript` holds a separately confirmed correction of the *words*.
`meaning.intended_instruction` and structured actions hold the confirmed
*meaning*. A correction of words cannot automatically confirm the interpretation.
For example, changing a mistaken application name is different from deciding
which of two earlier development branches the user meant. Ambiguous examples
require clarification and have no executable action target.

The record also preserves prior turns, task references, expected actions and
parameters, forbidden interpretations, source, sensitivity review and consent.
The structural JSON schema is supplemented by `validate_record`; schema
validation alone never authorizes training or export.

## Privacy and review gate

Synthetic fixtures have authored provenance and no owner-data permission claim.
Personal records require `owner_corrected` provenance, private-local encrypted
storage, separate text-training and audio-retention grants, owner meaning review
and a consent digest bound to the exact wording, meaning, audio and context.
Changing any protected content invalidates the old consent. Text consent never
authorizes audio; another person's voice requires its own permission. A personal
record is refused by the public exporter even if its cloud-export flag is true.

These checks are a data admission contract, **not a microphone recorder, Android
Keystore implementation, UI consent screen or automatic sensitive-content
classifier**. Unreviewed sensitive content cannot become an eligible training
example. Declaring data synthetic cannot anonymize real personal data. Capture,
review/edit/delete UI and encrypted storage belong to the coordinator's explicit
opt-in bridge. The existing owner-fact journal must not receive these proposals.

## Native LM1 contract

| Label | Scope |
| --- | --- |
| 0 | No side-effecting action; answer or clarify |
| 1 | Open one application/object |
| 2 | Search one term/object |
| 3 | Remember one explicitly requested item |

`train.tsv`, `dev.tsv` and `supervised_replay.tsv` keep the historical three
columns `id`, `label`, `prompt`. The wrapper remains `Auftrag: {prompt}\n`.
Supervision is one existing label token followed by actual EOS; prompt tokens
must not be supervised. The native original tokenizer must confirm
`prefix_tokens + 4 <= 256`. Character/byte lengths do not substitute for this.

The richer format distinguishes question, statement, request and correction and
represents multiple actions. Those examples are deliberately **not** reduced to
one misleading native label. This pilot therefore cannot demonstrate that LM1
learned full parameter extraction, pronoun resolution, question-vs-statement
classification or a general spoken-language model. Those require separate
pipeline and future model evaluation.

## Shortcut resistance and remaining limits

Every contrast pair uses exactly the same word multiset but a different correct
label. Exchanging the positions of two candidate instructions changes whether
an instruction is current, quoted, rejected or corrected. A fully optimal
order-insensitive word-bag classifier, including single-keyword and word-count
rules, can achieve at most 50% on either contrast split. Exact byte-length and
character-length oracle baselines are also reported. A simplistic high score
based only on `nicht`, `bitte`, an action verb or text length is thus exposed.

Training has four authored surface-template families; DEV has two different
surface-template families and different target names. Whole pairs and families
stay in one split. Task semantics intentionally remain shared: these are small
controlled diagnostics, **not proof of semantic or population independence**.
Position, punctuation and template-specific rules may still solve subsets.
Only the independently frozen evaluator can support a bounded transfer claim.
No result from these open examples can be relabelled as a blind test.

The 32 simple replay rows intentionally remain easy keyword-resolvable examples
of prior open/search behavior. Their purpose is retention, never novel-language
evidence. They are newly authored; the 64 historical open/search retention rows
are never reused for training. The manifest records normalized-text overlap=0
against that fixed file, its original SHA-256 and its audit-only role.

## Reproduce

Run from the repository root, choosing a destination that does not exist:

```sh
python experimental/personal-voice-20261111/data/voice_data.py \
  --output /tmp/continuum-voice-data-review \
  --historical-retention experimental/weight-space-20261110/data/retention.tsv
python -m unittest discover -s experimental/personal-voice-20261111/data -p test_voice_data.py -v
```

Generation refuses existing output directories. `manifest.json` binds every
generated file and the generator bytes. Tests mutate consent, scope, audio,
meaning, sensitivity and split membership and verify rejection. Corpus hashes
must stay fixed after native training registration. There is no network or model
execution in the generator.

## Connection to existing source

Read `docs/research/omega/PERSONAL-VOICE-DATA-HANDOFF-20261111.md` for the actual
Android speech → composer → local context paths and existing learning types.
All code and data here remain research-only. Original Gemma/LM1 selection,
ToolGate, app resources, APK, signing and releases are unchanged.

## Public methodological references

[Gardner et al. (2020), Evaluating Models' Local Decision Boundaries via Contrast
Sets](https://aclanthology.org/2020.findings-emnlp.117/) motivates controlled
meaning-changing contrast examples to expose dataset artifacts. [McCoy,
Pavlick and Linzen (2019), Right for the Wrong Reasons](https://aclanthology.org/P19-1334/)
uses controlled cases to diagnose lexical and syntactic shortcuts. The primary
ACL abstracts and publication records were checked on 11 November 2026. Our
bounded synthetic corpus applies this general testing rationale; it does not
reuse their datasets and is not a replication or a claim of equal coverage.
