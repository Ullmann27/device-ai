#!/usr/bin/env bash
# Actual existing Kotlin/Node codecs; no model import, promotion or Android build.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?existing Kotlin dependency jars required}
baseline=${1:?public SWB1 checkpoint}; candidate=${2:?research SWB2 checkpoint}; out=${3:?external evidence directory}
mkdir -p "$out"; out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
sources=("$root/app/src/main/java/at/heinz/deviceai/learning/LearningSnapshot.kt" "$root/app/src/main/java/at/heinz/deviceai/learning/TrainableSubwordEncoder.kt" "$root/app/src/main/java/at/heinz/deviceai/learning/Swb2ExperimentalEncoder.kt" "$root/experimental/swb1-contrastive-20261027/CheckpointCompatibility.kt")
sha256sum "${sources[@]}" "$baseline" "$candidate" > "$out/source-blobs.sha256"
java -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.txt" 2>&1
java -cp "$out/classes:$cp" at.heinz.deviceai.research.CheckpointCompatibility "$baseline" "$candidate" | tee "$out/jvm.txt"
node --input-type=module - "$root" "$baseline" "$candidate" "$out" <<'JS'
import fs from 'node:fs';import {pathToFileURL} from 'node:url';
const [root,a,b,out]=process.argv.slice(2);const {SubwordEncoder}=await import(pathToFileURL(root+'/.github/continuum/portable-subword.mjs'));
const old=SubwordEncoder.restore(fs.readFileSync(a)),side=SubwordEncoder.restore(fs.readFileSync(b));
const q='Weshalb wird der Behälter geprüft?',l='Das Ventil ist geöffnet.',r='Das Ventil ist nicht geöffnet.';
const expected={baselineIntent:old.intentScore(q),baselineSimilarity:old.similarity(l,r),sidecarIntent:side.intentScore(q),sidecarSimilarity:side.similarity(l,r)};
const actual=Object.fromEntries(fs.readFileSync(out+'/jvm.txt','utf8').trim().split('\n').map(s=>s.split('=')));
for(const [key,value] of Object.entries(expected))if(!Number.isFinite(+actual[key])||Math.abs(+actual[key]-value)>1e-12)throw Error('KOTLIN_NODE_PARITY_FAILED:'+key);
fs.writeFileSync(out+'/parity.json',JSON.stringify({expected,actual,tolerance:1e-12,passed:true},null,2)+'\n');
JS
