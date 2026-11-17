import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import java.util.HashSet;
import java.util.Set;

/** Host evidence runner: preserve actually executed identities and reject skips. */
class ReadinessJUnit {
  public static void main(String[] args) throws Exception {
    JUnitCore core = new JUnitCore();
    Set<String> failed = new HashSet<>();
    int[] assumed = {0};
    core.addListener(new RunListener() {
      @Override public void testStarted(Description d) { System.out.println("START " + d); }
      @Override public void testFailure(Failure f) {
        failed.add(f.getDescription().toString());
        System.out.println("FAIL " + f.getDescription() + " " + f.getTrace());
      }
      @Override public void testAssumptionFailure(Failure f) {
        assumed[0]++; failed.add(f.getDescription().toString());
        System.out.println("ASSUMPTION_SKIP " + f.getDescription());
      }
      @Override public void testIgnored(Description d) { System.out.println("IGNORED " + d); }
      @Override public void testFinished(Description d) {
        System.out.println((failed.contains(d.toString()) ? "NOT_PASS " : "PASS ") + d);
      }
    });
    Result result = core.run(
      Class.forName("at.heinz.deviceai.cognitive.AvatarCameraPolicyTest"),
      Class.forName("at.heinz.deviceai.cognitive.ContinuumReadinessGuideTest"),
      Class.forName("at.heinz.deviceai.AndroidAvatarCameraInputUiTest"));
    System.out.println("RESULT total=" + result.getRunCount() + " failures=" + result.getFailureCount()
      + " ignored=" + result.getIgnoreCount() + " assumptionSkips=" + assumed[0]
      + " runtimeMillis=" + result.getRunTime());
    System.exit(result.wasSuccessful() && result.getIgnoreCount() == 0 && assumed[0] == 0 ? 0 : 1);
  }
}
