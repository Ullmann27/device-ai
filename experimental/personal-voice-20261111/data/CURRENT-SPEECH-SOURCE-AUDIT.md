# Current speech/context source audit — 11 November 2026

This is a read-only source comparison, not an Android or Fold7 run. The research
baseline remains PR #728 at 1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667.
That baseline is older than the current APK/Cinema work; no whole-branch merge
into that work is appropriate.

## Identities actually inspected

- Current open Cinema/Maya PR #747 was fetched at HEAD
  `23146e7f817b825e48a2b086f73da5cf28e0a274`.
- Code53 integration checkout read at
  `4515e71ab5d39319e54c710d97a1816d82b6202e`, branch
  `agent/code53-maya-integration-20261111`.
- PR #752 names frozen Code53 source
  `fd86f11a4a822ee4805f4c462791baeb1d741db4`. The four source paths below had
  no diff between the Code53 checkout and that frozen source.
- The four fetched PR #747 files have exactly the same Git blob IDs as the
  inspected Code53 source. Only source files were read; no chat-history directory,
  owner audio, credentials or another research group's holdouts were read.

| Source path below app/src/main/java/at/heinz/deviceai | Git blob SHA |
| --- | --- |
| AndroidOnDeviceSpeechInput.kt | 6241210eb10f12574569dea8061cab1b7cf009c4 |
| cognitive/LocalConversationContext.kt | 5cc0f2ba4771a7dd3ca9872f342eedd2e8fac5e8 |
| cognitive/OwnerCorrectionIntent.kt | 0e35420b67a8e9ecba6fbc6d18cb2b68ee5f6c51 |
| learning/interactive/ResearchFeedback.kt | 47f5f07d22d289c55f407be3f7a3c2a62ddf4a09 |

## What already improved since the LM1 baseline

**ASR lifecycle:** AndroidOnDeviceSpeechInput now pools a warm recognizer and
uses bounded warm/warm/cold recovery for Android client/busy handover failures.
It still uses local recognition, de-AT, one returned hypothesis, no audio buffer
retention, and a 30-second session timeout. A 3000 ms sentence-silence hint is
passed to Android. These are source facts; whether the platform honors every
hint and how well it recognizes actual speech are device-test questions.

**Dictation versus conversation:** Ordinary dictation still enters the editable
composer. The explicitly activated ContinuousConversationMode can instead
accept one recognized utterance and pass it through the existing
`sendCurrentInput()` path. A blanket statement that all recognition requires a
new tap on Send would therefore be outdated. The training-capture permission
must remain independent of either speech mode.

**Persistent dialogue:** LocalConversationContext now uses LocalChatTranscriptStore
turns and callbacks for app-private chat history, restoration and invalidation.
Four recent complete owner turns can be retained, with a 7900 character/UTF-8
byte bound and response-tail handling. This existing chat persistence is not a
training-consent grant. The inspected store uses JSON lines in app-private
storage; app-private location alone is not an application-layer encryption
claim. This audit neither opens nor copies such files.

**Reference handling:** LocalConversationSituationTracker and
ConversationSituationModel already provide bounded topic-return hints. The
hints explicitly are not memory facts, evidence or tool rights.
OwnerCorrectionIntent resolves last, previous or yesterday's draft/PDF against
CockpitWorkJournal and is handled by AgentActivity before the ordinary local
model path. Development branches are not among its two supported target kinds.
The general LocalConversationContext scope-correction path still clears prior
turns and treats ambiguous 'other/previous/before' references as a clarification
case. A future correction bridge must snapshot current context first, and must
reuse existing specialized reference resolvers where applicable.

**Existing research feedback:** `learning/interactive/ResearchFeedback` already
proposes USER_FEEDBACK records, produces review digests, confirms/rejects/revokes
explicit reviewed candidates and fingerprints live training candidates. It
uses the existing learning ledger and excludes multiple unresolved revisions.
Its current provenance is deliberately `SYNTHETIC_FIXTURE`, and source search
found tests rather than an AgentActivity production call. It must not be
relabelled as approved private speech collection. Reuse its review/revocation
ideas when integrating the protected opt-in staging bridge; do not create an
alternative owner-fact ledger or automatically call training on a correction.

## Consequence for this experiment

The personal voice dataset and its four-label LM1 targets remain unchanged.
The coordinator's new bridge stays isolated research code, with no
AgentActivity mutation or production capture. Any later APK integration should
be a narrow reviewed port against the then-current source, reusing the existing
speech lifecycle, conversation mode, transcript controls, reference resolvers
and learning record types. Results from the older research baseline must never
be presented as a verified gain over the complete current Code53 application.
