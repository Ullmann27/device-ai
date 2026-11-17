// NF-5c task bank (study fixture, not a product module): pairs of plausible implementations that agree on typical
// examples and differ at edge cases. Written before any NF-5c study run.

/** Rounds a number to a whole number. */
export function roundHalfUp(x){return Math.floor(x+.5);}
/** Rounds to the nearest whole number (ties to even). */
export function roundHalfEven(x){const f=Math.floor(x),d=x-f;return d>.5?f+1:d<.5?f:(f%2===0?f:f+1);}

/** Share as a whole percentage. */
export function percentRound(x){return Math.round(x*100);}
/** Converts a share into percent. */
export function percentFloor(x){return Math.floor(x*100);}

/** Is the value above zero? */
export function isPositive(x){return x>0;}
/** Is the number at least zero? */
export function isNotNegative(x){return x>=0;}

/** Size in kilobytes for a byte count. */
export function kilobytesRound(b){return Math.round(b/1024);}
/** Kilobytes needed for a number of bytes. */
export function kilobytesCeil(b){return Math.ceil(b/1024);}

/** Square of a number. */
export function squareMul(x){return x*x;}
/** Second power of a number. */
export function squarePow(x){return x**2;}
