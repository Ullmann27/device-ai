import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RedactorExperiment {
    private static volatile int sink;
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("at.heinz.deviceai.cognitive.AiContextCompiler");
        Object compiler = type.getConstructor().newInstance();
        Method redact = type.getDeclaredMethod("redactSecrets", String.class);
        redact.setAccessible(true);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadCpuTimeSupported() || !bean.isThreadAllocatedMemorySupported())
            throw new IllegalStateException("Required counters unavailable");
        bean.setThreadCpuTimeEnabled(true);
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        Map<String, String> inputs = new LinkedHashMap<>();
        String plain = "Remember the synthetic observatory appointment on Monday. Never approve the proposed action.\n";
        inputs.put("plain_short", plain.repeat(3));
        inputs.put("plain_long", plain.repeat(128));
        inputs.put("legacy_assignments", "token=synthetic_R4_A secret=synthetic_R4_B api-key=synthetic_R4_C\n".repeat(32));
        inputs.put("quoted_explicit", "authorization=\"Bearer synthetic_R4_Q\"\nKEEP_THE_CORRECTION\n".repeat(32));
        for (var input : inputs.entrySet()) {
            for (int i=0; i<300; i++) sink ^= ((String)redact.invoke(compiler,input.getValue())).length();
            String expected = (String)redact.invoke(compiler,input.getValue());
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(expected.getBytes(StandardCharsets.UTF_8)));
            for (int sample=0; sample<5; sample++) {
                long cpu = bean.getCurrentThreadCpuTime();
                long allocation = bean.getThreadAllocatedBytes(thread);
                long wall = System.nanoTime();
                for (int i=0; i<200; i++) sink ^= ((String)redact.invoke(compiler,input.getValue())).length();
                wall = System.nanoTime() - wall;
                allocation = bean.getThreadAllocatedBytes(thread) - allocation;
                cpu = bean.getCurrentThreadCpuTime() - cpu;
                System.out.printf(java.util.Locale.ROOT,
                    "{\"case\":\"%s\",\"sample\":%d,\"iterations\":200,\"inputBytes\":%d,\"outputBytes\":%d,\"outputSha256\":\"%s\",\"threadCpuNanos\":%d,\"allocatedBytes\":%d,\"wallNanos\":%d}%n",
                    input.getKey(),sample,input.getValue().getBytes(StandardCharsets.UTF_8).length,
                    expected.getBytes(StandardCharsets.UTF_8).length,hash,cpu,allocation,wall);
            }
        }
    }
}
