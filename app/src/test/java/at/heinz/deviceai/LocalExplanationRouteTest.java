package at.heinz.deviceai;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Tests the real routing policy, without a model, network or device-tool fake. */
public class LocalExplanationRouteTest {
    @Test public void explicitExplanationUsesReadyModelWithRetrievedEvidence() {
        for (String request : new String[] {
            "Erkläre Quantencomputer anhand der vorhandenen Quellen.",
            "Erklär mir diesen Zusammenhang.",
            "Kannst du diesen Zusammenhang erklären?",
            "Erläutere den Unterschied zwischen beiden Quellen.",
            "Bitte diesen Zusammenhang erläutern."
        }) {
            assertEquals(request, AssistantRoute.LOCAL_MODEL,
                LocalFirstPolicy.INSTANCE.afterMemory(true, true, request));
        }
    }

    @Test public void missingModelKeepsRetrievedEvidenceAvailable() {
        assertEquals(AssistantRoute.LOCAL_CORE,
            LocalFirstPolicy.INSTANCE.afterMemory(true, false, "Erkläre Quantencomputer."));
    }

    @Test public void explanationWordsInsideDirectRecallDoNotRequestSynthesis() {
        for (String request : new String[] {
            "Was bedeutet das Wort Erkläre?",
            "Was habe ich über erklären gespeichert?",
            "Was weißt du über erläutern aus meiner Erinnerung?",
            "Was bedeutet „erkläre“?",
            "Wo wohnt Nela?"
        }) {
            assertEquals(request, AssistantRoute.LOCAL_CORE,
                LocalFirstPolicy.INSTANCE.afterMemory(true, true, request));
        }
    }
}
