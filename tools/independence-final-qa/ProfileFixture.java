import java.io.File;
import at.heinz.deviceai.chief.ChiefBotProfile;
import at.heinz.deviceai.chief.ChiefBotProfileStore;
/** Writes only this review's synthetic store through production APIs. */
public final class ProfileFixture {
  public static void main(String[] args) {
    ChiefBotProfileStore store = new ChiefBotProfileStore(new File(args[0]),10000,8*1024*1024,16*1024,256*1024L);
    if (args[1].equals("seed")) {
      if (store.seedDefaults(20).size()!=20) throw new AssertionError("seed");
      ChiefBotProfile original=store.get("bot-forge");
      ChiefBotProfile renamed=store.rename("bot-forge","Prüfwerkstatt",original.getProfileVersion());
      ChiefBotProfile current=store.saveWorkingContext("bot-forge","bot-forge",args[2],renamed.getProfileVersion());
      if (!current.getPermissionScope().isEmpty()) throw new AssertionError("permissions");
    }
    if (!store.get("bot-forge").getDisplayName().equals("Prüfwerkstatt") ||
        !store.workingContext("bot-forge","bot-forge").equals(args[2])) throw new AssertionError("persistence");
    System.out.println("PROFILE_PRIVATE_CONTEXT_AND_IDENTITY_CONFIRMED");
  }
}
