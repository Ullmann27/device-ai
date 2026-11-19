import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.BenchmarkInfo;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ExperimentalFlags;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.OpenApiTool;
import com.google.ai.edge.litertlm.ToolCall;
import com.google.ai.edge.litertlm.ToolKt;
import com.google.ai.edge.litertlm.ToolProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HOST probe (x86_64 Linux, litertlm-jvm 0.18.0, CPU, 2 threads) with the published gemma-4-E2B-it.litertlm.
 * Not a device measurement and not a quality evaluation: a handful of prompts that show
 *  (1) whether speculative decoding (MTP) can be switched on through the 0.18.0 Kotlin/JVM API for this file and what
 *      it does to the decode rate on this host,
 *  (2) whether the model answers in German and emits a structured tool call when a tool is registered.
 */
public final class Gemma4E2bProbe {
    private static final int THREADS = 2;
    private static final int CONTEXT = 4096;
    private static final int MAX_OUTPUT = 96;

    private static final String SYSTEM = "Du bist ein hilfreicher Assistent auf einem Telefon. Antworte auf Deutsch.";
    private static final String[] PROMPTS = {
        "Erkläre in drei Sätzen, warum der Himmel blau ist.",
        "Formuliere höflicher: Schick mir endlich die Rechnung, ich warte schon seit zwei Wochen darauf.",
        "Fasse in zwei Sätzen zusammen: Die Stadtbibliothek verlängert ab Montag ihre Öffnungszeiten. Sie ist dann "
            + "werktags von 8 bis 20 Uhr und samstags von 9 bis 14 Uhr geöffnet. Grund ist die gestiegene Nachfrage "
            + "nach Lernplätzen. Die Ausleihe von Medien ist weiterhin kostenlos."
    };

    public static void main(String[] args) throws Exception {
        String modelPath = args[0];
        String cacheDir = args[1];
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);

        // Optional third argument "on-first" reverses the order to expose warm-up and order effects.
        boolean onFirst = args.length > 2 && args[2].equals("on-first");
        System.out.println("ORDER " + (onFirst ? "SPECULATIVE_ON first" : "SPECULATIVE_OFF first"));
        List<String> first = run(modelPath, cacheDir, onFirst);
        List<String> second = run(modelPath, cacheDir, !onFirst);
        List<String> plain = onFirst ? second : first;
        List<String> speculative = onFirst ? first : second;
        for (int i = 0; i < PROMPTS.length; i++) {
            System.out.println("SAME_TEXT prompt=" + (i + 1) + " -> " + plain.get(i).equals(speculative.get(i)));
        }
        toolCall(modelPath, cacheDir);
    }

    private static List<String> run(String modelPath, String cacheDir, boolean speculativeDecoding) throws Exception {
        String label = speculativeDecoding ? "SPECULATIVE_ON" : "SPECULATIVE_OFF";
        List<String> answers = new ArrayList<>();
        ExperimentalFlags.INSTANCE.setEnableBenchmark(true);
        ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(speculativeDecoding);
        long initStarted = System.nanoTime();
        try (Engine engine = new Engine(new EngineConfig(modelPath, new Backend.CPU(THREADS, null), null, null,
                CONTEXT, null, cacheDir))) {
            try {
                engine.initialize();
            } finally {
                ExperimentalFlags.INSTANCE.setEnableBenchmark(false);
                ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(null);
            }
            System.out.println(label + " engineInitMs=" + (System.nanoTime() - initStarted) / 1_000_000L);
            for (int i = 0; i < PROMPTS.length; i++) {
                ConversationConfig config = new ConversationConfig(Contents.Companion.of(SYSTEM), Collections.emptyList(),
                    Collections.emptyList(), null, false, null, Collections.emptyMap(), null, false, MAX_OUTPUT);
                long started = System.nanoTime();
                try (Conversation conversation = engine.createConversation(config)) {
                    Message reply = conversation.sendMessage(PROMPTS[i]);
                    long wallMs = (System.nanoTime() - started) / 1_000_000L;
                    BenchmarkInfo info = conversation.getBenchmarkInfo();
                    String text = text(reply);
                    answers.add(text);
                    System.out.printf(java.util.Locale.ROOT,
                        "%s prompt=%d prefillTokens=%d decodeTokens=%d prefillTokPerSec=%.1f decodeTokPerSec=%.2f wallMs=%d%n",
                        label, i + 1, info.getLastPrefillTokenCount(), info.getLastDecodeTokenCount(),
                        info.getLastPrefillTokensPerSecond(), info.getLastDecodeTokensPerSecond(), wallMs);
                    System.out.println(label + " prompt=" + (i + 1) + " answer=" + shorten(text, 400));
                }
            }
        }
        return answers;
    }

    private static void toolCall(String modelPath, String cacheDir) throws Exception {
        OpenApiTool weather = new OpenApiTool() {
            @Override public String getToolDescriptionJsonString() {
                return "{\"name\":\"get_weather\",\"description\":\"Liefert das aktuelle Wetter für eine Stadt.\","
                    + "\"parameters\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},"
                    + "\"required\":[\"city\"]}}";
            }
            @Override public String execute(String paramsJsonString) {
                return "{\"error\":\"not executed in this probe\"}";
            }
        };
        List<ToolProvider> tools = Collections.singletonList(ToolKt.tool(weather));
        try (Engine engine = new Engine(new EngineConfig(modelPath, new Backend.CPU(THREADS, null), null, null,
                CONTEXT, null, cacheDir))) {
            engine.initialize();
            String[] questions = {
                "Wie ist das Wetter in Wien?",
                "Was ist die Hauptstadt von Österreich?"
            };
            for (String question : questions) {
                ConversationConfig config = new ConversationConfig(Contents.Companion.of(SYSTEM), Collections.emptyList(),
                    tools, null, false, null, Collections.emptyMap(), null, false, MAX_OUTPUT);
                try (Conversation conversation = engine.createConversation(config)) {
                    Message reply = conversation.sendMessage(question);
                    StringBuilder calls = new StringBuilder();
                    for (ToolCall call : reply.getToolCalls()) {
                        calls.append(call.getName()).append(call.getArguments()).append(' ');
                    }
                    System.out.println("TOOL_PROBE question=\"" + question + "\" toolCalls=" + reply.getToolCalls().size()
                        + " [" + calls.toString().trim() + "] text=" + shorten(text(reply), 200));
                }
            }
        }
    }

    private static String text(Message message) {
        StringBuilder text = new StringBuilder();
        for (Content content : message.getContents().getContents()) {
            if (content instanceof Content.Text part) text.append(part.getText());
        }
        return text.toString().replace('\n', ' ').trim();
    }

    private static String shorten(String value, int max) {
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
