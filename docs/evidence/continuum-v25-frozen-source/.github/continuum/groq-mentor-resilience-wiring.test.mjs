import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const client=fs.readFileSync(
  new URL('../../app/src/main/java/at/heinz/deviceai/AgentBackendClient.kt',import.meta.url),'utf8');
const activity=fs.readFileSync(
  new URL('../../app/src/main/java/at/heinz/deviceai/AgentActivity.kt',import.meta.url),'utf8');
const tokens=fs.readFileSync(
  new URL('../../app/src/main/java/at/heinz/deviceai/SecureTokenStore.kt',import.meta.url),'utf8');
const server=fs.readFileSync(new URL('../../backend/src/server.js',import.meta.url),'utf8');

const between=(source,head,tail)=>{
  const start=source.indexOf(head),end=source.indexOf(tail,start+head.length);
  assert.ok(start>=0&&end>start,'Missing source boundary: '+head);
  return source.slice(start,end);
};

test('mentor access remains encrypted on-device between app restarts',()=>{
  assert.match(tokens,/fun saveMentorToken\(/);
  assert.match(tokens,/fun loadMentorToken\(/);
  assert.match(tokens,/AES\/GCM\/NoPadding/);
  assert.match(tokens,/AndroidKeyStore/);
  assert.match(client,/SecureTokenStore\.saveMentorToken\(context, result\.getString\("token"\)\)/);
  assert.match(client,/val previousMentorToken = SecureTokenStore\.loadMentorToken\(context\)/);
});

test('one 401 may renew a previously owner-approved pairing, never auto-enrol a new device',()=>{
  const recovery=between(client,'private fun mentorRequestWithOneAuthRecovery(', 'fun isConfigured()');
  assert.match(recovery,/catch \(rejected: MentorAuthenticationRejected\)/);
  assert.match(recovery,/if \(previousMentorToken\.isNullOrBlank\(\)\) throw rejected/);
  assert.match(recovery,/synchronized\(mentorRecoveryLock\)/);
  assert.match(recovery,/pairMentorOnWorker\(base\)/);
  assert.match(recovery,/if \(!paired\.approved\)/);
  assert.match(recovery,/SecureTokenStore\.clearMentorToken\(context\)/);
  assert.equal((recovery.match(/request\(method, url, body, authenticated = true, token = /g)||[]).length,2);
  assert.match(client,/if \(code == HttpURLConnection\.HTTP_UNAUTHORIZED\)/);
});

test('retry of pairing probe is bounded and never silently duplicates Groq questions',()=>{
  const challenge=between(client,'private fun pairingChallengeWithOneRetry(', 
    'private fun mentorRequestWithOneAuthRecovery(');
  assert.match(challenge,/request\("GET", url, null, authenticated = false\)/);
  assert.match(challenge,/Thread\.sleep\(1_000L\)/);
  assert.match(challenge,/first is IOException/);
  assert.match(challenge,/first\.statusCode in 502\.\.504/);
  assert.doesNotMatch(challenge,/pairing\/redeem|mentor\/answer|consent/);

  const mentor=between(client,'fun groqMentor(', 'private fun request(');
  assert.match(mentor,/put\("consent", true\)/);
  assert.match(mentor,/mentorRequestWithOneAuthRecovery\(/);
  assert.doesNotMatch(mentor,/Thread\.sleep|while\s*\(|retryOnTimeout|retryOnRateLimit/);
});

test('a new backend URL cannot inherit the previous backend bearer credential',()=>{
  const url=between(client,'fun saveBackendUrl(', 'fun hasToken()');
  assert.match(url,/if \(previous\.isNotBlank\(\) && previous != normalized\)/);
  assert.match(url,/SecureTokenStore\.clearMentorToken\(context\)/);
});

test('development center gives an optional coding mentor with explicit question confirmation',()=>{
  assert.match(activity,/CenterAction\("GROQ · CODING-ZWEITMEINUNG \(OPTIONAL\)"\)/);
  assert.match(activity,/showGroqMentorDialog\(/);
  assert.match(activity,/setPositiveButton\("EINMALIG SENDEN"\)/);
  assert.match(activity,/setNegativeButton\("LOKAL BLEIBEN", null\)/);
  assert.match(activity,/UNBESTÄTIGT/);
  assert.match(server,/app\.post\("\/v1\/mentor\/answer", mentorAuth,/);
  assert.match(server,/req\.body\?\.consent !== true/);
  assert.doesNotMatch(activity,/GroqMentor\.answer\(/);
});

test('responses and HTTP connections close without retrying failed work and secrets',()=>{
  assert.match(client,/finally \{\s*connection\.disconnect\(\)/);
  assert.match(client,/throw BackendHttpFailure\(code, message\)/);
  assert.doesNotMatch(client,/Log\.(d|e)\(.*(token|question)/);
});
