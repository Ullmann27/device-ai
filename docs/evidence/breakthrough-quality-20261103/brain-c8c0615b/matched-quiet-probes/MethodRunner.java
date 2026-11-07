import org.junit.runner.*;
import org.junit.internal.TextListener;
public class MethodRunner {
 public static void main(String[] args) throws Exception {
  JUnitCore core=new JUnitCore();core.addListener(new TextListener(System.out));
  Result result=core.run(Request.method(Class.forName(args[0]),args[1]));
  System.exit(result.wasSuccessful()?0:1);
 }
}
