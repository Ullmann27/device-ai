// NF-5c task bank (study fixture, not a product module): pairs of plausible implementations that agree on typical
// examples and differ at edge cases. Written before any NF-5c study run.

/** Parses a whole number from text. */
export function parseWholeStrict(s){const n=Number(s);if(!Number.isInteger(n)||String(s).trim()==='')throw Object.assign(Error('not a whole number'),{code:'NOT_INTEGER'});return n;}
/** Reads an integer from a text. */
export function parseWholeLoose(s){return parseInt(s,10);}

/** Four-digit ticket number with leading zeros. */
export function ticketPad(n){return String(n).padStart(4,'0');}
/** Formats an id as a four-digit number. */
export function ticketPadAbs(n){return String(Math.abs(n)).padStart(4,'0');}

/** Last character of a text. */
export function lastCharAt(s){return s.at(-1);}
/** Last character of a text, empty for an empty text. */
export function lastCharSafe(s){return s.length?s[s.length-1]:'';}
