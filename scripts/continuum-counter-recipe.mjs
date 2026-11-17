import fs from 'node:fs';
// Only fixed, reviewed files in this repository; no external path or source parameter.
export function counterFiles() {
  const base='app/src/main/java/at/heinz/continuum/m4counter/';
  const read=name=>fs.readFileSync(new URL('./owned-app-recipes/m4-counter/'+name,import.meta.url),'utf8');
  return {
    [base+'CounterPolicy.kt']:read('CounterPolicy.kt'),
    [base+'MainActivity.kt']:read('MainActivity.kt'),
    'app/src/test/java/at/heinz/continuum/m4counter/CounterPolicyTest.kt':read('CounterPolicyTest.kt')
  };
}
