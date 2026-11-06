const { chromium } = require('playwright');
const path = require('path');
const dir = process.argv[2], phases = (process.argv[3] || '1,2,3,4,5').split(',').map(Number), dpr = +(process.argv[4] || 2);
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1190, height: 1322 }, deviceScaleFactor: dpr });
  for (const p of phases) {
    await page.goto('file://' + path.join(dir, 'design.html') + '?p=' + p);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(250);
    await page.screenshot({ path: path.join(dir, 'out', `concept-${p}.png`) });
    console.log('shot', p);
  }
  await browser.close();
})();
