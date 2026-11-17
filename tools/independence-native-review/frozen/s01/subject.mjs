/** Return a new array with a signed circular right rotation. */
export function rotateRight(items, amount) {
  const offset = amount % items.length;
  return items.slice(offset).concat(items.slice(0, offset));
}
