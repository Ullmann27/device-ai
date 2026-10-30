import test from 'node:test';
import assert from 'node:assert/strict';
import {classifyJUnitFailures} from './test-failure-environment.mjs';

// Verbatim excerpt of the real harness output on PR #243 head (2026-10-30, POSIX locale, JAVA_TOOL_OPTIONS set).
const REAL=`Picked up JAVA_TOOL_OPTIONS: -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
1) successfulGermanOutputRoundTripsThroughStrictVersionedWire(at.heinz.deviceai.ShellExecutionTest)
org.junit.ComparisonFailure: expected:<Gr[??e vom Ger?]t> but was:<Gr[??e vom Ger?]t>
	at org.junit.Assert.assertEquals(Assert.java:117)
2) concurrentProcessReviewsAcceptOnlyOneDigest(at.heinz.deviceai.learning.interactive.ResearchFeedbackMultiprocessTest)
java.lang.AssertionError: expected:<1> but was:<0>
	at org.junit.Assert.fail(Assert.java:89)

FAILURES!!!
Tests run: 1265,  Failures: 2
`;
test('the real encoding artefact is recognised; the fragile multiprocess test stays a code/test failure',()=>{
 const r=classifyJUnitFailures(REAL);
 assert.deepEqual(r.failures.map(f=>f.verdict),['HOST_ENVIRONMENT_SUSPECTED','CODE_OR_TEST_FAILURE']);
 assert.deepEqual(r.failures[0].reasons,['ENCODING_ARTEFACT']);
 assert.equal(r.hints.length,1);
 assert.equal(r.passGranted,false);assert.equal(r.learningLabel,null);
});
test('a genuine value mismatch is never blamed on the host',()=>{
 const r=classifyJUnitFailures('1) sums(at.x.MathTest)\norg.junit.ComparisonFailure: expected:<Grüße> but was:<Gruesse>\n');
 assert.equal(r.failures[0].verdict,'CODE_OR_TEST_FAILURE');
 const same=classifyJUnitFailures('1) text(at.x.T)\norg.junit.ComparisonFailure: expected:<abc> but was:<abc>\n');
 assert.equal(same.failures[0].verdict,'CODE_OR_TEST_FAILURE','identical without ? is a toString/identity issue, not encoding');
});
test('network, disk, memory and timeout signatures are host suspicions, each named',()=>{
 const out=['1) a(x.A)','java.net.UnknownHostException: dl.google.com','2) b(x.B)','java.io.IOException: No space left on device',
  '3) c(x.C)','java.lang.OutOfMemoryError: Java heap space','4) d(x.D)','org.junit.runners.model.TestTimedOutException: test timed out after 100 milliseconds'].join('\n');
 assert.deepEqual(classifyJUnitFailures(out).failures.map(f=>f.reasons[0]),['NETWORK_BLOCKED','DISK_FULL','MEMORY','TIMEOUT']);
});
test('invalid input is rejected',()=>{
 assert.throws(()=>classifyJUnitFailures(null),/INVALID_TEST_OUTPUT/);
 assert.equal(classifyJUnitFailures('OK (12 tests)').failures.length,0);
});
