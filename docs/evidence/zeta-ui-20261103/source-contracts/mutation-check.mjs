import fs from 'node:fs';
const mutations={
  fixed_font:['ContinuumHomeView.kt','ContinuumHomeLayout.phone(windowWidthDp, textScale)','ContinuumHomeLayout.phone(windowWidthDp, 1f)'],
  shrinking_controls:['ContinuumHomeLayout.kt','return Layout(wide, width, 1f, contentHeight, boxes)','return Layout(wide, width, .5f, contentHeight, boxes)'],
  overlapping_actions:['ContinuumHomeLayout.kt','val actionTop = heroTop + heroHeight + gap','val actionTop = heroTop'],
  fake_model_ready:['ContinuumDashboardState.kt','val online: Boolean = false','val online: Boolean = true'],
  missing_backlog_route:['AgentActivity.kt','Slot.WORKERS -> showDevelopmentBacklog()','Slot.WORKERS -> Unit'],
  missing_detach_cleanup:['ContinuumHologramView.kt','removeCallbacks(drawFrame)','Unit // removed cleanup']
};
const selected=mutations[process.env.ZETA_MUTANT];
if(!selected) throw new Error('Unknown mutation');
const original=fs.readFileSync;
fs.readFileSync=function(file,...args){
 const result=original.call(this,file,...args);
 if(String(file).endsWith('/'+selected[0]) && typeof result==='string') {
  if(!result.includes(selected[1])) throw new Error('Mutation target missing');
  return result.replaceAll(selected[1],selected[2]);
 }
 return result;
};
