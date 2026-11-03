export const run = ms => { const d=new Date(ms); return `${d.getUTCFullYear()}-${String(d.getUTCMonth()).padStart(2,"0")}-${String(d.getUTCDate()).padStart(2,"0")}`; };
