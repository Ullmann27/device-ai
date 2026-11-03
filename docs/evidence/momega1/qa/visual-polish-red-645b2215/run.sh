#!/bin/bash
source /workspace/momega1-build-env/env.sh
cd /workspace/momega1-build-env/guide-green-source-a39b614b
git rev-parse HEAD > '/workspace/momega1-build-env/visual-polish-red-645b2215/source-before.txt'
git status --porcelain >> '/workspace/momega1-build-env/visual-polish-red-645b2215/source-before.txt'
taskset -c 0,1 gradle --no-daemon --max-workers=2 --console=plain -I '/workspace/momega1-build-env/visual-polish-red-645b2215/init.gradle' -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest > '/workspace/momega1-build-env/visual-polish-red-645b2215/gradle.txt' 2>&1
printf '%s\n' "$?" > '/workspace/momega1-build-env/visual-polish-red-645b2215/gradle.exit'
cp app/build/test-results/testDebugUnitTest/TEST-at.heinz.deviceai.IndependentFinalVisualPolishUiTest.xml '/workspace/momega1-build-env/visual-polish-red-645b2215/junit.xml'
git rev-parse HEAD > '/workspace/momega1-build-env/visual-polish-red-645b2215/source-after.txt'
git status --porcelain >> '/workspace/momega1-build-env/visual-polish-red-645b2215/source-after.txt'
