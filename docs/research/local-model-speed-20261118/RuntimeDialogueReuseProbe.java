import at.heinz.deviceai.evaluation.DialogueLatencyProbe;
import at.heinz.deviceai.evaluation.ModelEvalDeviceSample;
import at.heinz.deviceai.localmodel.ImportedLocalModel;
import at.heinz.deviceai.localmodel.LiteRtLmLocalModelRuntime;
import at.heinz.deviceai.localmodel.LocalDialogueReceipt;
import at.heinz.deviceai.localmodel.LocalDialogueRequest;
import at.heinz.deviceai.localmodel.LocalInferenceCallback;
import at.heinz.deviceai.localmodel.LocalInferenceEvent;
import at.heinz.deviceai.localmodel.LocalInferenceMetrics;
import at.heinz.deviceai.localmodel.LocalModelCatalog;
import at.heinz.deviceai.localmodel.LocalModelState;
import at.heinz.deviceai.localmodel.NativeLiteRtModelInspector;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HOST probe (x86_64 Linux, litertlm-jvm 0.18.0, CPU, 2 threads, window 4096). Not a device measurement.
 *
 * Runs the app's own classes – LiteRtLmLocalModelRuntime.streamDialogue / streamTextOnly / releaseDialogue / cancel
 * and the DialogueLatencyProbe runner – against the desktop build of the same library version:
 *  1. the dialogue latency probe exactly as the model-centre button runs it (six turns without, six with keeping);
 *  2. the keep/close rules of the runtime: a cancelled turn, another generation entry and an explicit release each
 *     end the kept conversation, and a request with the old key is then answered from its complete prompt.
 * Arguments: model file, SHA-256 of that file, work directory (outside the repository; delete it afterwards),
 * optional catalog id (decides the app's output budget, e.g. "gemma4-e2b-it"; default "qwen3-0.6b"),
 * optional clock-line suffix (see part 1), optional "benchmark": only part 2, with LiteRT-LM's experimental
 * benchmark flag set before the engine is created so that the runtime reports the tokens it read per turn
 * (the app never sets that flag; with it the app's token-based output-limit check becomes active).
 */
public final class RuntimeDialogueReuseProbe {
    private static LiteRtLmLocalModelRuntime runtime;

    private static final class Turn {
        String text = "";
        String terminal = "NONE";
        String failure;
        Long firstTextMs;
        long totalMs;
        Integer inputTokens;
        Integer outputTokens;

        String numbers() {
            return "readTokens=" + inputTokens + " writtenTokens=" + outputTokens + " firstTextMs=" + firstTextMs +
                " totalMs=" + totalMs + " chars=" + text.length();
        }
    }

    private interface Start { void run(LocalInferenceCallback callback); }

    /** One blocking generation; [cancelAfterFirstText] issues the caller's STOP as soon as text arrives. */
    private static Turn generate(Start start, boolean cancelAfterFirstText) throws Exception {
        Turn turn = new Turn();
        StringBuilder text = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        long started = System.nanoTime();
        start.run(event -> {
            if (event instanceof LocalInferenceEvent.TextDelta) {
                text.append(((LocalInferenceEvent.TextDelta) event).getText());
                if (cancelAfterFirstText && cancelled.compareAndSet(false, true)) runtime.cancel();
            } else if (event instanceof LocalInferenceEvent.Completed) {
                LocalInferenceMetrics metrics = ((LocalInferenceEvent.Completed) event).getMetrics();
                turn.terminal = "COMPLETED";
                turn.firstTextMs = metrics.getFirstTokenLatencyMillis();
                turn.totalMs = metrics.getTotalLatencyMillis();
                turn.inputTokens = metrics.getInputTokens();
                turn.outputTokens = metrics.getOutputTokens();
                done.countDown();
            } else if (event instanceof LocalInferenceEvent.Failed) {
                turn.terminal = "FAILED";
                turn.failure = ((LocalInferenceEvent.Failed) event).getMessage();
                done.countDown();
            } else if (event instanceof LocalInferenceEvent.Cancelled) {
                turn.terminal = "CANCELLED";
                done.countDown();
            }
        });
        if (!done.await(20, TimeUnit.MINUTES)) throw new IllegalStateException("generation did not end");
        if (turn.totalMs == 0) turn.totalMs = (System.nanoTime() - started) / 1_000_000L;
        turn.text = text.toString();
        // The runtime reports READY shortly after the terminal event.
        for (int i = 0; i < 500 && runtime.getState() != LocalModelState.READY; i++) Thread.sleep(10);
        return turn;
    }

    private static DialogueLatencyProbe.Generation measured(Start start) {
        try {
            Turn turn = generate(start, false);
            return new DialogueLatencyProbe.Generation(turn.text, turn.terminal.equals("COMPLETED"), turn.failure,
                turn.firstTextMs, turn.totalMs);
        } catch (Exception failure) {
            return new DialogueLatencyProbe.Generation("", false, String.valueOf(failure), null, 0);
        }
    }

    private static String receipt(LocalDialogueRequest request) {
        LocalDialogueReceipt receipt = request.getReceipt();
        return "reused=" + receipt.getReused() + " reason=" + receipt.getReason() + " tokensBefore=" +
            receipt.getTokensBefore() + " retained=" + receipt.getRetained() + " tokensAfter=" + receipt.getTokensAfter();
    }

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "PASS " : "FAIL ") + name);
        if (!condition) System.exit(3);
    }

    public static void main(String[] args) throws Exception {
        File model = new File(args[0]);
        String sha256 = args[1];
        File work = new File(args[2]);
        boolean benchmark = args.length > 5 && args[5].equals("benchmark");
        if (benchmark) com.google.ai.edge.litertlm.ExperimentalFlags.INSTANCE.setEnableBenchmark(true);
        String id = args.length > 3 ? args[3] : "qwen3-0.6b";
        LocalModelCatalog catalog = new LocalModelCatalog(new File(work, "catalog"), new NativeLiteRtModelInspector(),
            4L * 1024 * 1024 * 1024);
        // "import" is a Java keyword; the catalog copies and verifies the file like the app's import does.
        try (InputStream input = new FileInputStream(model)) {
            LocalModelCatalog.class.getMethod("import", String.class, String.class, InputStream.class, String.class,
                Function1.class).invoke(catalog, id, "host", input, sha256, null);
        }
        runtime = new LiteRtLmLocalModelRuntime(catalog, new File(work, "cache"),
            "You are Device AI, an on-device assistant.", () -> 2, limit -> 4096, null);
        CountDownLatch loaded = new CountDownLatch(1);
        AtomicReference<Object> loadResult = new AtomicReference<>();
        Function1<kotlin.Result<ImportedLocalModel>, Unit> onLoaded = result -> {
            loadResult.set(result);
            loaded.countDown();
            return Unit.INSTANCE;
        };
        runtime.load(id, "host", onLoaded);
        loaded.await(10, TimeUnit.MINUTES);
        System.out.println("LOAD state=" + runtime.getState() + " backend=" + runtime.getActiveBackend() +
            " contextTokens=" + runtime.getContextTokens() + " threads=" + runtime.getComputeThreads() +
            " toolFreePromptBytes=" + runtime.getToolFreePromptUtf8Bytes());
        check("model loaded", runtime.getState() == LocalModelState.READY);
        String anchor = "Referenzzeit laut Geräteuhr: 2026-11-18 (Mittwoch) 12:00, Zeitzone Europe/Vienna, UTC+01:00.\n";

        // 1. The dialogue latency probe as the model-centre button runs it. Optional 5th argument: a suffix for the
        //    clock line of every prompt. Qwen3 writes hidden reasoning before its answer, so its time to the first
        //    visible text is mostly that reasoning; with its documented soft switch "/no_think" as suffix the time
        //    to first text is the time to read the prompt. The app itself never adds such a switch.
        String suffix = args.length > 4 ? args[4] : "";
        String probeAnchor = suffix.isEmpty() ? anchor : anchor.trim() + " " + suffix + "\n";
        System.out.println("PROBE clock suffix='" + suffix + "' benchmarkFlag=" + benchmark);
        Integer promptBytes = runtime.getToolFreePromptUtf8Bytes();
        if (!benchmark) {
        JSONObject report = new DialogueLatencyProbe.Runner(
            prompt -> measured(callback -> runtime.streamTextOnly(prompt, callback)),
            request -> measured(callback -> runtime.streamDialogue(request, callback)),
            () -> { runtime.releaseDialogue(); return Unit.INSTANCE; },
            () -> new ModelEvalDeviceSample(null, null, null,
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()),
            () -> false,
            () -> probeAnchor,
            promptBytes == null ? 6000 : promptBytes,
            (mode, turn, total) -> { System.out.println("PROBE " + mode + " turn " + turn + "/" + total); return Unit.INSTANCE; },
            () -> "host-probe-key"
        ).run(new JSONObject().put("host", true).put("model", model.getName()).put("modelSha256", sha256)
            .put("contextTokens", runtime.getContextTokens()).put("computeThreads", runtime.getComputeThreads()));
        System.out.println("REPORT " + report.toString());
        System.out.println(DialogueLatencyProbe.INSTANCE.summary(report));
        System.out.println("INFO probe complete=" + report.getBoolean("complete"));
        }

        // 2. Keep/close rules of the runtime.
        String fresh = DialogueLatencyProbe.INSTANCE.freshPrompt(java.util.Collections.emptyList(),
            "Nenne mir eine Farbe.", anchor, 6000);
        String followUp = DialogueLatencyProbe.INSTANCE.followUp("Nenne mir noch eine andere Farbe.", anchor);

        LocalDialogueRequest first = new LocalDialogueRequest("key-a", fresh, null, new LocalDialogueReceipt());
        Turn turn = generate(callback -> runtime.streamDialogue(first, callback), false);
        System.out.println("A1 " + turn.terminal + " " + turn.numbers() + " " + receipt(first));
        check("a completed dialogue turn is retained", turn.terminal.equals("COMPLETED") && first.getReceipt().getRetained());

        LocalDialogueRequest second = new LocalDialogueRequest("key-a", fresh, followUp, new LocalDialogueReceipt());
        turn = generate(callback -> runtime.streamDialogue(second, callback), false);
        System.out.println("A2 " + turn.terminal + " " + turn.numbers() + " " + receipt(second));
        check("the follow-up is appended to the kept conversation", second.getReceipt().getReused() &&
            second.getReceipt().getTokensBefore().equals(first.getReceipt().getTokensAfter()) &&
            second.getReceipt().getTokensAfter() > second.getReceipt().getTokensBefore());

        LocalDialogueRequest stopped = new LocalDialogueRequest("key-a", fresh, followUp, new LocalDialogueReceipt());
        turn = generate(callback -> runtime.streamDialogue(stopped, callback), true);
        System.out.println("A3 " + turn.terminal + " " + turn.numbers() + " " + receipt(stopped));
        check("a stopped turn was appended but is not retained", stopped.getReceipt().getReused() &&
            turn.terminal.equals("CANCELLED") && !stopped.getReceipt().getRetained());

        LocalDialogueRequest afterStop = new LocalDialogueRequest("key-a", fresh, followUp, new LocalDialogueReceipt());
        turn = generate(callback -> runtime.streamDialogue(afterStop, callback), false);
        System.out.println("A4 " + turn.terminal + " " + turn.numbers() + " " + receipt(afterStop));
        check("after a stop the same key is answered from the complete prompt", !afterStop.getReceipt().getReused() &&
            "RUNTIME_NO_KEPT_CONVERSATION".equals(afterStop.getReceipt().getReason()) &&
            turn.terminal.equals("COMPLETED") && afterStop.getReceipt().getRetained());

        LocalDialogueRequest otherKey = new LocalDialogueRequest("key-b", fresh, followUp, new LocalDialogueReceipt());
        turn = generate(callback -> runtime.streamDialogue(otherKey, callback), false);
        System.out.println("B1 " + turn.terminal + " " + turn.numbers() + " " + receipt(otherKey));
        check("another key never continues a kept conversation", !otherKey.getReceipt().getReused() &&
            turn.terminal.equals("COMPLETED"));

        turn = generate(callback -> runtime.streamTextOnly(fresh, callback), false);
        LocalDialogueRequest afterOther = new LocalDialogueRequest("key-b", fresh, followUp, new LocalDialogueReceipt());
        Turn next = generate(callback -> runtime.streamDialogue(afterOther, callback), false);
        System.out.println("B2 textOnly=" + turn.terminal + " " + turn.numbers() + " then " + next.terminal + " " + next.numbers() + " " + receipt(afterOther));
        check("another generation entry ends the kept conversation", !afterOther.getReceipt().getReused() &&
            next.terminal.equals("COMPLETED"));

        runtime.releaseDialogue();
        LocalDialogueRequest afterRelease = new LocalDialogueRequest("key-b", fresh, followUp, new LocalDialogueReceipt());
        turn = generate(callback -> runtime.streamDialogue(afterRelease, callback), false);
        System.out.println("B3 " + turn.terminal + " " + turn.numbers() + " " + receipt(afterRelease));
        check("an explicit release ends the kept conversation", !afterRelease.getReceipt().getReused() &&
            turn.terminal.equals("COMPLETED"));

        CountDownLatch unloaded = new CountDownLatch(1);
        Function1<kotlin.Result<Unit>, Unit> onUnloaded = result -> { unloaded.countDown(); return Unit.INSTANCE; };
        runtime.unload(onUnloaded);
        unloaded.await(2, TimeUnit.MINUTES);
        System.out.println("UNLOAD state=" + runtime.getState());
        check("unload releases the engine with a kept conversation", runtime.getState() == LocalModelState.EMPTY);
        System.exit(0);
    }
}
