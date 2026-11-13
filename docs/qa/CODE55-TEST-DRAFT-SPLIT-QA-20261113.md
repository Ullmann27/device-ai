# Code55 protected test-draft signing

The existing registered Code52 signing entry delegates Code55 full-QA runs to
`continuum-code55-original-signer.sh`. That script still expected the old
combined host/native-PDF job and pinned obsolete workflow blobs. It cannot
accept the current split native PDF/Filament workflow.

This change updates that existing unpublished-draft route. Its protected
branch remains `chatgpt/code52-owner-signing-20261109`; product source remains
`integrate/code55-apk-fable-20261112`. Neither branch is main. No environment
policy or secret is changed, and no signing or publication is triggered.

The source-evidence checks reuse the stricter logic already tested in the
Code55 product line (889e06d4, historical Code50 signer filename): exact full
and APK run identities/attempts, all four full-QA jobs, both APK jobs,
authenticated artifact hashes/bytes/source, safe archive extraction,
recomputed JUnit with the unchanged explicit skip policy, Node XML, actual
native PDF and Filament receipts, unsigned package/ABI/16 KiB audit and the
pinned original-signed predecessor. The workflow blobs are explicitly pinned
to the current split-QA definitions. No failed-native exception is added.

Signer and product identities are distinct: the protected signer must match
its workflow SHA and be clean; the explicitly approved product SHA is fetched
into a separate temporary worktree and independently checked. Its existing
validators and skip manifest are used. The JUnit summary receives that product
SHA explicitly; the workflow/signing identity is never changed.

The registered manual entry discovers a matching successful APK run when its
older input schema supplies only source and full-QA run. The explicit Code55
entry also accepts a transport run. Both require the exact owner-approved
source and the existing protected environment with the owner reviewer and
one exact signing branch. Before any key use, all proof checks run again.

Signing creates only an unpublished draft, verifies original certificate,
package, byte hash, authenticated download and draft/source metadata, and
checks that Latest did not change. Publishing remains a separate decision.
No release publication command exists in this script.

Local validation: 14 functional Code55 cases (positive synthetic receipts and
independent negative mutations), 12 existing Code52 cases, 16 Code53 cases and
19 Code54 cases passed, zero skips. Bash syntax and embedded-Python static
checks passed. Functional fixtures use fake tools and synthetic APK bytes;
they do not claim real signing, authorization, device installation or QA of
a product candidate. The CI workflow repeats all tests with the source-side
validator dependencies pinned to 889e06d47fffd2230fa5b062dd1f31b65992e728.

Pending: exact-commit CI, final product full QA and APK audit, protected owner
approval, actual original signing and Fold7 acceptance. Existing test APK
75306783 is superseded for release by dialogue-routing work in PR #827.
