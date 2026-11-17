import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relative) => fs.readFileSync(path.join(repo, relative), 'utf8');
const aidl = read('app/src/main/aidl/at/heinz/deviceai/IDeviceShell.aidl');
const binder = read('app/src/termux/java/at/heinz/deviceai/IDeviceShell.java');
const gradle = read('app/build.gradle.kts');
const builder = read('scripts/continuum-mobile-builder.mjs');
const shell = read('app/src/main/java/at/heinz/deviceai/ShellUserService.kt');
const bridge = read('app/src/main/java/at/heinz/deviceai/ShizukuBridge.kt');

test('Termux Binder interface matches the exact existing AIDL descriptor and stable wire IDs', () => {
  assert.match(aidl, /package at\.heinz\.deviceai;/);
  assert.match(aidl, /String execute\(String command\)\s*=\s*1;/);
  assert.match(aidl, /void destroy\(\)\s*=\s*16777114;/);
  assert.match(binder, /String DESCRIPTOR\s*=\s*"at\.heinz\.deviceai\.IDeviceShell"/);
  assert.match(binder, /TRANSACTION_execute\s*=\s*IBinder\.FIRST_CALL_TRANSACTION/);
  assert.match(binder, /TRANSACTION_destroy\s*=\s*16777114/);
});

test('both binder methods preserve argument, return and exception marshalling', () => {
  assert.match(binder, /extends Binder implements IDeviceShell/);
  assert.match(binder, /String execute\(String command\) throws RemoteException/);
  assert.match(binder, /void destroy\(\) throws RemoteException/);
  assert.match(binder, /data\.enforceInterface\(DESCRIPTOR\)/);
  assert.match(binder, /data\.readString\(\)/);
  assert.match(binder, /data\.writeInterfaceToken\(DESCRIPTOR\)/);
  assert.match(binder, /data\.writeString\(command\)/);
  assert.match(binder, /reply\.writeNoException\(\)/);
  assert.match(binder, /reply\.readException\(\)/);
  assert.match(binder, /reply\.readString\(\)/);
  assert.match(binder, /remote\.transact\(TRANSACTION_execute, data, reply, 0\)/);
  assert.match(binder, /remote\.transact\(TRANSACTION_destroy, data, reply, 0\)/);
  assert.match(binder, /finally\s*\{\s*reply\.recycle\(\);\s*data\.recycle\(\);/);
});

test('desktop and production configurations retain canonical AIDL generation', () => {
  assert.match(gradle, /providers\.gradleProperty\("deviceAiMobileAidlFallback"\)\.orNull == "true"/);
  assert.match(gradle, /if \(deviceAiMobileAidlFallback\) \{\s*java\.srcDir\("src\/termux\/java"\)/);
  assert.match(gradle, /aidl = !deviceAiMobileAidlFallback/);
  assert.match(gradle, /buildConfig = true/);
});

test('only the bounded mobile builder activates the alternative, never changes source or tools', () => {
  assert.match(builder, /'-PdeviceAiMobileAidlFallback=true'/);
  assert.match(builder, /':app:testDebugUnitTest'/);
  assert.match(builder, /':app:lintDebug'/);
  assert.match(builder, /':app:assembleDebug'/);
  assert.doesNotMatch(builder, /writeFileSync\([^)]*IDeviceShell|git\s+push/);
});

test('Shizuku user service, endpoint and shell gate keep their original IPC types', () => {
  assert.match(shell, /class ShellUserService\(\)\s*:\s*IDeviceShell\.Stub\(\)/);
  assert.match(shell, /override fun execute\(command: String\): String/);
  assert.match(shell, /override fun destroy\(\)/);
  assert.match(bridge, /IDeviceShell\.Stub\.asInterface\(service\)/);
  assert.match(bridge, /service\.execute\(command\)/);
});
