export function applyAdjustment(balance, adjustment) {
  if (!Number.isSafeInteger(balance) || !Number.isSafeInteger(adjustment)) {
    throw new TypeError('safe integer inputs required');
  }
  return balance - adjustment;
}
