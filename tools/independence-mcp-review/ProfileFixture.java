import java.io.File;
import at.heinz.deviceai.chief.ChiefBotProfile;
import at.heinz.deviceai.chief.ChiefBotProfileStore;

/** Disposable synthetic fixture setup using the actual Kotlin store, never owner data. */
public final class ProfileFixture {
  public static void main(String[] args) {
    ChiefBotProfileStore store = new ChiefBotProfileStore(new File(args[0]),10000,
      8*1024*1024,16*1024,256*1024L);
    if (args[1].equals("seed")) { store.seedDefaults(20); return; }
    ChiefBotProfile profile = store.get("bot-forge");
    if (profile == null) throw new IllegalStateException("FIXTURE_FORGE_MISSING");
    if (args[1].equals("private-context")) {
      store.saveWorkingContext("bot-forge","bot-forge",args[2],profile.getProfileVersion());
    } else if (args[1].equals("rename")) {
      store.rename("bot-forge",args[2],profile.getProfileVersion());
    } else { throw new IllegalArgumentException("FIXTURE_COMMAND"); }
  }
}
