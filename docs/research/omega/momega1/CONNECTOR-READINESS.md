# Optional account connectors: observed readiness

Checked 2026-11-03 against source `d8a927dd6ced4dea489e2042afb4b5624157fa3b`.
No account API connector is connected in this source. This inventory does not
create an account, application registration, permission grant or network request
to private account data. Local chat remains usable without these optional services.

| Service | Existing Android path | Missing concrete prerequisites | Real account test |
|---|---|---|---|
| Google account | None | Developer project/branding, client tied to original package and signing identity, authentication UI and revocation handling | NOT_RUN |
| Gmail | Owner-approved installed-app launch/screen observation only | Separate action-scoped Google authorization, reviewed Gmail adapter, least required scopes, response provenance and cancellation | NOT_RUN |
| Google Drive/Calendar | None | Service-specific narrow authorization and reviewed adapters; account login alone grants neither | NOT_RUN |
| Facebook | Installed-app tools can be offered under existing owner policy | Registered Meta app, permitted use case/scopes, callback validation, private token lifecycle and a reviewed adapter | NOT_RUN |
| TikTok | Installed-app tools can be offered under existing owner policy | Registered app/Login Kit, platform and redirect settings, selected products/scopes, token lifecycle and a reviewed adapter | NOT_RUN |

`launch_app` proves an observed foreground launch. `read_app_screen` produces
bounded, per-request accessibility text with provenance. Neither grants mailbox,
social-feed, posting or account access. Installing an app, granting accessibility,
or connecting ChatGPT's tools must never display an Android account as connected.

## Current provider documentation

- [Google Android authorization](https://developer.android.com/identity/authorization)
  separates sign-in from data authorization and recommends asking for additional
  scopes when the owner invokes a feature. Android client registration binds the
  package and signing-certificate fingerprint. Read successfully 2026-11-03.
- [Gmail scopes](https://developers.google.com/workspace/gmail/api/auth/scopes)
  lists `gmail.readonly` as restricted. Gmail reading is distinct from sending,
  modifying or deleting. Public-distribution verification and server-data handling
  requirements must be assessed for the actual chosen architecture. Read
  successfully 2026-11-03; page updated 2026-09-10.
- [TikTok Sandbox](https://developers.tiktok.com/docs/en/add-a-sandbox)
  provides a restricted testing configuration with explicitly added target users.
  It is distinct from public app approval and does not provide public-video
  posting or Data Portability API access. Read successfully 2026-11-03; page
  updated 2026-08-04.
- [Facebook Login documentation](https://developers.facebook.com/docs/facebook-login/overview/)
  returned HTTP 429 during this audit. Precise current Meta eligibility/review
  rules remain unverified; third-party summaries were not substituted as proof.

## Intended bounded test sequence

1. Supply a real developer registration and original-package callback identity
   through the protected configuration path; no credentials in Git or chat.
2. Owner explicitly selects one service and one read-only test scope. Start the
   provider's supported authentication/authorization flow; validate state/PKCE,
   redirect binding, expiry, returned scopes and account selection.
3. Perform one bounded read of owner-selected synthetic test data, with no send,
   posting or deletion permission. Mark connected only after the actual response.
4. Bind visible document/agent effects to actual request and result IDs. Revocation,
   cancellation or timeout ends the effect and drops any transient returned data.
5. Revoke access and verify that repeat calls fail closed. Public rollout and
   additional provider approvals remain separate from this private development test.

No fallback to unrestricted screen automation, hidden browser cookies, shared
ChatGPT credentials or external inference is part of this plan.
