// NF-5c task bank (study fixture, not a product module): pairs of plausible implementations that agree on typical
// examples and differ at edge cases. Written before any NF-5c study run.

/** Removes spaces at both ends of a text. */
export function trimSpaces(s){let a=0,b=s.length;while(a<b&&s[a]===' ')a++;while(b>a&&s[b-1]===' ')b--;return s.slice(a,b);}
/** Removes whitespace at both ends of a text. */
export function trimBlank(s){return s.trim();}

/** Turns a title into a URL slug: lower case, words joined by hyphens. */
export function slugAscii(s){return s.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');}
/** Makes a slug from a title for links. */
export function slugLetters(s){return s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu,'-').replace(/^-|-$/g,'');}

/** Capitalizes the first letter of a word. */
export function capitalizeFirst(s){return s.charAt(0).toUpperCase()+s.slice(1);}
/** Writes a word with a capital first letter. */
export function capitalizeWord(s){return s.charAt(0).toUpperCase()+s.slice(1).toLowerCase();}

/** Counts the words of a sentence. */
export function countWordsSplit(s){return s.split(' ').length;}
/** Number of words in a text. */
export function countWordsTokens(s){return s.trim().split(/\s+/).filter(Boolean).length;}

/** Initials of a name, e.g. for avatars. */
export function initialsUpper(s){return s.split(/\s+/).filter(Boolean).map(w=>w[0].toUpperCase()).join('');}
/** First letters of the words of a name. */
export function initialsAsIs(s){return s.split(/\s+/).filter(Boolean).map(w=>w[0]).join('');}

/** Normalizes a key for lookups: trimmed and lower case. */
export function keyLower(s){return s.trim().toLowerCase();}
/** Normalizes a lookup key. */
export function keySnake(s){return s.trim().toLowerCase().replace(/\s+/g,'_');}

/** Upper-case version of a value written as text. */
export function upperText(s){return String(s).toUpperCase();}
/** Writes a value in capital letters. */
export function upperConcat(s){return (''+s).toUpperCase();}
