// 24 independently specified additional benchmark defects. Reference actions are for task validity only.
// Real agent results are NEVER inferred from oracle validity.
export const EXTRA_TASKS = Object.freeze([
  {
    "id": "num-absolute",
    "grammar": "numeric",
    "goal": "absolute(x) returns magnitude",
    "files": {
      "lib/num-absolute.mjs": "export function absolute(x){return x<0?x:-x;}\n"
    },
    "test": "import {absolute} from '../../lib/num-absolute.mjs';\ntest('num-absolute',()=>{assert.equal(absolute(-7),7);assert.equal(absolute(9),9);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-absolute.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-absolute.mjs",
          "old_text": "x<0?x:-x",
          "new_text": "x<0?-x:x"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-area",
    "grammar": "numeric",
    "goal": "area(w,h) multiplies both sides",
    "files": {
      "lib/num-area.mjs": "export function area(w,h){return w+h;}\n"
    },
    "test": "import {area} from '../../lib/num-area.mjs';\ntest('num-area',()=>{assert.equal(area(4,5),20);assert.equal(area(0,9),0);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-area.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-area.mjs",
          "old_text": "return w+h;",
          "new_text": "return w*h;"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-average",
    "grammar": "numeric",
    "goal": "mean computes arithmetic mean",
    "files": {
      "lib/num-average.mjs": "export function mean(a,b){return a+b/2;}\n"
    },
    "test": "import {mean} from '../../lib/num-average.mjs';\ntest('num-average',()=>{assert.equal(mean(4,8),6);assert.equal(mean(-2,2),0);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-average.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-average.mjs",
          "old_text": "return a+b/2;",
          "new_text": "return (a+b)/2;"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-even",
    "grammar": "numeric",
    "goal": "even(n) returns true for even integers",
    "files": {
      "lib/num-even.mjs": "export function even(n){return n%2===1;}\n"
    },
    "test": "import {even} from '../../lib/num-even.mjs';\ntest('num-even',()=>{assert.equal(even(8),true);assert.equal(even(7),false);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-even.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-even.mjs",
          "old_text": "n%2===1",
          "new_text": "n%2===0"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-percent-change",
    "grammar": "numeric",
    "goal": "change(oldValue,newValue) returns percentage difference",
    "files": {
      "lib/num-percent-change.mjs": "export function change(oldValue,newValue){return (oldValue-newValue)/oldValue*100;}\n"
    },
    "test": "import {change} from '../../lib/num-percent-change.mjs';\ntest('num-percent-change',()=>{assert.equal(change(100,125),25);assert.equal(change(100,80),-20);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-percent-change.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-percent-change.mjs",
          "old_text": "oldValue-newValue",
          "new_text": "newValue-oldValue"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-max",
    "grammar": "numeric",
    "goal": "greater(a,b) returns the larger number",
    "files": {
      "lib/num-max.mjs": "export function greater(a,b){return a<b?a:b;}\n"
    },
    "test": "import {greater} from '../../lib/num-max.mjs';\ntest('num-max',()=>{assert.equal(greater(7,3),7);assert.equal(greater(-9,-4),-4);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-max.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-max.mjs",
          "old_text": "a<b?a:b",
          "new_text": "a>b?a:b"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-min",
    "grammar": "numeric",
    "goal": "smaller(a,b) returns the smaller number",
    "files": {
      "lib/num-min.mjs": "export function smaller(a,b){return a>b?a:b;}\n"
    },
    "test": "import {smaller} from '../../lib/num-min.mjs';\ntest('num-min',()=>{assert.equal(smaller(7,3),3);assert.equal(smaller(-9,-4),-9);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-min.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-min.mjs",
          "old_text": "a>b?a:b",
          "new_text": "a<b?a:b"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-fahrenheit",
    "grammar": "numeric",
    "goal": "toFahrenheit converts Celsius to Fahrenheit",
    "files": {
      "lib/num-fahrenheit.mjs": "export function toFahrenheit(c){return c*5/9+32;}\n"
    },
    "test": "import {toFahrenheit} from '../../lib/num-fahrenheit.mjs';\ntest('num-fahrenheit',()=>{assert.equal(toFahrenheit(0),32);assert.equal(toFahrenheit(100),212);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-fahrenheit.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-fahrenheit.mjs",
          "old_text": "c*5/9+32",
          "new_text": "c*9/5+32"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-product",
    "grammar": "numeric",
    "goal": "multiply(a,b) multiplies two numbers",
    "files": {
      "lib/num-product.mjs": "export function multiply(a,b){return a+b;}\n"
    },
    "test": "import {multiply} from '../../lib/num-product.mjs';\ntest('num-product',()=>{assert.equal(multiply(3,4),12);assert.equal(multiply(0,5),0);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-product.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-product.mjs",
          "old_text": "return a+b;",
          "new_text": "return a*b;"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-clamp-lower",
    "grammar": "numeric",
    "goal": "lowerBound(x,min) never falls below min",
    "files": {
      "lib/num-clamp-lower.mjs": "export function lowerBound(x,min){return x<min?x:min;}\n"
    },
    "test": "import {lowerBound} from '../../lib/num-clamp-lower.mjs';\ntest('num-clamp-lower',()=>{assert.equal(lowerBound(-4,0),0);assert.equal(lowerBound(5,0),5);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-clamp-lower.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-clamp-lower.mjs",
          "old_text": "x<min?x:min",
          "new_text": "x<min?min:x"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-clamp-upper",
    "grammar": "numeric",
    "goal": "upperBound(x,max) never exceeds max",
    "files": {
      "lib/num-clamp-upper.mjs": "export function upperBound(x,max){return x>max?x:max;}\n"
    },
    "test": "import {upperBound} from '../../lib/num-clamp-upper.mjs';\ntest('num-clamp-upper',()=>{assert.equal(upperBound(15,10),10);assert.equal(upperBound(5,10),5);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-clamp-upper.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-clamp-upper.mjs",
          "old_text": "x>max?x:max",
          "new_text": "x>max?max:x"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "num-difference",
    "grammar": "numeric",
    "goal": "delta(current,previous) measures signed change",
    "files": {
      "lib/num-difference.mjs": "export function delta(current,previous){return previous-current;}\n"
    },
    "test": "import {delta} from '../../lib/num-difference.mjs';\ntest('num-difference',()=>{assert.equal(delta(12,7),5);assert.equal(delta(3,9),-6);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/num-difference.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/num-difference.mjs",
          "old_text": "previous-current",
          "new_text": "current-previous"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-trim",
    "grammar": "general",
    "goal": "clean trims a string at both ends",
    "files": {
      "lib/gen-trim.mjs": "export function clean(text){return text.toLowerCase();}\n"
    },
    "test": "import {clean} from '../../lib/gen-trim.mjs';\ntest('gen-trim',()=>{assert.equal(clean('  HELLO '),'hello');assert.equal(clean(' X '),'x');});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-trim.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-trim.mjs",
          "old_text": "text.toLowerCase()",
          "new_text": "text.trim().toLowerCase()"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-unique",
    "grammar": "general",
    "goal": "unique returns first occurrences only",
    "files": {
      "lib/gen-unique.mjs": "export function unique(values){return values;}\n"
    },
    "test": "import {unique} from '../../lib/gen-unique.mjs';\ntest('gen-unique',()=>{assert.deepEqual(unique([2,1,2,3,1]),[2,1,3]);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-unique.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-unique.mjs",
          "old_text": "return values;",
          "new_text": "return [...new Set(values)];"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-count",
    "grammar": "general",
    "goal": "countOf counts only matching array elements",
    "files": {
      "lib/gen-count.mjs": "export function countOf(values,needle){return values.filter(x=>x===x).length;}\n"
    },
    "test": "import {countOf} from '../../lib/gen-count.mjs';\ntest('gen-count',()=>{assert.equal(countOf(['a','b','a'],'a'),2);assert.equal(countOf([1,2,3],4),0);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-count.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-count.mjs",
          "old_text": "x===x",
          "new_text": "x===needle"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-range",
    "grammar": "general",
    "goal": "range(n) returns exactly n integers from zero",
    "files": {
      "lib/gen-range.mjs": "export function range(n){const out=[];for(let i=0;i<=n;i++)out.push(i);return out;}\n"
    },
    "test": "import {range} from '../../lib/gen-range.mjs';\ntest('gen-range',()=>{assert.deepEqual(range(3),[0,1,2]);assert.deepEqual(range(0),[]);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-range.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-range.mjs",
          "old_text": "i<=n",
          "new_text": "i<n"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-csv",
    "grammar": "general",
    "goal": "csvEntries trims comma-separated cells",
    "files": {
      "lib/gen-csv.mjs": "export function csvEntries(text){return text.split(',');}\n"
    },
    "test": "import {csvEntries} from '../../lib/gen-csv.mjs';\ntest('gen-csv',()=>{assert.deepEqual(csvEntries(' A, B ,C '),['A','B','C']);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-csv.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-csv.mjs",
          "old_text": "return text.split(',');",
          "new_text": "return text.split(',').map(x=>x.trim());"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-fallback",
    "grammar": "general",
    "goal": "fallback preserves valid falsy values",
    "files": {
      "lib/gen-fallback.mjs": "export function fallback(value,alternative){return value||alternative;}\n"
    },
    "test": "import {fallback} from '../../lib/gen-fallback.mjs';\ntest('gen-fallback',()=>{assert.equal(fallback(0,42),0);assert.equal(fallback(null,42),42);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-fallback.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-fallback.mjs",
          "old_text": "value||alternative",
          "new_text": "value??alternative"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-prefix",
    "grammar": "general",
    "goal": "hasPrefix checks the beginning of text",
    "files": {
      "lib/gen-prefix.mjs": "export function hasPrefix(text,prefix){return text.endsWith(prefix);}\n"
    },
    "test": "import {hasPrefix} from '../../lib/gen-prefix.mjs';\ntest('gen-prefix',()=>{assert.equal(hasPrefix('abcdef','abc'),true);assert.equal(hasPrefix('abcdef','def'),false);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-prefix.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-prefix.mjs",
          "old_text": "text.endsWith(prefix)",
          "new_text": "text.startsWith(prefix)"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-immutable-add",
    "grammar": "general",
    "goal": "append does not mutate the supplied array",
    "files": {
      "lib/gen-immutable-add.mjs": "export function append(list,item){list.push(item);return list;}\n"
    },
    "test": "import {append} from '../../lib/gen-immutable-add.mjs';\ntest('gen-immutable-add',()=>{const input=[1,2];assert.deepEqual(append(input,3),[1,2,3]);assert.deepEqual(input,[1,2]);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-immutable-add.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-immutable-add.mjs",
          "old_text": "list.push(item);return list;",
          "new_text": "return [...list,item];"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-numeric-order",
    "grammar": "general",
    "goal": "ascending sorts numbers ascending and does not mutate",
    "files": {
      "lib/gen-numeric-order.mjs": "export function ascending(values){return values.sort();}\n"
    },
    "test": "import {ascending} from '../../lib/gen-numeric-order.mjs';\ntest('gen-numeric-order',()=>{const input=[11,3,2];assert.deepEqual(ascending(input),[2,3,11]);assert.deepEqual(input,[11,3,2]);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-numeric-order.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-numeric-order.mjs",
          "old_text": "values.sort()",
          "new_text": "[...values].sort((a,b)=>a-b)"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-empty-list",
    "grammar": "general",
    "goal": "firstOrNull returns null instead of undefined on empty list",
    "files": {
      "lib/gen-empty-list.mjs": "export function firstOrNull(items){return items[0];}\n"
    },
    "test": "import {firstOrNull} from '../../lib/gen-empty-list.mjs';\ntest('gen-empty-list',()=>{assert.equal(firstOrNull([]),null);assert.equal(firstOrNull([7]),7);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-empty-list.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-empty-list.mjs",
          "old_text": "return items[0];",
          "new_text": "return items.length?items[0]:null;"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-remove-item",
    "grammar": "general",
    "goal": "without removes all occurrences without mutating input",
    "files": {
      "lib/gen-remove-item.mjs": "export function without(values,item){return values.filter(x=>x===item);}\n"
    },
    "test": "import {without} from '../../lib/gen-remove-item.mjs';\ntest('gen-remove-item',()=>{const input=[1,2,1,3];assert.deepEqual(without(input,1),[2,3]);assert.deepEqual(input,[1,2,1,3]);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-remove-item.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-remove-item.mjs",
          "old_text": "x===item",
          "new_text": "x!==item"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  },
  {
    "id": "gen-last",
    "grammar": "general",
    "goal": "last returns final array element or null if empty",
    "files": {
      "lib/gen-last.mjs": "export function last(values){return values[values.length];}\n"
    },
    "test": "import {last} from '../../lib/gen-last.mjs';\ntest('gen-last',()=>{assert.equal(last([5,6]),6);assert.equal(last([]),null);});\n",
    "oracle": [
      {
        "tool": "read_file",
        "args": {
          "path": "lib/gen-last.mjs"
        }
      },
      {
        "tool": "edit_file",
        "args": {
          "path": "lib/gen-last.mjs",
          "old_text": "return values[values.length];",
          "new_text": "return values.length?values[values.length-1]:null;"
        }
      },
      {
        "tool": "check",
        "args": {}
      },
      {
        "tool": "finish",
        "args": {
          "plan": [
            "Read baseline and failing test",
            "Apply exact minimal code repair"
          ],
          "hypothesis": "Targeted original defect causes the expected counterexample"
        }
      }
    ]
  }
]);
