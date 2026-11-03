#!/bin/bash
set -euo pipefail
qa_source=${1:?source SHA}
qa_checkout=${2:?candidate checkout}
qa_out=${3:?fresh absolute output}
[[ "$qa_out" == /* && ! -e "$qa_out" ]]
cd "$qa_checkout"
[[ "$(git rev-parse HEAD)" == "$qa_source" ]]
git diff --quiet HEAD -- app scripts .github tools backend
mkdir -p "$qa_out"
source /workspace/continuum-build-env/env.sh
snapshot() {
 python3 - "$qa_out/$1.json" "$qa_source" <<'PY'
import sys,json,hashlib
from pathlib import Path
files=['app/src/main/java/at/heinz/deviceai/ContinuumBotRosterDialog.kt','app/src/main/java/at/heinz/deviceai/AgentActivity.kt','app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt']
test=Path('/workspace/synapse-ui-review/tools/synapse-ui-review/IndependentBotRosterUiTest.kt');init=Path('/workspace/synapse-ui-review/tools/synapse-ui-review/source.init.gradle')
Path(sys.argv[1]).write_text(json.dumps({'source':sys.argv[2],'sourceSha256':{f:hashlib.sha256(Path(f).read_bytes()).hexdigest() for f in files},'testSha256':hashlib.sha256(test.read_bytes()).hexdigest(),'sourceInitSha256':hashlib.sha256(init.read_bytes()).hexdigest()},indent=2)+'\n')
PY
}
snapshot before
set +e
gradle --no-daemon --max-workers=2 --console=plain -I /workspace/continuum-build-env/test-jvm21.init.gradle -I /workspace/synapse-ui-review/tools/synapse-ui-review/source.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PsynapseUiReviewOnly=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > "$qa_out/gradle.log" 2>&1
qa_exit=$?
set -e
printf '%s\n' "$qa_exit" > "$qa_out/gradle.exit"
snapshot after
cmp "$qa_out/before.json" "$qa_out/after.json"
if [[ -f app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentBotRosterUiTest.xml ]]; then
 cp app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentBotRosterUiTest.xml "$qa_out/junit.xml"
 python3 - "$qa_out/junit.xml" <<'PY'
import sys,xml.etree.ElementTree as E
r=E.parse(sys.argv[1]).getroot();print(r.attrib)
for c in r.findall('testcase'):
 f=c.find('failure')
 if f is not None: print(c.attrib.get('name'),f.attrib.get('message'))
PY
fi
exit "$qa_exit"
