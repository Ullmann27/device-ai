// Preloaded into every Node process of a lab test run: reports peak resident memory on exit (kilobytes).
process.on('exit',()=>{try{process.stderr.write('\nLAB_MAXRSS_KB='+process.resourceUsage().maxRSS+'\n');}catch{}});
