import {checkLocalBrain, queryLocalBrain} from './continuum-local-brain.mjs';
const command=process.argv[2];
if(!['health','chat'].includes(command)) {
  process.stderr.write('Usage: node scripts/continuum-local-brain-cli.mjs health | chat <your prompt>\n');
  process.exitCode=2;
} else {
  try {
    if(command==='health') {
      const report=await checkLocalBrain();
      process.stdout.write(JSON.stringify(report)+'\n');
      if(!report.ready)process.exitCode=1;
    } else {
      const prompt=process.argv.slice(3).join(' ').trim();
      if(!prompt)throw Error('PROMPT_REQUIRED');
      const result=await queryLocalBrain({
        systemInstruction:'Du bist der lokal ausgeführte Textassistent für Device AI Continuum. Gib klare Antworten. Keine Systembefehle oder Behauptungen über geprüfte Geräteaktionen.',
        userPrompt:prompt,
        apiKey:process.env.CONTINUUM_LOCAL_BRAIN_API_KEY,
        model:process.env.CONTINUUM_LOCAL_BRAIN_MODEL || 'local'
      });
      process.stdout.write(result.text+'\n');
    }
  } catch(e) {
    process.stderr.write('LOCAL_MODEL_UNAVAILABLE_OR_INVALID: '+String(e?.message??e)+'\n');
    process.exitCode=1;
  }
}
