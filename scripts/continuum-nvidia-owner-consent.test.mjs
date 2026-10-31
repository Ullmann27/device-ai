import test from 'node:test';
import assert from 'node:assert/strict';
import {requireExplicitOwnerConsent} from './continuum-nvidia-owner-consent.mjs';

const GRANT = 'ONE_REMOTE_TECHNICAL_REVIEW';

test('RED: missing owner grant cannot be inferred from a provider credential', () => {
  for (const provided of [undefined, null, '', false, 0]) {
    assert.throws(() => requireExplicitOwnerConsent(provided, GRANT),
      /NVIDIA_OWNER_CONSENT_REQUIRED/);
  }
});

test('RED: wrong or malformed owner grant fails closed', () => {
  for (const provided of ['YES', GRANT + ' ', 'one_remote_technical_review', {}, []]) {
    assert.throws(() => requireExplicitOwnerConsent(provided, GRANT),
      /NVIDIA_OWNER_CONSENT_REQUIRED/);
  }
  assert.throws(() => requireExplicitOwnerConsent(GRANT, ''),
    /NVIDIA_OWNER_CONSENT_REQUIRED/);
});

test('GREEN: exact explicit runtime grant is accepted without modification', () => {
  assert.equal(requireExplicitOwnerConsent(GRANT, GRANT), GRANT);
});
