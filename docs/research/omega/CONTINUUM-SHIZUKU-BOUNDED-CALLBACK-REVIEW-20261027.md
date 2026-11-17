# Continuum — bounded callback and checked Binder single-flight review (2026-10-27)

**Scope:** Isolated review branch from `7ca4702d1012a5dceb6c2bb8efebe8c8534b0ff7` (Draft PR #179). No modifications to PR #179, main, release approval, SWB1 weights, owner APK or signing state.

## Confirmed defects and minimal correction

1. `DeviceToolExecutorRunner.execute` previously waited indefinitely on `CountDownLatch.await()` when a device adapter never called back. The enclosing `ToolGate` has a deadline and interrupts the runner, but it does not independently bound a direct use of this adapter. The new runner rejects missing callbacks after at most **18,000ms**, preserving `ToolBackendFailure(TIMED_OUT)`. An interrupted wait preserves the thread interrupt flag and fails with `CANCELLED`. An atomic first-result latch rejects duplicate callbacks; a callback arriving **after** timeout cannot change the already returned failure.
2. `ShizukuBridge.executeChecked` previously spawned one new daemon Binder thread per call. `ToolGate` Future cancellation cannot guarantee termination of a remote Binder transaction; repeated delayed responses could cause an unbounded worker-thread pileup. The new `CheckedShellCallDispatcher` accepts **at most one checked call in-flight** per bridge and fails closed for overlapping calls. The slot is released only after the underlying task really returns, **before** the legitimate next callback starts a sequential request. A timeout or `stop()` does not forcibly release the slot. If Binder blocks forever, the bridge intentionally refuses subsequent checked actions rather than starting unbounded threads.
3. A stale result after a stop/binder death/service reconnect could be misattributed if the Binder proxy object was reused. A monotonic atomic `checkedSession` generation increments for stop, binder death and service connect/disconnect; both before and after the Binder request the checked call verifies that the original generation and service still match. Stale results are rejected, never published as success.

These repairs do **not** claim the OS aborted or rolled back the remote operation, or that arbitrary subprocess descendants were terminated. They restrict concurrency, status and receipt trust rather than silently retrying ambiguous privileged actions.

## New files and exact source identity

- `app/src/main/java/at/heinz/deviceai/DeviceToolExecutorRunner.kt`: Git blob `d07e972e638db2a3d03854058c86e346c429b249`
- `app/src/main/java/at/heinz/deviceai/CheckedShellCallDispatcher.kt`: Git blob `efa95edab48b89515d5c553779fa6910ae7f392e`
- `app/src/main/java/at/heinz/deviceai/ShizukuBridge.kt`: initial isolated integration blob `ef5e3e21ae4742952a7adf2e1ba0193f1573535c`
- `app/src/test/java/at/heinz/deviceai/DeviceToolExecutorRunnerTest.kt`: Git blob `dafeab4ee6a56ceacad54f1a54b0a20bae6016db`
- `app/src/test/java/at/heinz/deviceai/CheckedShellCallDispatcherTest.kt`: Git blob `2f07b9461e72b59b8aca0859706a7344e38a235b`

## Actually executed local verification

Host: Java 21.0.12.1, `kotlinc-jvm 1.9.0`, no Android SDK, AGP or native Shizuku runtime.

Two **exact-source, Git-blob-matched** main Kotlin modules and both exact test files were compiled with `kotlinc`. A minimal Java-only JUnit-4-compatible `org.junit.Test`/assertion shim and a small local `ToolCallRequest` / `ToolRunner` / `DeviceToolExecutor` stub supplied declarations that normally come from the Android module. The shim was used only by the local test harness; it is **not committed as an application dependency**.

Actual `kotlinc` exited 0 and the Java reflection test runner executed the `@Test` methods:

- `DeviceToolExecutorRunnerTest`: 6 PASS, 0 FAIL — normal success, callback error, timeout/no callback, late success, duplicate callback, interrupted waiter.
- `CheckedShellCallDispatcherTest`: 5 PASS, 0 FAIL — blocked Binder, 100 overlapping requests (one actual backend start), stale generation, recovery after failure, thread-creation error.
- **Combined: 11 PASS / 0 FAIL**.

The host compile was not a full Android `ShizukuBridge.kt` compilation because Android/Shizuku dependencies are absent. It also was **not** a Gradle JUnit invocation; production dependency/API parity still needs verification. Source hashes were checked with `git hash-object` on the local Kotlin compilation inputs.

## Required before approval

1. Run `./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.DeviceToolExecutorRunnerTest' --tests 'at.heinz.deviceai.CheckedShellCallDispatcherTest'` (or the actual application variant-specific equivalent) on a complete SDK/AGP toolchain; compile `ShizukuBridge.kt` with real Android/Shizuku types.
2. Demonstrate real Binder hanging/returning late, stop/rebind and callback failure/timeout on a disposable Shizuku testing environment. Inspect thread counts and ensure no successful ToolGate receipts from stale callbacks; do not retest uncertain destructive actions automatically.
3. Test inherited-open-pipe process descendants using safely owned test PIDs. The current `BoundedShellProcess` only directly destroys the `Process`; its grandchildren and blocked output threads still have **no guaranteed termination**.
4. Run the actual main-app AGP/SDK36 build, verify existing owner package/signature/version and data-preserving Fold7 installation. Keep the owner-v25 source approval freeze until separately reviewed.
5. Merge only after independent compatibility checks, never directly to `main`. Preserve all sources and tests in ordinary editable GitHub branches.

**Not claimed:** full Android build, APK signing, physical Fold7 test, Shizuku runtime test, process-tree termination, increased SWB1 intelligence, production release.
