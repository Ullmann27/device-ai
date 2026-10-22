// Shared source contract for the work-area navigation (owner request 22.10.2026: work areas visible on the start
// screen and one tap away in a fixed bottom bar). A center counts as reachable from the cockpit when the cockpit
// builds the work-area grid, the spec lists the area in that grid and openWorkArea routes it to the live handler.
import fs from 'node:fs';

const dir = new URL('../../app/src/main/java/at/heinz/deviceai/', import.meta.url);

export function workspaceNav(activity) {
  const spec = fs.readFileSync(new URL('ContinuumWorkspaceNavSpec.kt', dir), 'utf8');
  const list = name => [...((spec.match(new RegExp('val ' + name + ' = listOf\\(([^)]*)\\)')) || [])[1] || '')
    .matchAll(/ContinuumWorkArea\.([A-Z_]+)/g)].map(m => m[1]);
  const areas = [...((spec.match(/enum class ContinuumWorkArea \{([^}]*)\}/) || [])[1] || '').matchAll(/[A-Z_]+/g)].map(m => m[0]);
  const start = activity.indexOf('private fun openWorkArea(');
  const body = start < 0 ? '' : activity.slice(start, activity.indexOf('\n    }\n', start));
  const route = area => (body.match(new RegExp('ContinuumWorkArea\\.' + area + ' -> (\\w+)\\(')) || [])[1] || null;
  const homeGrid = list('homeGrid');
  return {
    spec, areas, homeGrid, bottomBar: list('bottomBar'), routeBody: body, route,
    homeReaches: (card, fn) => card.includes('buildWorkAreaGrid()') && homeGrid.some(a => route(a) === fn)
  };
}
