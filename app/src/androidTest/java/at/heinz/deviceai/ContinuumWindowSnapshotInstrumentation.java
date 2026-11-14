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

/** Captures windows from test process. */
public final class ContinuumWindowSnapshotInstrumentation extends Instrumentation {
    private static final String TEST_PACKAGE = "at.heinz.deviceai.mentorpaired.test";
    private static final String APP_PACKAGE = "at.heinz.deviceai.mentorpaired";
    private static final long TIMEOUT_MS = 45000;
    private int nodes;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        long started = SystemClock.uptimeMillis();
        try {
            require(TEST_PACKAGE.equals(getContext().getPackageName()), "Wrong test package");
            require(TEST_PACKAGE.equals(getTargetContext().getPackageName()), "Self target required");
            require(Build.VERSION.SDK_INT == 35, "API 35 required");
            require(java.util.Arrays.asList(Build.SUPPORTED_ABIS).contains("x86_64"), "x86_64 required");
            require("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE), "Emulator required");
            UiAutomation automation = getUiAutomation();
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            long deadline = started + TIMEOUT_MS;
            int sample = 0;
            while (SystemClock.uptimeMillis() < deadline) {
                SparseArray<List<AccessibilityWindowInfo>> displays = automation.getWindowsOnAllDisplays();
                AccessibilityNodeInfo root = null;
                try {
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
                    if (focusedCount == 1) root = focused.getRoot();
                    observation.putBoolean("root_present", root != null);
                    sendStatus(1, observation);
                    if (root != null && APP_PACKAGE.equals(text(root.getPackageName())) && root.refresh()) {
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
                        writeNode(xml, root, 0);
                        xml.endTag("", "hierarchy");
                        xml.endDocument();
                        byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
                        require(bytes.length <= 262144, "Hierarchy exceeds evidence limit");
                        long hierarchyFinished = SystemClock.uptimeMillis();
                        Bitmap screenshot = automation.takeScreenshot();
                        require(screenshot != null, "Screenshot unavailable");
                        long screenshotFinished = SystemClock.uptimeMillis();
                        File screenshotFile = new File(getTargetContext().getCacheDir(), "continuum-window-snapshot.png");
                        try {
                            result.putInt("screenshot_width", screenshot.getWidth());
                            result.putInt("screenshot_height", screenshot.getHeight());
                            try (FileOutputStream output = new FileOutputStream(screenshotFile)) {
                                require(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output), "Screenshot write failed");
                            }
                        } finally { screenshot.recycle(); }
                        SparseArray<List<AccessibilityWindowInfo>> after = automation.getWindowsOnAllDisplays();
                        try {
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
            require(code == Activity.RESULT_OK, "Focused QA hierarchy unavailable before deadline");
        } catch (Throwable failure) {
            result.putString("status", "failed");
            result.putString("error", failure.toString());
            code = Activity.RESULT_CANCELED;
        }
        result.putLong("elapsed_ms", SystemClock.uptimeMillis() - started);
        finish(code, result);
    }

    private void writeNode(XmlSerializer xml, AccessibilityNodeInfo node, int index) throws Exception {
        require(++nodes <= 4096, "Hierarchy node limit exceeded");
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
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (child.isVisibleToUser()) writeNode(xml, child, i); }
            finally { child.recycle(); }
        }
        xml.endTag("", "node");
    }

    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
