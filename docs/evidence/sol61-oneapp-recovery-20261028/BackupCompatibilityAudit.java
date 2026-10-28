import at.heinz.deviceai.learning.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.security.*;

/** Explicit read-only owner-file audit. All application writes use a fresh temporary directory. */
public class BackupCompatibilityAudit {
  static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
    byte[] original=Files.readAllBytes(Path.of(args[0]));
    var restored=TrainableSubwordEncoder.Companion.restore(original);
    var fresh=new TrainableSubwordEncoder(73L);
    var intentField=ContinuumLearningCore.class.getDeclaredField("INTENT_HOLDOUT");intentField.setAccessible(true);
    var semanticField=ContinuumLearningCore.class.getDeclaredField("SEMANTIC_HOLDOUT");semanticField.setAccessible(true);
    var intents=(List<SubwordIntentExample>)intentField.get(null);
    var semantics=(List<SubwordSemanticExample>)semanticField.get(null);
    var canonical=new ByteArrayOutputStream();
    try(var out=new DataOutputStream(canonical)) {
      for(var e:intents){out.writeUTF(e.getText());out.writeInt(e.getLabel());}
      for(var e:semantics){out.writeUTF(e.getLeft());out.writeUTF(e.getRight());out.writeBoolean(e.getSimilar());}
    }
    var bi=fresh.evaluateIntent(intents);var bs=fresh.evaluateSemantic(semantics);
    var ci=restored.evaluateIntent(intents);var cs=restored.evaluateSemantic(semantics);
    var temp=Files.createTempDirectory("continuum-private-audit-");
    try {
      var core=new ContinuumLearningCore(temp.toFile(),()->System.currentTimeMillis());
      var decision=core.importSnapshot(original);
      var archive=new ContinuumKnowledgeArchive(temp.toFile());
      int entries;
      try(var in=Files.newInputStream(Path.of(args[1]))){entries=archive.importZip(in);}
      int first=archive.stats().values().stream().mapToInt(Integer::intValue).sum();
      try(var in=Files.newInputStream(Path.of(args[1]))){archive.importZip(in);}
      int second=archive.stats().values().stream().mapToInt(Integer::intValue).sum();
      if(first!=second)throw new AssertionError("Duplicate archive replay changed unique count");
      if(!hash(original).equals(hash(Files.readAllBytes(Path.of(args[0])))))throw new AssertionError("Input modified");
      System.out.printf(Locale.ROOT,"{\"swb1Updates\":%d,\"parameters\":%d,\"snapshotSha256\":\"%s\",\"frozenBenchmarkSha256\":\"%s\",\"freshIntent\":%d,\"oldIntent\":%d,\"intentTotal\":%d,\"freshSemantic\":%d,\"oldSemantic\":%d,\"semanticTotal\":%d,\"freshIntentLoss\":%.12f,\"oldIntentLoss\":%.12f,\"freshSemanticLoss\":%.12f,\"oldSemanticLoss\":%.12f,\"acceptedAgainstFreshSeedOnly\":%s,\"archiveEntriesValidated\":%d,\"uniqueSources\":%d,\"uniqueAfterRepeat\":%d,\"phoneTargetCompared\":false,\"inputFilesUnchanged\":true}%n", restored.getUpdates(),restored.getParameterCount(),hash(original),hash(canonical.toByteArray()),bi.getCorrect(),ci.getCorrect(),bi.getTotal(),bs.getCorrect(),cs.getCorrect(),bs.getTotal(),bi.getLoss(),ci.getLoss(),bs.getLoss(),cs.getLoss(),decision.getAccepted(),entries,first,second);
    } finally {try(var paths=Files.walk(temp)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  }
}
