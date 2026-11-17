/**
 * Hand-reviewed rewrite rules for compiler errors with a single known fix.
 * Used by repair-strategies.mjs (strategy KOTLIN_KNOWN_FIX:<kind>) only for the
 * exact line a compiler diagnostic points at; it never scans or edits by itself.
 * Rules come from real failures of this repository.
 */
export const KNOWN_FIXES=Object.freeze([
  // 373bddb: Set has no takeLast(); bounded retention needs an ordered list.
  {kind:'SET_TAKELAST',from:'.toSet().takeLast(',to:'.toSet().toList().takeLast('}
]);

export function knownFixes(source){
  return KNOWN_FIXES.filter(f=>typeof source==='string'&&source.includes(f.from)).map(f=>({...f}));
}
