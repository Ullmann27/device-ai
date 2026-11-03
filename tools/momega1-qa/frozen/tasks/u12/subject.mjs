export const run = (entry,now) => now-entry.insertedAt <= entry.ttl;
