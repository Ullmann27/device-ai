package at.heinz.deviceai.chief

import java.io.File

/** Compile/run only against reviewed schema-v1 source classes; no candidate classes. */
object LegacyProfileFixture {
    @JvmStatic fun main(args: Array<String>) {
        val s = ChiefBotProfileStore(File(args.single()))
        s.seedDefaults(3)
        val p = s.get("bot-kora")!!
        s.saveWorkingContext("bot-kora", "bot-kora", "LEGACY_PRIVATE_NOTE_174", p.profileVersion)
        println("V1_FIXTURE_CREATED")
    }
}
