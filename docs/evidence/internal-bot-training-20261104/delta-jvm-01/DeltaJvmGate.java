
import java.nio.file.*;
import java.util.*;
import org.junit.runner.*;
import org.junit.internal.TextListener;

public final class DeltaJvmGate {
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    private static Class<?> requireOrigin(String name, String expected, Map<String,String> origins) throws Exception {
        Class<?> cls = Class.forName(name, false, DeltaJvmGate.class.getClassLoader());
        if (cls.getProtectionDomain().getCodeSource() == null)
            throw new IllegalStateException("Missing CodeSource: " + name);
        Path actual = Paths.get(cls.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        origins.put(name, actual.toString());
        if (!actual.equals(Paths.get(expected).toRealPath()))
            throw new IllegalStateException("Unexpected CodeSource: " + name + " -> " + actual);
        return cls;
    }
    public static void main(String[] args) throws Exception {
        // report, new production directory, new test directory, baseline app jar,
        // then seven pairs of test-class name and required origin.
        if (args.length != 18) throw new IllegalArgumentException("Expected four paths and seven class/origin pairs");
        Map<String,String> origins = new LinkedHashMap<>();
        Result result = null;
        Throwable problem = null;
        boolean originsVerified = false;
        boolean junitStarted = false;
        try {
            requireOrigin("at.heinz.deviceai.chief.ChiefBotConversationCodec", args[1], origins);
            requireOrigin("at.heinz.deviceai.chief.ChiefBotConversation", args[1], origins);
            requireOrigin("at.heinz.deviceai.chief.ChiefBotProfileStore", args[3], origins);
            requireOrigin("at.heinz.deviceai.chief.ChiefBotPracticeConversationTest", args[2], origins);
            Class<?>[] classes = new Class<?>[7];
            for (int i = 0; i < classes.length; i++)
                classes[i] = requireOrigin(args[4 + i * 2], args[5 + i * 2], origins);
            originsVerified = true;
            JUnitCore core = new JUnitCore();
            core.addListener(new TextListener(System.out));
            junitStarted = true;
            result = core.run(classes);
        } catch (Throwable error) {
            problem = error;
            error.printStackTrace(System.out);
        }
        int tests = result == null ? 0 : result.getRunCount();
        int failures = result == null ? 0 : result.getFailureCount();
        int ignored = result == null ? 0 : result.getIgnoreCount();
        int assumptions = result == null ? 0 : result.getAssumptionFailureCount();
        boolean passed = problem == null && originsVerified && result != null &&
            result.wasSuccessful() && tests == 70 && ignored == 0 && assumptions == 0;
        StringBuilder json = new StringBuilder("{\"schema\":\"continuum-delta-junit-result\",\"version\":1");
        json.append(",\"codeSourcesVerified\":").append(originsVerified);
        json.append(",\"junitStarted\":").append(junitStarted);
        json.append(",\"junitCompleted\":").append(result != null);
        json.append(",\"tests\":").append(result == null ? "null" : Integer.toString(tests));
        json.append(",\"failures\":").append(result == null ? "null" : Integer.toString(failures));
        json.append(",\"ignored\":").append(result == null ? "null" : Integer.toString(ignored));
        json.append(",\"assumptionFailures\":").append(result == null ? "null" : Integer.toString(assumptions));
        json.append(",\"passed\":").append(passed).append(",\"codeSources\":{");
        boolean first = true;
        for (Map.Entry<String,String> entry : origins.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        json.append("},\"error\":").append(problem == null ? "null" : quote(problem.toString())).append("}\n");
        Files.writeString(Paths.get(args[0]), json.toString(), StandardOpenOption.CREATE_NEW);
        System.exit(passed ? 0 : 1);
    }
}
