export const run = rows => rows.reduce((s,r)=>s+r.value*r.weight,0)/rows.length;
