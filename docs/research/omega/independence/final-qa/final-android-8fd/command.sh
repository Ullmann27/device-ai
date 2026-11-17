source /workspace/continuum-build-env/env.sh
exec gradle --no-daemon --max-workers=2 --console=plain \
 -I /workspace/continuum-build-env/test-jvm21.init.gradle \
 -I "$INDEPENDENT_REVIEW_INIT" \
 -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m" \
 -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
 -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \
 -Pkotlin.compiler.execution.strategy=in-process \
 :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin
