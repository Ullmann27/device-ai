package at.heinz.deviceai;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Base64;
import android.util.SparseArray;
import android.util.Xml;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures windows from test process. */
public final class ContinuumWindowSnapshotInstrumentation extends Instrumentation {
    private static final String TEST_PACKAGE = "at.heinz.deviceai.mentorpaired.test";
    private static final String APP_PACKAGE = "at.heinz.deviceai.mentorpaired";
    private static final long TIMEOUT_MS = 45000;
    private static final int PREFETCH = AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST
            | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE;
    private final AtomicBoolean completed = new AtomicBoolean();
    private volatile int nodes;
    private volatile String phase = "setup";
    private volatile String operation = "setup";
    private long started;
    private long deadline;
    private Thread captureThread;
    private Thread watchdog;
    private boolean awaitAvatarDiagnostic;
    private String awaitText;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        awaitAvatarDiagnostic = arguments != null && "true".equals(arguments.getString("await_avatar_diagnostic"));
        String encoded = arguments == null ? null : arguments.getString("await_text_base64");
        awaitText = encoded == null ? null : new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        started = SystemClock.uptimeMillis();
        deadline = started + TIMEOUT_MS;
        captureThread = Thread.currentThread();
        try {
            require(TEST_PACKAGE.equals(getContext().getPackageName()), "Wrong test package");
            require(TEST_PACKAGE.equals(getTargetContext().getPackageName()), "Self target required");
            require(Build.VERSION.SDK_INT == 35, "API 35 required");
            require(java.util.Arrays.asList(Build.SUPPORTED_ABIS).contains("x86_64"), "x86_64 required");
            require("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE), "Emulator required");
            require(awaitText == null || (!awaitText.isEmpty() && awaitText.length() <= 4000), "Invalid expected text");
            startDeadlineWatcher();
            setPhase("connect");
            UiAutomation automation = getUiAutomation();
            checkDeadline();
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            int sample = 0;
            while (SystemClock.uptimeMillis() < deadline) {
                setPhase("windows");
                SparseArray<List<AccessibilityWindowInfo>> displays = automation.getWindowsOnAllDisplays();
                AccessibilityNodeInfo root = null;
                try {
                    checkDeadline();
                    JSONArray state = new JSONArray();
                    AccessibilityWindowInfo focused = null;
                    int focusedCount = 0;
                    for (int d = 0; d < displays.size(); d++) {
                        int display = displays.keyAt(d);
                        for (AccessibilityWindowInfo window : displays.valueAt(d)) {
                            Rect bounds = new Rect();
                            window.getBoundsInScreen(bounds);
                            state.put(new JSONObject().put("display", display).put("id", window.getId())
                                    .put("type", window.getType()).put("focused", window.isFocused())
                                    .put("active", window.isActive()).put("title", text(window.getTitle()))
                                    .put("bounds", bounds.toShortString()).put("layer", window.getLayer()));
                            if (display == Display.DEFAULT_DISPLAY && window.isFocused()
                                    && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                                focused = window;
                                focusedCount++;
                            }
                        }
                    }
                    Bundle observation = new Bundle();
                    observation.putInt("sample", ++sample);
                    observation.putLong("elapsed_ms", SystemClock.uptimeMillis() - started);
                    observation.putString("windows", state.toString());
                    if (focusedCount == 1) {
                        setPhase("root");
                        root = focused.getRoot(PREFETCH);
                        checkDeadline();
                    }
                    observation.putBoolean("root_present", root != null);
                    sendStatus(1, observation);
                    if (rootReady(root)) {
                        setPhase("capture_setup");
                        long captureStarted = SystemClock.uptimeMillis();
                        Display display = getContext().getSystemService(DisplayManager.class)
                                .getDisplay(Display.DEFAULT_DISPLAY);
                        require(display != null, "Default display missing");
                        int rotation = display.getRotation();
                        StringWriter writer = new StringWriter();
                        XmlSerializer xml = Xml.newSerializer();
                        xml.setOutput(writer);
                        xml.startDocument("UTF-8", true);
                        xml.startTag("", "hierarchy");
                        xml.attribute("", "rotation", Integer.toString(rotation));
                        nodes = 0;
                        setPhase("hierarchy");
                        writeNode(xml, root, 0);
                        xml.endTag("", "hierarchy");
                        xml.endDocument();
                        byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
                        require(bytes.length <= 262144, "Hierarchy exceeds evidence limit");
                        long hierarchyFinished = SystemClock.uptimeMillis();
                        setPhase("screenshot");
                        Bitmap screenshot = automation.takeScreenshot();
                        require(screenshot != null, "Screenshot unavailable");
                        long screenshotFinished = SystemClock.uptimeMillis();
                        File screenshotFile = new File(getTargetContext().getCacheDir(), "continuum-window-snapshot.png");
                        try {
                            setPhase("image_write");
                            result.putInt("screenshot_width", screenshot.getWidth());
                            result.putInt("screenshot_height", screenshot.getHeight());
                            try (FileOutputStream output = new FileOutputStream(screenshotFile)) {
                                require(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output), "Screenshot write failed");
                                checkDeadline();
                            }
                        } finally { screenshot.recycle(); }
                        setPhase("windows_after");
                        SparseArray<List<AccessibilityWindowInfo>> after = automation.getWindowsOnAllDisplays();
                        try {
                            checkDeadline();
                            int matches = 0;
                            int focusedAfter = -1;
                            JSONArray stateAfter = new JSONArray();
                            for (int d = 0; d < after.size(); d++) {
                                for (AccessibilityWindowInfo window : after.valueAt(d)) {
                                    stateAfter.put(new JSONObject().put("display", after.keyAt(d))
                                            .put("id", window.getId()).put("type", window.getType())
                                            .put("focused", window.isFocused()).put("active", window.isActive()));
                                    if (after.keyAt(d) == Display.DEFAULT_DISPLAY && window.isFocused()
                                            && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                                        matches++;
                                        focusedAfter = window.getId();
                                    }
                                }
                            }
                            result.putString("windows_after", stateAfter.toString());
                            require(matches == 1 && focusedAfter == focused.getId(), "Focus changed during capture");
                            require(display.getRotation() == rotation, "Rotation changed during capture");
                        } finally {
                            for (int d = 0; d < after.size(); d++) {
                                for (AccessibilityWindowInfo window : after.valueAt(d)) window.recycle();
                            }
                        }
                        setPhase("hash");
                        byte[] digest = MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(screenshotFile.toPath()));
                        StringBuilder hash = new StringBuilder();
                        for (byte value : digest) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                        result.putString("screenshot_sha", hash.toString());
                        result.putLong("capture_started_ms", captureStarted);
                        result.putLong("hierarchy_finished_ms", hierarchyFinished);
                        result.putLong("screenshot_finished_ms", screenshotFinished);
                        result.putLong("capture_finished_ms", SystemClock.uptimeMillis());
                        result.putInt("rotation", rotation);
                        result.putString("hierarchy_base64", Base64.encodeToString(bytes, Base64.NO_WRAP));
                        result.putString("windows", state.toString());
                        result.putInt("window_id", focused.getId());
                        result.putInt("node_count", nodes);
                        result.putInt("helper_pid", Process.myPid());
                        result.putString("helper_package", getTargetContext().getPackageName());
                        result.putString("status", "ok");
                        checkDeadline();
                        code = Activity.RESULT_OK;
                        break;
                    }
                } finally {
                    if (root != null) root.recycle();
                    for (int d = 0; d < displays.size(); d++) {
                        for (AccessibilityWindowInfo window : displays.valueAt(d)) window.recycle();
                    }
                }
                SystemClock.sleep(200);
            }
            require(code == Activity.RESULT_OK, "Requested QA hierarchy unavailable before deadline");
        } catch (Throwable failure) {
            result.putString("status", "failed");
            result.putString("error", failure.toString());
            result.putString("capture_stack_base64", stack(failure.getStackTrace()));
            code = Activity.RESULT_CANCELED;
        }
        complete(code, result);
    }

    private void startDeadlineWatcher() {
        watchdog = new Thread(() -> {
            try {
                long remaining;
                while ((remaining = deadline - SystemClock.uptimeMillis()) > 0) Thread.sleep(remaining);
            } catch (InterruptedException ignored) { return; }
            if (completed.get()) return;
            Bundle result = progress();
            result.putString("status", "failed");
            result.putString("error", "Capture deadline exceeded");
            result.putString("capture_stack_base64", stack(captureThread.getStackTrace()));
            complete(Activity.RESULT_CANCELED, result);
        }, "continuum-snapshot-deadline");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void complete(int code, Bundle result) {
        long elapsed = SystemClock.uptimeMillis() - started;
        if (code == Activity.RESULT_OK && elapsed >= TIMEOUT_MS) {
            code = Activity.RESULT_CANCELED;
            result.putString("status", "failed");
            result.putString("error", "Capture completed after deadline");
        }
        if (!completed.compareAndSet(false, true)) return;
        if (watchdog != null && watchdog != Thread.currentThread()) watchdog.interrupt();
        result.putString("phase", phase);
        result.putString("operation", operation);
        result.putInt("observed_nodes", nodes);
        result.putLong("elapsed_ms", elapsed);
        if (code != Activity.RESULT_OK) sendStatus(3, result);
        finish(code, result);
    }

    private Bundle progress() {
        Bundle state = new Bundle();
        state.putString("phase", phase);
        state.putString("operation", operation);
        state.putInt("observed_nodes", nodes);
        state.putLong("elapsed_ms", SystemClock.uptimeMillis() - started);
        return state;
    }

    private void setPhase(String next) {
        checkDeadline();
        phase = next;
        operation = next;
        sendStatus(2, progress());
        checkDeadline();
    }

    private void checkDeadline() {
        require(!completed.get() && SystemClock.uptimeMillis() < deadline,
                "Capture deadline exceeded at " + phase + "/" + operation);
    }

    private boolean rootReady(AccessibilityNodeInfo root) {
        if (root == null || !APP_PACKAGE.equals(text(root.getPackageName()))) return false;
        setPhase("refresh");
        boolean current = root.refresh();
        checkDeadline();
        if (!current) return false;
        if (awaitAvatarDiagnostic) {
            setPhase("avatar_title");
            boolean ready = hasAvatarDiagnosticTitle(root);
            checkDeadline();
            if (!ready) return false;
        }
        if (awaitText == null) return true;
        setPhase("expected_text");
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(awaitText);
        try {
            checkDeadline();
            for (AccessibilityNodeInfo match : matches) {
                if (match.isVisibleToUser() && APP_PACKAGE.equals(text(match.getPackageName()))
                        && awaitText.equals(text(match.getText()))) return true;
            }
            return false;
        } finally { for (AccessibilityNodeInfo match : matches) match.recycle(); }
    }

    private static String stack(StackTraceElement[] frames) {
        StringBuilder text = new StringBuilder();
        for (StackTraceElement frame : frames) text.append(frame).append('\n');
        return Base64.encodeToString(text.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private boolean hasAvatarDiagnosticTitle(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText("Hologramm");
        try {
            for (AccessibilityNodeInfo match : matches) {
                String title = text(match.getText());
                if ("Continuum · Hologramm-Gerätediagnose".equals(title)
                        || "Hologramm · Diagnose".equals(title)) return true;
            }
            return false;
        } finally { for (AccessibilityNodeInfo match : matches) match.recycle(); }
    }

    private void writeNode(XmlSerializer xml, AccessibilityNodeInfo node, int index) throws Exception {
        checkDeadline();
        require(++nodes <= 4096, "Hierarchy node limit exceeded");
        operation = "node_fields";
        if (nodes % 16 == 0) sendStatus(2, progress());
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        xml.startTag("", "node");
        xml.attribute("", "index", Integer.toString(index));
        xml.attribute("", "text", text(node.getText()));
        xml.attribute("", "resource-id", text(node.getViewIdResourceName()));
        xml.attribute("", "class", text(node.getClassName()));
        xml.attribute("", "package", text(node.getPackageName()));
        xml.attribute("", "content-desc", text(node.getContentDescription()));
        xml.attribute("", "checkable", Boolean.toString(node.isCheckable()));
        xml.attribute("", "checked", Boolean.toString(node.isChecked()));
        xml.attribute("", "clickable", Boolean.toString(node.isClickable()));
        xml.attribute("", "enabled", Boolean.toString(node.isEnabled()));
        xml.attribute("", "focusable", Boolean.toString(node.isFocusable()));
        xml.attribute("", "focused", Boolean.toString(node.isFocused()));
        xml.attribute("", "scrollable", Boolean.toString(node.isScrollable()));
        xml.attribute("", "long-clickable", Boolean.toString(node.isLongClickable()));
        xml.attribute("", "password", Boolean.toString(node.isPassword()));
        xml.attribute("", "selected", Boolean.toString(node.isSelected()));
        xml.attribute("", "bounds", bounds.toShortString());
        for (int i = 0; i < node.getChildCount(); i++) {
            checkDeadline();
            operation = "child_" + nodes + "_" + i;
            AccessibilityNodeInfo child = node.getChild(i, PREFETCH);
            try {
                checkDeadline();
                if (child != null && child.isVisibleToUser()) writeNode(xml, child, i);
            } finally { if (child != null) child.recycle(); }
        }
        xml.endTag("", "node");
    }

    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
