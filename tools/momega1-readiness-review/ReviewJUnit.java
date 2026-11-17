import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.util.*;
class ReviewJUnit {
 public static void main(String[] args) throws Exception {
  JUnitCore core=new JUnitCore();Set<String> failed=new HashSet<>();int[] assumptions={0};
  core.addListener(new RunListener(){
   public void testStarted(Description d){System.out.println("START "+d);}
   public void testFailure(Failure f){failed.add(f.getDescription().toString());System.out.println("FAIL "+f.getDescription()+" "+f.getTrace());}
   public void testAssumptionFailure(Failure f){assumptions[0]++;failed.add(f.getDescription().toString());System.out.println("ASSUMPTION_SKIP "+f.getDescription());}
   public void testIgnored(Description d){System.out.println("IGNORED "+d);}
   public void testFinished(Description d){System.out.println((failed.contains(d.toString())?"NOT_PASS ":"PASS ")+d);}
  });
  Class<?>[] types=new Class<?>[args.length];for(int i=0;i<args.length;i++)types[i]=Class.forName(args[i]);
  Result r=core.run(types);System.out.println("RESULT total="+r.getRunCount()+" failures="+r.getFailureCount()+" ignored="+r.getIgnoreCount()+" assumptionSkips="+assumptions[0]);
  System.exit(r.wasSuccessful()&&r.getIgnoreCount()==0&&assumptions[0]==0?0:1);
 }
}
