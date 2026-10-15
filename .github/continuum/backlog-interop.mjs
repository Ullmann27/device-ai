// Executed by the real Gradle JVM integration test; production codec on both ends.
import fs from 'node:fs';
import {addTask,emptyBacklog,importBridge,exportBridge,changeStatus} from './development-backlog.mjs';
const mode=process.argv[2];
const b=mode==='create'?addTask(emptyBacklog(),{title:'Grüße 🧠 / Test',benefit:80}):importBridge(fs.readFileSync(0,'utf8'));
if(mode==='advance')changeStatus(b,b.tasks[0].id,b.tasks[0].status==='BLOCKED'?'OPEN':'BLOCKED',{note:'Node geprüft'});
process.stdout.write(exportBridge(b));
