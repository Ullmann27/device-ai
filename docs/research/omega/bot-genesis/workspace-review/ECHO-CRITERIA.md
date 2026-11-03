# Independent deterministic reporter acceptance

Frozen before reporter implementation, 2026-11-03. Candidate before extension:
`0297b2680b2c44980086186ec7429bcda42a809a`. The reporter is a third
persisted identity, not a third model and not an independent verifier.

- E01: reporter resolves a distinct persisted stable profile; renaming does not
  change authority, workspace or provenance.
- E02: report binds exact task/job/source/attempt/request hash/current grant and
  receipt provenance, reconstructed from currently MAC-validated canonical records.
- E03: changed source, expired or revoked grant, expired assignment, UNKNOWN or
  failed terminal execution cannot produce a confirmed-success report.
- E04: absent or malformed verification receipts deny confirmed success.
- E05: reporting adds no model call, task debit or canonical journal mutation;
  report contains no model transcript or private owner context.
- E06: artifact stays inside existing coding-evidence storage; its content hash
  is verified when consumed, not merely printed as a claim.
- E07: repeated report creation cannot change the canonical task outcome.
- E08: report distinguishes assignment, actual execution, scripted host tests,
  real model inference and device evidence. A summary is not another verifier.

Tests may use synthetic canonical tasks and scripted patches when explicitly
labelled. They do not establish local-model problem solving or Fold7 behavior.
