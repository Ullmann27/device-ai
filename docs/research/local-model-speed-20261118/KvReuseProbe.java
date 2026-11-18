import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.BenchmarkInfo;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ExperimentalFlags;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.ThinkingConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HOST probe (x86_64 Linux, litertlm-jvm 0.18.0, CPU). Not a device measurement.
 * Question: does a reused Conversation prefill only the new turn, while a fresh Conversation per turn
 * (the pattern LiteRtLmLocalModelRuntime uses today) prefills system text + whole history every turn?
 */
public final class KvReuseProbe {
    private static final int THREADS = 2;
    private static final int CONTEXT = 4096;
    private static final int MAX_OUTPUT = 24;

    public static void main(String[] args) throws Exception {
        String modelPath = args[0];
        String cacheDir = args[1];
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);

        StringBuilder system = new StringBuilder("Du bist ein hilfreicher Assistent auf einem Telefon. Antworte kurz auf Deutsch.\n");
        for (int i = 1; i <= 40; i++) {
            system.append("Regel ").append(i).append(": Beantworte nur, was aus dem Gespraech hervorgeht; ")
                  .append("erfinde keine Geraeteaktionen und nenne Unsicherheit ausdruecklich.\n");
        }
        String[] questions = {
            "Ich habe drei rote und vier blaue Kugeln. Wie viele Kugeln sind es zusammen?",
            "Nun lege ich zwei blaue Kugeln weg. Wie viele blaue bleiben?",
            "Und wie viele Kugeln habe ich jetzt insgesamt?"
        };

        // 1) Engine created WITHOUT the experimental benchmark flag (what the app does today).
        ExperimentalFlags.INSTANCE.setEnableBenchmark(false);
        try (Engine engine = engine(modelPath, cacheDir)) {
            engine.initialize();
            try (Conversation conversation = engine.createConversation(config(system.toString(), false))) {
                conversation.sendMessage(questions[0]);
                try {
                    BenchmarkInfo info = conversation.getBenchmarkInfo();
                    System.out.println("FLAG_OFF getBenchmarkInfo -> returned prefillTokens=" + info.getLastPrefillTokenCount());
                } catch (Throwable failure) {
                    System.out.println("FLAG_OFF getBenchmarkInfo -> THROWS " + failure.getClass().getSimpleName() + ": "
                        + String.valueOf(failure.getMessage()).replace('\n', ' '));
                }
                System.out.println("FLAG_OFF getTokenCount -> " + conversation.getTokenCount());
            }
        }

        // 2) Engine created WITH the benchmark flag: native prefill/decode counters are available.
        ExperimentalFlags.INSTANCE.setEnableBenchmark(true);
        try (Engine engine = engine(modelPath, cacheDir)) {
            engine.initialize();
            ExperimentalFlags.INSTANCE.setEnableBenchmark(false);

            // A) fresh Conversation per turn, whole dialogue in the prompt (today's app pattern).
            List<String> history = new ArrayList<>();
            for (int turn = 0; turn < questions.length; turn++) {
                StringBuilder prompt = new StringBuilder();
                if (!history.isEmpty()) {
                    prompt.append("Bisheriges Gespraech:\n");
                    for (String line : history) prompt.append(line).append('\n');
                    prompt.append('\n');
                }
                prompt.append("Nutzer: ").append(questions[turn]);
                long started = System.nanoTime();
                try (Conversation conversation = engine.createConversation(config(system.toString(), false))) {
                    Message reply = conversation.sendMessage(prompt.toString());
                    long wallMs = (System.nanoTime() - started) / 1_000_000L;
                    report("A_FRESH_PER_TURN", turn + 1, conversation, wallMs);
                    history.add("Nutzer: " + questions[turn]);
                    history.add("Assistent: " + text(reply));
                }
            }

            // B) one Conversation reused; only the new owner turn is sent.
            try (Conversation conversation = engine.createConversation(config(system.toString(), false))) {
                for (int turn = 0; turn < questions.length; turn++) {
                    long started = System.nanoTime();
                    conversation.sendMessage(questions[turn]);
                    long wallMs = (System.nanoTime() - started) / 1_000_000L;
                    report("B_REUSED_CONVERSATION", turn + 1, conversation, wallMs);
                }
            }

            // C) preface prefilled when the Conversation is created (prefillPrefaceOnInit = true), then first turn.
            long createStarted = System.nanoTime();
            try (Conversation conversation = engine.createConversation(config(system.toString(), true))) {
                long createMs = (System.nanoTime() - createStarted) / 1_000_000L;
                System.out.println("C_PREFACE_PREFILLED create wallMs=" + createMs
                    + " tokenCountAfterCreate=" + conversation.getTokenCount());
                long started = System.nanoTime();
                conversation.sendMessage(questions[0]);
                long wallMs = (System.nanoTime() - started) / 1_000_000L;
                report("C_PREFACE_PREFILLED", 1, conversation, wallMs);
            }
        }
    }

    private static Engine engine(String modelPath, String cacheDir) {
        return new Engine(new EngineConfig(modelPath, new Backend.CPU(THREADS, null), null, null, CONTEXT, null, cacheDir));
    }

    private static ConversationConfig config(String system, boolean prefillPreface) {
        return new ConversationConfig(Contents.Companion.of(system), Collections.emptyList(), Collections.emptyList(),
            null, false, null, Collections.emptyMap(), null, prefillPreface, MAX_OUTPUT, new ThinkingConfig(false, 0));
    }

    private static String text(Message message) {
        String value = message.toString().replace('\n', ' ').trim();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private static void report(String scenario, int turn, Conversation conversation, long wallMs) {
        BenchmarkInfo info = conversation.getBenchmarkInfo();
        System.out.printf(java.util.Locale.ROOT,
            "%s turn=%d prefillTokens=%d decodeTokens=%d prefillTokPerSec=%.1f decodeTokPerSec=%.1f ttftSec=%.2f wallMs=%d tokenCount=%d%n",
            scenario, turn, info.getLastPrefillTokenCount(), info.getLastDecodeTokenCount(),
            info.getLastPrefillTokensPerSecond(), info.getLastDecodeTokensPerSecond(),
            info.getTimeToFirstTokenInSecond(), wallMs, conversation.getTokenCount());
    }
}
