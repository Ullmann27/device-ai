/** Explicit runtime owner grant for a remote technical-source review.
 * A valid provider key alone is never permission to disclose project sources.
 * The expected grant is supplied by the reviewed caller, not by model output.
 */
export function requireExplicitOwnerConsent(provided, expected) {
  if (typeof expected !== 'string' || expected.length === 0 ||
      typeof provided !== 'string' || provided !== expected) {
    throw Error('NVIDIA_OWNER_CONSENT_REQUIRED');
  }
  return provided;
}
