#!/usr/bin/env bash
# Kotlin type/compile check of the Android main source set WITHOUT the Android SDK or Gradle.
#
# Use this when dl.google.com (Android SDK, Google Maven) is unreachable. It compiles app/src/main/java with kotlinc
# against official Maven Central artifacts:
#   android-all-15.jar  org.robolectric:android-all:15-robolectric-13954326 (Android 15 framework classes)
#   okhttp.jar okio.jar com.squareup.okhttp3:okhttp:4.12.0, com.squareup.okio:okio-jvm:3.6.0
#   shizuku-*.jar       classes.jar of dev.rikka.shizuku:{api,aidl,shared,provider}:13.1.5
# plus generated stubs: R (from app/src/main/res), BuildConfig, AIDL interfaces (from app/src/main/aidl) and
# androidx.annotation.Keep. LiteRT-LM exists only on Google Maven; compile errors in the files that import it are
# listed separately and do not fail the check. android-all carries strict android.annotation.Nullable where the SDK
# android.jar uses lenient "Recently" annotations, so those are downgraded to warnings like in a Gradle build.
# This is NOT an APK build, not lint and not a device test: report KOTLIN_COMPILE_CHECKED, never APK_BUILT.
#
# Usage: CONTINUUM_ANDROID_DEPS=/path/to/jars scripts/android-kotlin-compile-check.sh [out-dir]
set -euo pipefail
deps=${CONTINUUM_ANDROID_DEPS:?set CONTINUUM_ANDROID_DEPS to the directory with the jars listed above}
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:-$(mktemp -d)}
mkdir -p "$out/stubs/at/heinz/deviceai" "$out/classes"
command -v kotlinc >/dev/null || { echo "kotlinc not on PATH" >&2; exit 2; }
for jar in android-all-15.jar okhttp.jar okio.jar shizuku-api.jar shizuku-aidl.jar shizuku-shared.jar shizuku-provider.jar; do
  [ -s "$deps/$jar" ] || { echo "missing $deps/$jar" >&2; exit 2; }
done

python3 - "$root/app/src/main/res" "$out/stubs/at/heinz/deviceai/R.java" <<'PY'
import os, re, sys
res, target = sys.argv[1], sys.argv[2]
types = {}
for folder in sorted(os.listdir(res)):
    kind = folder.split("-")[0]
    for name in sorted(os.listdir(os.path.join(res, folder))):
        path = os.path.join(res, folder, name)
        if kind != "values":
            types.setdefault(kind, set()).add(name.split(".")[0])
        text = open(path, encoding="utf-8", errors="replace").read() if name.endswith(".xml") else ""
        types.setdefault("id", set()).update(re.findall(r'@\+id/([A-Za-z0-9_]+)', text))
        if kind == "values":
            for tag, value in re.findall(r'<(string|color|dimen|bool|integer|style|array|string-array|plurals)\s+name="([^"]+)"', text):
                kind2 = {"string-array": "array"}.get(tag, tag)
                types.setdefault(kind2, set()).add(value.replace(".", "_"))
lines = ["package at.heinz.deviceai;", "public final class R {"]
n = 0x7f000000
for kind in sorted(types):
    lines.append(f"  public static final class {kind} {{")
    for name in sorted(types[kind]):
        n += 1
        lines.append(f"    public static final int {name} = {n};")
    lines.append("  }")
lines.append("}")
open(target, "w").write("\n".join(lines) + "\n")
PY

python3 - "$root/app/src/main/aidl" "$out/stubs" <<'PY'
import os, re, sys
src, out = sys.argv[1], sys.argv[2]
for folder, _, files in os.walk(src):
    for name in files:
        if not name.endswith(".aidl"):
            continue
        text = open(os.path.join(folder, name), encoding="utf-8").read()
        package = re.search(r"package\s+([\w.]+);", text).group(1)
        iface = re.search(r"interface\s+(\w+)", text).group(1)
        methods = []
        for ret, method, params in re.findall(r"(\w[\w.<>\[\]]*)\s+(\w+)\s*\(([^)]*)\)\s*(?:=\s*\d+)?\s*;", text):
            params = ", ".join(re.sub(r"^(in|out|inout)\s+", "", p.strip()) for p in params.split(",") if p.strip())
            methods.append(f"  {ret} {method}({params}) throws android.os.RemoteException;")
        target = os.path.join(out, *package.split("."), iface + ".java")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        open(target, "w").write("\n".join([f"package {package};", f"public interface {iface} extends android.os.IInterface {{",
            *methods,
            f"  public static abstract class Stub extends android.os.Binder implements {iface} {{",
            f"    public static {iface} asInterface(android.os.IBinder obj) {{ return null; }}",
            "    @Override public android.os.IBinder asBinder() { return this; }", "  }", "}"]) + "\n")
