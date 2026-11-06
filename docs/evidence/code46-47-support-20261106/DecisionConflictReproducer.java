import at.heinz.deviceai.cognitive.LocalDecisionPromptDigest;
import at.heinz.deviceai.cognitive.WorkDecisionRecall;
import java.util.List;

class DecisionConflictReproducer {
    private static final String QUESTION = "Wie geht Code47 weiter?";
    private static final String OLD = "Wir haben entschieden: Code47 wird am Montag veröffentlicht.";

    public static void main(String[] args) {
        int failures = 0;
        failures += check("confidential-active-conflict",
            "Wir haben entschieden: Code47 wird am Freitag veröffentlicht; das Passwort bleibt intern.", false);
        failures += check("overlong-active-conflict",
            "Wir haben entschieden: Code47 wird am Freitag veröffentlicht, " +
                "weil die Prüfung noch läuft. ".repeat(8), true);
        System.out.println("CASES=2 FAILED=" + failures);
        if (failures != 0) System.exit(1);
    }

    private static int check(String name, String newer, boolean overlong) {
        List<WorkDecisionRecall.Item> items = WorkDecisionRecall.INSTANCE.items(List.of(), List.of(
            new WorkDecisionRecall.OwnerStatement(OLD, 5L, null),
            new WorkDecisionRecall.OwnerStatement(newer, 6L, null)
        ), 10L);
        if (items.size() != 2) throw new AssertionError("Both real decisions must reach selector");
        if (overlong && items.get(0).getText().length() <= 180)
            throw new AssertionError("Newer decision must exceed the entry limit");
        String control = LocalDecisionPromptDigest.INSTANCE.render(QUESTION,
            items.stream().filter(item -> item.getAt() == 5L).toList());
        if (!control.contains("Montag")) throw new AssertionError("Older single-decision control failed");
        String result = LocalDecisionPromptDigest.INSTANCE.render(QUESTION, items);
        boolean passed = result.isEmpty();
        System.out.println(name + ": " + (passed ? "PASS" : "FAIL") +
            " | actual_empty=" + passed + " | stale_monday=" + result.contains("Montag"));
        if (!passed) System.out.println(result);
        return passed ? 0 : 1;
    }
}
