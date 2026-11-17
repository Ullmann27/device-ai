# Independent case freeze

Candidate 78d94a3959f655cd512025824ca92d78e698a5a1, review base cf34fb6a.
Frozen before any challenge execution. Syntax checks only: both exit 0. See frozen-tests.sha256.
31 cases C01-C28 (C21 has four corruption variants). Cases C11/C14/C15/C17 assert desired authorization/revocation safety and may be RED; they are not rewritten to accept observed unsafe output. C12 expressly measures narrow transfer boundary, not completed novel-task recall. C27/C28 expose signer/current-model trust limitations without asserting scientific provenance.

Test source is reviewer-authored after API/spec inspection; these are not blind holdouts. Positive proof fixtures use disposable runtime keys and manually built synthetic observations. There is no model inference or live brand admission.
