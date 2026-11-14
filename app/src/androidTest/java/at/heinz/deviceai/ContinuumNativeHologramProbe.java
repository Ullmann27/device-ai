package at.heinz.deviceai;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.Surface;
import android.view.PixelCopy;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inspector.WindowInspector;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the existing surface lifecycle. */
public final class ContinuumNativeHologramProbe {
    private static final String PACKAGE = "at.heinz.deviceai.mentorpaired";
    private static final String ACTIVITY = "at.heinz.deviceai.AgentActivity";
    private static final String HOLOGRAM = "at.heinz.deviceai.ContinuumHologramView";
    private static final long MAIN_TIMEOUT_MS = 30000;
    private final Instrumentation instrumentation;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final JSONObject report = new JSONObject();
    private final JSONArray actions = new JSONArray();
    private final JSONArray samples = new JSONArray();
    private final long started = SystemClock.elapsedRealtimeNanos();
    private File directory;
    private Activity activity;
    private View hologram;

    private ContinuumNativeHologramProbe(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    public static Bundle run(Instrumentation instrumentation) {
        return new ContinuumNativeHologramProbe(instrumentation).run();
    }

    private Bundle run() {
        Bundle result = new Bundle();
        Instrumentation.ActivityMonitor monitor = null;
        boolean complete = false;
        try {
            require(PACKAGE.equals(instrumentation.getTargetContext().getPackageName()), "Disposable target required");
            require((PACKAGE + ".test").equals(instrumentation.getContext().getPackageName()), "Test package required");
            require(Build.VERSION.SDK_INT == 35, "API 35 required");
            require(Arrays.asList(Build.SUPPORTED_ABIS).contains("x86_64"), "x86_64 required");
            require("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE), "Emulator required");
            require((instrumentation.getTargetContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    "Debug target required");
            directory = new File(instrumentation.getTargetContext().getCacheDir(), "code59-hologram-diagnostic");
            require(directory.isDirectory() || directory.mkdirs(), "Evidence directory unavailable");
            report.put("schema", 1).put("scope", "EXISTING_HOME_STAGE_DIAGNOSTIC_ONLY")
                    .put("package", PACKAGE).put("pid", Process.myPid()).put("sdk", Build.VERSION.SDK_INT)
                    .put("hardware", Build.HARDWARE).put("started_elapsed_nanos", started)
                    .put("limitations", "Instrumentation and readback affect timing; captures are sequential. " +
                            "TextureView.getBitmap may return an empty bitmap after copy failure. " +
                            "SurfaceTexture timestamps apply only to the same instance. No visible-avatar or UI-flow pass.")
                    .put("actions", actions).put("samples", samples);
            phase("launch");
            monitor = instrumentation.addMonitor(ACTIVITY, null, false);
            instrumentation.getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName(PACKAGE, ACTIVITY).addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = monitor.waitForActivityWithTimeout(MAIN_TIMEOUT_MS);
            require(activity != null && ACTIVITY.equals(activity.getClass().getName()), "Actual AgentActivity unavailable");
            long deadline = SystemClock.elapsedRealtime() + MAIN_TIMEOUT_MS;
            boolean visible = false;
            while (SystemClock.elapsedRealtime() < deadline) {
                visible = onMain(() -> {
                    require(!activity.isFinishing() && !activity.isDestroyed(), "Activity closed");
                    if (closeExistingDialog()) return false;
                    Object existing = field(activity, "hologramView");
                    if (!(existing instanceof View)) return false;
                    View view = (View) existing;
                    require(HOLOGRAM.equals(view.getClass().getName()), "Unexpected hologram class");
                    if (!view.isAttachedToWindow() || !view.isShown() || !view.hasWindowFocus()
                            || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
                    ViewParent parent = view.getParent();
                    while (parent instanceof View && parent != activity.getWindow().getDecorView()) parent = parent.getParent();
                    require(parent == activity.getWindow().getDecorView(), "Hologram detached from actual Activity");
                    Rect bounds = new Rect();
                    if (!view.getGlobalVisibleRect(bounds) || bounds.isEmpty()) return false;
                    hologram = view;
                    return true;
                });
                if (visible) break;
                SystemClock.sleep(100);
            }
            require(visible, "Visible home stage unavailable");
            phase("first_sample");
            JSONObject first = onMain(this::sample);
            samples.put(first);
            persist();
            long observationDeadline = SystemClock.elapsedRealtime() + 90000;
            JSONObject current = first;
            boolean settled = false;
            while (SystemClock.elapsedRealtime() < observationDeadline && samples.length() < 96) {
                boolean failed = current.optBoolean("native3dFailed") || current.optBoolean("cockpitWorldFailed");
                settled = failed || (!current.optBoolean("assetLoading") && current.optBoolean("texture_available")
                        && current.optBoolean("renderer_present") && current.optBoolean("surface_valid")
                        && current.optBoolean("cockpit_ready") && current.optBoolean("native3dDrawn")
                        && current.optLong("surface_texture_timestamp") > 0);
                if (settled) break;
                SystemClock.sleep(1000);
                current = onMain(this::sample);
                samples.put(current);
                persist();
            }
            report.put("loading_observation_complete", settled).put("comparison_first_sample", samples.length() - 1);
            SystemClock.sleep(1600);
            samples.put(onMain(this::sample));
            report.put("comparison_second_sample", samples.length() - 1);
            persist();

            phase("display_capture");
            long screenStarted = SystemClock.elapsedRealtimeNanos();
            Bitmap screen = instrumentation.getUiAutomation().takeScreenshot();
            require(screen != null, "Actual display screenshot unavailable");
            long screenFinished = SystemClock.elapsedRealtimeNanos();
            report.put("screen", saveBitmap(screen, "hologram-screen.png")
                    .put("started_elapsed_nanos", screenStarted).put("finished_elapsed_nanos", screenFinished));
            persist();

            phase("texture_capture");
            samples.put(onMain(this::sample));
            persist();
            long textureStarted = SystemClock.elapsedRealtimeNanos();
            Bitmap texture = onMain(() -> {
                TextureView actual = (TextureView) field(hologram, "nativeTexture");
                require(actual != null && actual.isAvailable(), "Actual native texture unavailable");
                require(actual.getWidth() > 0 && actual.getHeight() > 0, "Texture has no measured bounds");
                return actual.getBitmap();
            });
            report.put("texture_readback", new JSONObject().put("queued_elapsed_nanos", textureStarted)
                    .put("returned_elapsed_nanos", SystemClock.elapsedRealtimeNanos())
                    .put("thread", Looper.getMainLooper().getThread().getName()).put("copy_success_code_available", false));
            require(texture != null, "TextureView.getBitmap returned null");
            report.put("texture", saveBitmap(texture, "hologram-texture.png"));
            persist();
            phase("surface_pixel_copy");
            report.put("pixel_copy", copySurface());
            samples.put(onMain(this::sample));
            persist();
            if (!settled) captureInitialStack();
            phase("surface_resize_regression");
            verifySurfaceResize();
            require(settled, "Loading state did not settle before deadline");
            complete = true;
        } catch (Throwable error) {
            try {
                report.put("error", error.toString()).put("error_stack", android.util.Log.getStackTraceString(error));
                JSONArray stack = new JSONArray();
                for (StackTraceElement entry : Looper.getMainLooper().getThread().getStackTrace()) stack.put(entry.toString());
                report.put("actual_main_thread_stack", stack);
            } catch (Exception ignored) { }
        } finally {
            if (monitor != null) instrumentation.removeMonitor(monitor);
            if (activity != null) main.post(() -> activity.finish());
        }
        try {
            report.put("outcome", complete ? "DIAGNOSTIC_CAPTURED" : "DIAGNOSTIC_INCOMPLETE")
                    .put("finished_elapsed_nanos", SystemClock.elapsedRealtimeNanos())
                    .put("elapsed_ms", (SystemClock.elapsedRealtimeNanos() - started) / 1000000L);
            persist();
            require(directory != null, "Evidence directory not admitted");
            result.putString("report_sha256", sha256(new File(directory, "hologram-state.json")));
        } catch (Exception error) {
            complete = false;
            result.putString("evidence_error", error.toString());
        }
        result.putString("status", complete ? "ok" : "failed");
        result.putString("outcome", complete ? "DIAGNOSTIC_CAPTURED" : "DIAGNOSTIC_INCOMPLETE");
        result.putString("report_path", "cache/code59-hologram-diagnostic/hologram-state.json");
        result.putInt("pid", Process.myPid());
        result.putString("stream", "Existing home-stage diagnostic only; no UI-flow or visible-avatar pass.\n");
        return result;
    }

    private boolean closeExistingDialog() throws Exception {
        for (View root : WindowInspector.getGlobalWindowViews()) {
            if (!root.isShown() || !root.hasWindowFocus()) continue;
            String button = containsText(root, "Continuum · Gemeinsam starten") ? "SCHLIESSEN"
                    : containsText(root, "Darf ich dich beim Gespräch anschauen?") ? "NEIN" : null;
            if (button == null) continue;
            Button actual = findButton(root, button);
            require(actual != null && actual.isEnabled() && actual.isShown() && actual.isClickable(), "Actual dialog button unavailable");
            require(actual.performClick(), "Actual dialog button rejected click");
            actions.put(new JSONObject().put("button", button).put("elapsed_nanos", SystemClock.elapsedRealtimeNanos()));
            return true;
        }
        return false;
    }

    private JSONObject sample() throws Exception {
        require(Looper.myLooper() == Looper.getMainLooper(), "Mainthread sampling required");
        require(field(activity, "hologramView") == hologram, "Hologram instance replaced");
        Object nativeRenderer = field(hologram, "native3d");
        TextureView texture = (TextureView) field(hologram, "nativeTexture");
        Surface surface = (Surface) field(hologram, "nativeSurface");
        Object motion = invoke(hologram, "sampleMotion");
        Object trace = field(hologram, "avatarTrace");
        Method diagnose = trace.getClass().getDeclaredMethod("diagnose", int.class);
        diagnose.setAccessible(true);
        JSONObject state = new JSONObject().put("elapsed_nanos", SystemClock.elapsedRealtimeNanos())
                .put("main_thread", Thread.currentThread().getName()).put("hologram", viewState(hologram))
                .put("texture", texture == null ? JSONObject.NULL : viewState(texture))
                .put("texture_available", texture != null && texture.isAvailable())
                .put("texture_is_texture_view", texture instanceof TextureView)
                .put("texture_parent_is_hologram", texture != null && texture.getParent() == hologram)
                .put("surface_valid", surface != null && surface.isValid())
                .put("surface_identity", surface == null ? JSONObject.NULL : System.identityHashCode(surface))
                .put("renderer_present", nativeRenderer != null)
                .put("native_renderer_identity", nativeRenderer == null ? JSONObject.NULL : System.identityHashCode(nativeRenderer))
                .put("cockpit_ready", nativeRenderer != null && field(nativeRenderer, "cockpitStage") != null)
                .put("callbacks", invoke(motion, "getCallbackCount")).put("draws", invoke(motion, "getDrawCount"))
                .put("phase", invoke(motion, "getPhase")).put("reason", invoke(motion, "getReason"))
                .put("trace", diagnose.invoke(trace, 60)).put("trace_frame", invoke(trace, "frameNumber"))
                .put("rotation", activity.getDisplay().getRotation());
        if (nativeRenderer != null) {
            state.put("viewport_width", field(nativeRenderer, "viewportWidth"))
                    .put("viewport_height", field(nativeRenderer, "viewportHeight"));
            for (String name : new String[]{"engine", "asset", "scene", "renderer", "cockpitStage", "swapChain"}) {
                Object value = field(nativeRenderer, name);
                state.put(name + "_identity", value == null ? JSONObject.NULL : System.identityHashCode(value));
            }
            Object scene = field(nativeRenderer, "scene");
            if (scene != null) {
                require("com.google.android.filament.Scene".equals(scene.getClass().getName()), "Unexpected native scene class");
                state.put("scene_entities", scene.getClass().getMethod("getEntityCount").invoke(scene));
            }
        }
        for (String name : new String[]{"native3dDrawn", "native3dFailed", "cockpitWorldFailed", "assetLoading",
                "headEnabled", "head3dEnabled", "foregroundActive", "motionAllowed", "animationsEnabled",
                "worldBufferWidth", "worldBufferHeight"}) state.put(name, field(hologram, name));
        byte[] asset = (byte[]) field(hologram, "assetBytes");
        state.put("asset_bytes", asset == null ? 0 : asset.length);
        SurfaceTexture consumer = texture == null ? null : texture.getSurfaceTexture();
        state.put("surface_texture_identity", consumer == null ? JSONObject.NULL : System.identityHashCode(consumer));
        state.put("surface_texture_timestamp", consumer == null ? JSONObject.NULL : consumer.getTimestamp());
        Object consumerSample = invoke(trace, "consumerSample");
        state.put("consumer_generation", invoke(consumerSample, "getGeneration"));
        state.put("consumer_updates", invoke(consumerSample, "getUpdateCount"));
        JSONArray parents = new JSONArray();
        ViewParent parent = hologram.getParent();
        while (parent instanceof View && parents.length() < 12) {
            parents.put(viewState((View) parent));
            parent = parent.getParent();
        }
        state.put("ancestors", parents);
        state.put("monotonic_nanos", System.nanoTime());
        return state;
    }

    private void captureInitialStack() throws Exception {
        phase("pre_resize_native_stack");
        String nonce = UUID.randomUUID().toString().replace("-", "");
        JSONObject capture = new JSONObject().put("schema", 1).put("package", PACKAGE)
                .put("pid", Process.myPid()).put("uid", Process.myUid()).put("nonce", nonce)
                .put("requested_monotonic_nanos", System.nanoTime());
        report.put("pre_resize_native_stack", new JSONObject().put("request", capture));
        persist();
        File temporary = new File(directory, "stack-request.tmp");
        Files.write(temporary.toPath(), capture.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temporary.toPath(), new File(directory, "stack-request.json").toPath());
        File acknowledgement = new File(directory, "stack-ack.json");
        long deadline = SystemClock.elapsedRealtime() + MAIN_TIMEOUT_MS;
        while (!acknowledgement.isFile() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        require(acknowledgement.isFile() && acknowledgement.length() <= 4096, "Native stack acknowledgement missing");
        JSONObject ack = new JSONObject(new String(Files.readAllBytes(acknowledgement.toPath()), StandardCharsets.UTF_8));
        require(ack.getInt("schema") == 1 && ack.getInt("pid") == Process.myPid()
                && nonce.equals(ack.getString("nonce")), "Native stack acknowledgement mismatch");
        JSONObject evidence = report.getJSONObject("pre_resize_native_stack");
        evidence.put("acknowledgement", ack).put("acknowledged_monotonic_nanos", System.nanoTime());
        persist();
        require("CAPTURED".equals(ack.getString("status")), "Native stack capture failed");
        evidence.put("after_capture", onMain(this::sample));
        persist();
    }

    private void verifySurfaceResize() throws Exception {
        JSONObject proof = new JSONObject().put("status", "STARTED")
                .put("started_elapsed_nanos", SystemClock.elapsedRealtimeNanos());
        report.put("resize_regression", proof);
        persist();
        Map<String, Object> original = onMain(this::surfaceObjects);
        Object frame = original.get("frame");
        TextureView texture = (TextureView) original.get("texture");
        Rect bounds = (Rect) original.get("bounds");
        Method present = stagePresenter(frame.getClass());
        Exception failure = null;
        try {
            JSONObject unchanged = onMain(() -> {
                present.invoke(hologram, frame);
                Map<String, Object> same = surfaceObjects();
                requireSameSurfaceObjects(original, same);
                require(original.get("swapChain") == same.get("swapChain"), "Unchanged stage rebuilt SwapChain");
                present.invoke(hologram, copyStage(frame, 1f, true));
                Map<String, Object> pose = surfaceObjects();
                requireSameSurfaceObjects(original, pose);
                require(original.get("swapChain") == pose.get("swapChain"), "Monitor pose rebuilt SwapChain");
                return new JSONObject().put("same_dimensions_preserved", true).put("monitor_pose_preserved", true);
            });
            proof.put("unchanged", unchanged).put("before", original.get("state"));
            persist();

            Map<String, Object> stage = onMain(() -> {
                float width = ((Number) field(frame, "width")).floatValue();
                float height = ((Number) field(frame, "height")).floatValue();
                present.invoke(hologram, copyStage(frame, Math.min(.5f, 640f / Math.max(width, height)), false));
                Map<String, Object> resized = surfaceObjects();
                requireRebuilt(original, resized);
                JSONObject before = (JSONObject) original.get("state"), after = (JSONObject) resized.get("state");
                require(before.getInt("worldBufferWidth") != after.getInt("worldBufferWidth")
                        || before.getInt("worldBufferHeight") != after.getInt("worldBufferHeight"), "Stage buffer unchanged");
                return resized;
            });
            JSONObject stageProof = new JSONObject().put("changed", stage.get("state"));
            proof.put("stage", stageProof);
            persist();
            stageProof.put("rendered", awaitResizedFrame(stage, stageProof, "rendered"));
            persist();
            Map<String, Object> restoredStage = onMain(() -> {
                present.invoke(hologram, frame);
                Map<String, Object> restored = surfaceObjects();
                requireRebuilt(stage, restored);
                return restored;
            });
            stageProof.put("restored", restoredStage.get("state"));
            stageProof.put("restored_rendered", awaitResizedFrame(restoredStage, stageProof, "restored_rendered"));
            persist();

            Map<String, Object> layout = onMain(() -> {
                require(bounds.width() > 2, "Texture too narrow for real resize");
                texture.layout(bounds.left, bounds.top, bounds.right - 1, bounds.bottom);
                Map<String, Object> resized = surfaceObjects();
                requireRebuilt(restoredStage, resized);
                require(texture.getWidth() == bounds.width() - 1, "Framework resize did not apply");
                return resized;
            });
            JSONObject layoutProof = new JSONObject().put("changed", layout.get("state"));
            proof.put("texture_layout", layoutProof);
            persist();
            layoutProof.put("rendered", awaitResizedFrame(layout, layoutProof, "rendered"));
            persist();
            Map<String, Object> restoredLayout = onMain(() -> {
                texture.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
                Map<String, Object> restored = surfaceObjects();
                requireRebuilt(layout, restored);
                return restored;
            });
            layoutProof.put("restored", restoredLayout.get("state"));
            layoutProof.put("restored_rendered", awaitResizedFrame(restoredLayout, layoutProof, "restored_rendered"));
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            try {
                onMain(() -> {
                    present.invoke(hologram, frame);
                    texture.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
                    return null;
                });
            } catch (Exception cleanup) {
                proof.put("restore_error", cleanup.toString());
                if (failure == null) throw cleanup;
            }
            persist();
        }
        proof.put("status", "PASSED").put("finished_elapsed_nanos", SystemClock.elapsedRealtimeNanos());
        persist();
    }

    private Map<String, Object> surfaceObjects() throws Exception {
        Object renderer = field(hologram, "native3d");
        require(renderer != null, "Actual native renderer required for resize");
        Map<String, Object> objects = new LinkedHashMap<>();
        objects.put("native_renderer", renderer);
        for (String name : new String[]{"engine", "asset", "scene", "renderer", "cockpitStage", "swapChain"}) {
            Object value = field(renderer, name);
            require(value != null, "Actual " + name + " required for resize");
            objects.put(name, value);
        }
        TextureView texture = (TextureView) field(hologram, "nativeTexture");
        require(texture != null && texture.isAvailable(), "Actual TextureView required for resize");
        objects.put("texture", texture);
        objects.put("surface", field(hologram, "nativeSurface"));
        objects.put("surface_texture", texture.getSurfaceTexture());
        objects.put("bounds", new Rect(texture.getLeft(), texture.getTop(), texture.getRight(), texture.getBottom()));
        Object frame = field(hologram, "cockpitWorldFrame");
        require(frame != null, "Actual stage frame required for resize");
        objects.put("frame", frame);
        objects.put("state", sample());
        return objects;
    }

    private static void requireSameSurfaceObjects(Map<String, Object> before, Map<String, Object> after) {
        for (String key : new String[]{"native_renderer", "engine", "asset", "scene", "renderer", "cockpitStage",
                "texture", "surface", "surface_texture"})
            require(before.get(key) == after.get(key), "Resize replaced " + key);
    }

    private static void requireRebuilt(Map<String, Object> before, Map<String, Object> after) throws Exception {
        requireSameSurfaceObjects(before, after);
        require(before.get("swapChain") != after.get("swapChain"), "Resize retained old SwapChain");
        JSONObject oldState = (JSONObject) before.get("state"), newState = (JSONObject) after.get("state");
        require(!newState.getBoolean("native3dDrawn") && newState.getJSONObject("texture").getDouble("alpha") == 0,
                "Resize retained old submission readiness");
        require(newState.getLong("consumer_generation") > oldState.getLong("consumer_generation"),
                "Resize retained old consumer generation");
    }

    private JSONObject awaitResizedFrame(Map<String, Object> resized, JSONObject proof, String key) throws Exception {
        JSONObject changed = (JSONObject) resized.get("state");
        JSONArray progress = new JSONArray();
        proof.put(key + "_progress", progress);
        // Android EGL defaults to monotonic.
        require(changed.getLong("surface_texture_timestamp") <= changed.getLong("monotonic_nanos"),
                "Unexpected EGL resize timestamp clock");
        long timestamp = Math.max(changed.getLong("monotonic_nanos"), changed.getLong("surface_texture_timestamp"));
        long updates = changed.getLong("consumer_updates");
        long deadline = SystemClock.elapsedRealtime() + MAIN_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            Map<String, Object> current = onMain(this::surfaceObjects,
                    Math.max(1, deadline - SystemClock.elapsedRealtime()));
            requireSameSurfaceObjects(resized, current);
            require(resized.get("swapChain") == current.get("swapChain"), "SwapChain changed while awaiting resized frame");
            JSONObject state = (JSONObject) current.get("state");
            proof.put(key + "_last_observed", state);
            require(state.getLong("consumer_generation") == changed.getLong("consumer_generation"),
                    "Consumer generation changed while awaiting resized frame");
            require(state.getLong("surface_texture_timestamp") <= state.getLong("monotonic_nanos"),
                    "Unexpected EGL timestamp clock");
            require(state.getInt("worldBufferWidth") == changed.getInt("worldBufferWidth")
                    && state.getInt("worldBufferHeight") == changed.getInt("worldBufferHeight"), "Resize buffer changed unexpectedly");
            if (state.getBoolean("native3dDrawn") && state.getJSONObject("texture").getDouble("alpha") > 0
                    && state.getInt("viewport_width") == changed.getInt("worldBufferWidth")
                    && state.getInt("viewport_height") == changed.getInt("worldBufferHeight")
                    && state.getLong("surface_texture_timestamp") > timestamp
                    && state.getLong("consumer_updates") > updates) {
                progress.put(state);
                timestamp = state.getLong("surface_texture_timestamp");
                updates = state.getLong("consumer_updates");
                if (progress.length() == 2) return state;
            }
            SystemClock.sleep(Math.max(0, Math.min(100, deadline - SystemClock.elapsedRealtime())));
        }
        throw new IllegalStateException("Two fresh consumed frames required after resize");
    }

    private Method stagePresenter(Class<?> frameClass) {
        Method selected = null;
        for (Method method : hologram.getClass().getDeclaredMethods()) {
            String name = method.getName();
            if ((name.equals("presentCockpitStage") || name.startsWith("presentCockpitStage$"))
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[]{frameClass})) {
                require(selected == null, "Ambiguous stage presenter");
                selected = method;
            }
        }
        require(selected != null, "Existing stage presenter unavailable");
        selected.setAccessible(true);
        return selected;
    }

    private static Object copyStage(Object frame, float scale, boolean poseOnly) throws Exception {
        String[] names = {"width", "height", "monitorLeft", "monitorTop", "monitorRight", "monitorBottom",
                "yawDegrees", "pitchDegrees", "depthOffset"};
        Object[] values = new Object[names.length];
        Class<?>[] types = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            float value = ((Number) field(frame, names[i])).floatValue();
            if (i < 6 || i == 8) value *= scale;
            if (i == 6 && poseOnly) value += value > 0 ? -.5f : .5f;
            values[i] = value;
            types[i] = float.class;
        }
        Object copy = frame.getClass().getDeclaredMethod("copy", types).invoke(frame, values);
        require(Boolean.TRUE.equals(invoke(copy, "valid")), "Resized stage must remain valid");
        return copy;
    }

    private static JSONObject viewState(View view) throws Exception {
        Rect visible = new Rect();
        boolean intersects = view.getGlobalVisibleRect(visible);
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        JSONObject state = new JSONObject().put("class", view.getClass().getName())
                .put("identity", System.identityHashCode(view)).put("width", view.getWidth()).put("height", view.getHeight())
                .put("screen_x", location[0]).put("screen_y", location[1]).put("visible_rect", visible.toShortString())
                .put("intersects_display", intersects).put("attached", view.isAttachedToWindow()).put("shown", view.isShown())
                .put("window_focus", view.hasWindowFocus()).put("visibility", view.getVisibility())
                .put("window_visibility", view.getWindowVisibility()).put("alpha", view.getAlpha())
                .put("scale_x", view.getScaleX()).put("scale_y", view.getScaleY()).put("translation_z", view.getTranslationZ())
                .put("elevation", view.getElevation()).put("scroll_x", view.getScrollX()).put("scroll_y", view.getScrollY());
        state.put("layer_type", view.getLayerType());
        if (view instanceof ViewGroup) state.put("clip_children", ((ViewGroup) view).getClipChildren());
        return state;
    }

    private JSONObject saveBitmap(Bitmap bitmap, String name) throws Exception {
        try {
            File file = new File(directory, name);
            int[] row = new int[bitmap.getWidth()];
            long nontransparent = 0, colored = 0;
            boolean readable = bitmap.getConfig() != Bitmap.Config.HARDWARE;
            for (int y = 0; readable && y < bitmap.getHeight(); y++) {
                bitmap.getPixels(row, 0, row.length, 0, y, row.length, 1);
                for (int pixel : row) {
                    if ((pixel >>> 24) != 0) nontransparent++;
                    if ((pixel & 0x00ffffff) != 0) colored++;
                }
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "PNG encoding failed");
            }
            return new JSONObject().put("file", name).put("width", bitmap.getWidth()).put("height", bitmap.getHeight())
                    .put("nontransparent_pixels", readable ? nontransparent : JSONObject.NULL)
                    .put("nonblack_rgb_pixels", readable ? colored : JSONObject.NULL)
                    .put("bytes", file.length()).put("sha256", sha256(file));
        } finally { bitmap.recycle(); }
    }

    private JSONObject copySurface() throws Exception {
        CompletableFuture<PixelCopy.Result> completion = new CompletableFuture<>();
        AtomicLong finished = new AtomicLong();
        JSONObject state = new JSONObject().put("started_elapsed_nanos", SystemClock.elapsedRealtimeNanos());
        try {
            Surface surface = onMain(() -> (Surface) field(hologram, "nativeSurface"));
            require(surface != null && surface.isValid(), "Actual surface unavailable for PixelCopy");
            state.put("source_identity", System.identityHashCode(surface));
            PixelCopy.request(PixelCopy.Request.Builder.ofSurface(surface).build(), Runnable::run, value -> {
                finished.set(SystemClock.elapsedRealtimeNanos());
                if (!completion.complete(value) && value.getStatus() == PixelCopy.SUCCESS) value.getBitmap().recycle();
            });
            PixelCopy.Result value = completion.get(MAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            state.put("status", value.getStatus()).put("finished_elapsed_nanos", finished.get());
            if (value.getStatus() == PixelCopy.SUCCESS)
                state.put("image", saveBitmap(value.getBitmap(), "hologram-surface-copy.png"));
        } catch (Exception error) {
            state.put("error", error.toString());
            if (!completion.cancel(false)) {
                PixelCopy.Result ready = completion.getNow(null);
                if (ready != null && ready.getStatus() == PixelCopy.SUCCESS && !ready.getBitmap().isRecycled())
                    ready.getBitmap().recycle();
            }
        }
        return state;
    }

    private <T> T onMain(Callable<T> operation) throws Exception {
        return onMain(operation, MAIN_TIMEOUT_MS);
    }

    private <T> T onMain(Callable<T> operation, long timeoutMillis) throws Exception {
        long queued = SystemClock.elapsedRealtimeNanos();
        AtomicBoolean expired = new AtomicBoolean(false);
        AtomicReference<Bitmap> pendingBitmap = new AtomicReference<>();
        FutureTask<T> task = new FutureTask<>(() -> {
            T value = operation.call();
            if (value instanceof Bitmap) {
                pendingBitmap.set((Bitmap) value);
                if (expired.get()) {
                    Bitmap abandoned = pendingBitmap.getAndSet(null);
                    if (abandoned != null) abandoned.recycle();
                }
            }
            return value;
        });
        require(main.post(task), "Mainthread unavailable");
        try {
            T value = task.get(timeoutMillis, TimeUnit.MILLISECONDS);
            pendingBitmap.set(null);
            return value;
        }
        finally {
            expired.set(true);
            task.cancel(false);
            Bitmap abandoned = pendingBitmap.getAndSet(null);
            if (abandoned != null) abandoned.recycle();
            Bundle timing = new Bundle();
            timing.putLong("main_wait_elapsed_ms", (SystemClock.elapsedRealtimeNanos() - queued) / 1000000L);
            instrumentation.sendStatus(1, timing);
        }
    }

    private void phase(String name) throws Exception {
        report.put("phase", name);
        persist();
        Bundle value = new Bundle();
        value.putString("phase", name);
        value.putLong("elapsed_ms", (SystemClock.elapsedRealtimeNanos() - started) / 1000000L);
        instrumentation.sendStatus(1, value);
    }

    private void persist() throws Exception {
        if (directory == null) return;
        byte[] bytes = report.toString(2).getBytes(StandardCharsets.UTF_8);
        File pending = new File(directory, "hologram-state.json.tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(pending)) {
                output.write(bytes);
                output.getFD().sync();
            }
            Files.move(pending.toPath(), new File(directory, "hologram-state.json").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending.toPath());
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        require(owner != null && owner.getClass().getName().startsWith("at.heinz.deviceai."), "App-owned reflection only");
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object invoke(Object owner, String name) throws Exception {
        require(owner != null && owner.getClass().getName().startsWith("at.heinz.deviceai."), "App-owned reflection only");
        Method method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (containsText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }

    private static Button findButton(View view, String text) {
        if (view instanceof Button && text.equalsIgnoreCase(((Button) view).getText().toString())) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = findButton(((ViewGroup) view).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static String sha256(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder value = new StringBuilder();
        for (byte item : digest) value.append(String.format(Locale.ROOT, "%02x", item & 255));
        return value.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