PY
mkdir -p "$out/stubs/androidx/annotation"
printf 'package androidx.annotation;\npublic @interface Keep {}\n' > "$out/stubs/androidx/annotation/Keep.java"

version_name=$(grep -m1 'versionName = ' "$root/app/build.gradle.kts" | grep -o '"[^"]*"' | tail -1)
cat > "$out/stubs/at/heinz/deviceai/BuildConfig.java" <<EOF
package at.heinz.deviceai;
public final class BuildConfig {
  public static final boolean DEBUG = false;
  public static final String APPLICATION_ID = "at.heinz.deviceai";
  public static final int VERSION_CODE = 1;
  public static final String VERSION_NAME = $version_name;
}
EOF

cp="$deps/android-all-15.jar:$deps/okhttp.jar:$deps/okio.jar:$deps/shizuku-api.jar:$deps/shizuku-aidl.jar:$deps/shizuku-shared.jar:$deps/shizuku-provider.jar"
mapfile -t sources < <(find "$root/app/src/main/java" -name '*.kt' -o -name '*.java' | sort)
unavailable=$(grep -l 'com.google.ai.edge.litertlm' "${sources[@]}" | sed "s#^$root/##" || true)
set +e
mapfile -t stubs < <(find "$out/stubs" -name '*.java' | sort)
kotlinc "${sources[@]}" "${stubs[@]}" -cp "$cp" -jvm-target 17 -nowarn -d "$out/classes" \
  -Xnullability-annotations=@android.annotation:warn -XXLanguage:+BreakContinueInInlineLambdas > "$out/kotlinc.log" 2>&1
status=$?
set -e
# Only ordinary diagnostic exit 1 can be the documented missing-LiteRT case.
# A killed/crashed compiler may also have printed those diagnostics before dying.
if [ "$status" -ne 0 ] && [ "$status" -ne 1 ]; then
  echo "KOTLIN_COMPILE_CHECK_FAILED: compiler exited $status; see $out/kotlinc.log"
  exit "$status"
fi
if grep -Eq '(^exception:|^Exception in thread|OutOfMemoryError|Internal compiler error|^error:)' "$out/kotlinc.log"; then
  echo "KOTLIN_COMPILE_CHECK_FAILED: compiler failure; see $out/kotlinc.log"
  if [ "$status" -eq 0 ]; then exit 1; else exit "$status"; fi
fi
errors=$(grep -E '^[^ ]+\.(kt|java):[0-9]+:[0-9]+: error:' "$out/kotlinc.log" || true)
external=""; own=""
while IFS= read -r line; do
  [ -z "$line" ] && continue
  file=${line%%:*}; file=${file#"$root/"}
  if grep -qxF "$file" <<< "$unavailable"; then external+="$line"$'\n'; else own+="$line"$'\n'; fi
done <<< "$errors"
echo "sources=${#sources[@]} kotlinc_exit=$status"
echo "errors in files that need LiteRT-LM (Google Maven, unavailable here): $(printf '%s' "$external" | grep -c . || true)"
if [ -n "$own" ]; then
  echo "KOTLIN_COMPILE_CHECK_FAILED: $(printf '%s' "$own" | grep -c .) error(s) outside the LiteRT-LM files"
  printf '%s' "$own" | sed "s#^$root/##" | head -50
  exit 1
fi
if [ "$status" -ne 0 ] && [ -z "$external" ]; then
  echo "KOTLIN_COMPILE_CHECK_FAILED: kotlinc exit $status without parsed errors; see $out/kotlinc.log"
  exit 1
fi
echo "KOTLIN_COMPILE_CHECKED: no compile error outside the LiteRT-LM files (not an APK build)"
