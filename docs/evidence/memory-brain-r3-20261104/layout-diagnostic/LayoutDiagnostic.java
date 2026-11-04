import at.heinz.deviceai.ui.ContinuumHomeLayout;
import java.util.Locale;
import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

public final class LayoutDiagnostic {
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length == 1 && args[0].equals("junit")) {
            JUnitCore core = new JUnitCore();
            core.addListener(new TextListener(System.out));
            Result result = core.run(Request.method(
                Class.forName("at.heinz.deviceai.ui.ContinuumLandscapeCockpitLayoutTest"),
                "everythingFitsOneScreenWithUsableTargetsAndNoOverlap"));
            System.exit(result.wasSuccessful() ? 0 : 1);
        }
        float[][] windows = {{840f, 649f}, {832f, 700f}, {960f, 600f}, {1280f, 800f}, {1440f, 900f}};
        System.out.println("width\theight\tfont\tslot\tleft\ttop\tright\tbottom\tboxWidth\tboxHeight\tstrictTarget\theightHex\ttopHex\tbottomHex");
        for (float[] window : windows) for (float font : new float[]{1f, 1.3f}) {
            ContinuumHomeLayout.Layout layout = ContinuumHomeLayout.INSTANCE.wide(window[0], window[1], font);
            for (var entry : layout.getBoxes().entrySet()) {
                ContinuumHomeLayout.Box box = entry.getValue();
                boolean accepted = box.getWidth() >= 48f && box.getHeight() >= 48f;
                System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%s\t%.9f\t%.9f\t%.9f\t%.9f\t%.9f\t%.9f\t%s\t%s\t%s\t%s%n",
                    window[0], window[1], font, entry.getKey(), box.getLeft(), box.getTop(),
                    box.getRight(), box.getBottom(), box.getWidth(), box.getHeight(), accepted,
                    Float.toHexString(box.getHeight()), Float.toHexString(box.getTop()), Float.toHexString(box.getBottom()));
            }
        }
    }
}
