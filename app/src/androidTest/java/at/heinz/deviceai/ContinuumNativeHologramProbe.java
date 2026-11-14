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
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Observes the existing stage. */
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
                        && current.optBoolean("cockpit_ready") && current.optBoolean("native3dDrawn"));
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
                .put("texture_parent_is_hologram", texture != null && texture.getParent() == hologram)
                .put("surface_valid", surface != null && surface.isValid())
                .put("surface_identity", surface == null ? JSONObject.NULL : System.identityHashCode(surface))
                .put("renderer_present", nativeRenderer != null)
                .put("cockpit_ready", nativeRenderer != null && field(nativeRenderer, "cockpitStage") != null)
                .put("callbacks", invoke(motion, "getCallbackCount")).put("draws", invoke(motion, "getDrawCount"))
                .put("phase", invoke(motion, "getPhase")).put("reason", invoke(motion, "getReason"))
                .put("trace", diagnose.invoke(trace, 60)).put("trace_frame", invoke(trace, "frameNumber"))
                .put("rotation", activity.getDisplay().getRotation());
        if (nativeRenderer != null) {
            state.put("viewport_width", field(nativeRenderer, "viewportWidth"))
                    .put("viewport_height", field(nativeRenderer, "viewportHeight"));
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
        JSONArray parents = new JSONArray();
        ViewParent parent = hologram.getParent();
        while (parent instanceof View && parents.length() < 12) {
            parents.put(viewState((View) parent));
            parent = parent.getParent();
        }
        state.put("ancestors", parents);
        return state;
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
            T value = task.get(MAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
        try (FileOutputStream output = new FileOutputStream(new File(directory, "hologram-state.json"))) {
            output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
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
