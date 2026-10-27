#!/usr/bin/env bash
# Host compile of actual Shizuku adapters and unchanged committed AIDL fallback.
# Real android-all + official app-versioned dependencies; no Android API stubs.
# BuildConfig constants are a generated compile fixture, not an APK or device run.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
deps=${CONTINUUM_FABRIC_JVM_DEPS:?official Kotlin/JUnit/android-all jars}
shellDeps=${CONTINUUM_SHELL_ANDROID_DEPS:?official Shizuku13.1.5 and androidx-annotation1.9.1 jars}
out=${1:?external output directory}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
cp=$(find "$deps" "$shellDeps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
# android-all includes its own java.* classes. ECJ on a modular host must use
# the real host JRE for those; retain all actual Android classes unchanged.
python3 - "$deps/android-all-15.jar" "$out/android-framework.jar" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as src, zipfile.ZipFile(sys.argv[2], 'w') as dst:
    for item in src.infolist():
        if not item.filename.startswith(('java/', 'javax/', 'sun/', 'jdk/')) and item.filename != 'module-info.class':
            dst.writestr(item,src.read(item.filename))
PY
javaCp=${cp//"$deps/android-all-15.jar:"/}
javaCp="$out/android-framework.jar:$javaCp"
mkdir -p "$out/classes" "$out/generated"
cat > "$out/generated/BuildConfig.java" <<'JAVA'
package at.heinz.deviceai;
public final class BuildConfig {
    public static final boolean DEBUG = false;
    public static final int VERSION_CODE = 25;
}
JAVA
if command -v javac >/dev/null; then
    javaCompiler=(javac -source 17 -target 17)
elif [[ -f "$shellDeps/ecj.jar" ]]; then
    javaCompiler=(java -jar "$shellDeps/ecj.jar" -17)
else
    echo 'UNAVAILABLE: install javac or official Eclipse ecj.jar' >&2
    exit 69
fi
"${javaCompiler[@]}" -classpath "$javaCp" -d "$out/classes" \
    "$root/app/src/termux/java/at/heinz/deviceai/IDeviceShell.java" "$out/generated/BuildConfig.java" \
    > "$out/javac.txt" 2>&1
sources=()
for name in AgentToolLayer ToolRegistry AgentProtocol ToolExecutionReceiptStore CommandInterpreter \
    DeviceStorageProbe ShellExecution BoundedShellProcess DeviceToolExecutor DeviceToolExecutorRunner ShizukuBridge ShellUserService; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/$name.kt")
done
sha256sum "${sources[@]}" "$root/app/src/termux/java/at/heinz/deviceai/IDeviceShell.java" > "$out/source-blobs.sha256"
java -Xmx512m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
    -classpath "$out/classes:$cp" -jvm-target 17 -d "$out/classes" "${sources[@]}" > "$out/compile.txt" 2>&1
echo 'ANDROID_SHELL_ADAPTER_COMPILE_PASS (host; no APK/device execution)'
