// B25 requires editable persisted budgets, absent from the current profile API.
// Do not pretend the bridge's default profile represents a 500ms profile.
import {cases as supplemental} from './supplemental-cases.mjs';
import {fresh} from './adapter-cases.mjs';
export const notTestable = ['B25-profile-lifetime-cap'];
export const cases = [...supplemental.filter(c => !notTestable.includes(c.id)), {
  id:'B28-default-profile-lifetime-cap', expected:'REJECT', make() {
    const value=fresh();
    value.canonical.expiresAt=130000;
    value.message.expiresAt=value.now+120001;
    return value;
  }
}];
