export function countPages(items, pageSize) {
  if (!Number.isInteger(items) || items < 0 || !Number.isInteger(pageSize) || pageSize <= 0) {
    throw new RangeError('nonnegative integer items and positive integer page size required');
  }
  return Math.floor(items / pageSize);
}
