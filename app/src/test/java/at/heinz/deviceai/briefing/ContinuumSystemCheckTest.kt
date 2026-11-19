package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.InternetAccessMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The truthful system start report: every check from its facts, and the wording rules of the spoken report. */
class ContinuumSystemCheckTest {
    private val gb = 1_000_000_000L

    /** A device on which every check is fine. */
    private fun fine() = ContinuumSystemFacts(
        brain = ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.READY, "gemma-3n-e4b-it"),
        speechInputAvailable = true, microphoneGranted = true, speechOutputEnabled = true, speechOutputReady = true,
        internetMode = InternetAccessMode.ASK, networkOnline = true, allFilesAccess = true, accessibilityOn = true,
        shizukuReady = true, batteryPercent = 82, charging = false, thermalStatus = 0,
        storageFreeBytes = 38 * gb, storageTotalBytes = 256 * gb)

    private fun check(facts: ContinuumSystemFacts, key: String) = ContinuumSystemCheck.evaluate(facts).single { it.key == key }
    private fun state(facts: ContinuumSystemFacts, key: String) = check(facts, key).state
    private fun report(facts: ContinuumSystemFacts, name: String? = "Heinz") =
        ContinuumSystemCheck.report(ContinuumSystemCheck.evaluate(facts), name)

    private val OK = ContinuumSystemCheck.State.OK
    private val WARN = ContinuumSystemCheck.State.WARN
    private val FAIL = ContinuumSystemCheck.State.FAIL
    private val OFF = ContinuumSystemCheck.State.OFF

    @Test fun everythingFineIsSaidShortAndPersonally() {
        val checks = ContinuumSystemCheck.evaluate(fine())
        assertTrue(checks.toString(), checks.all { it.state == OK })
        assertEquals("Alles läuft, Heinz: Gemma ist geladen, die Spracheingabe steht und der Akku ist bei 82 Prozent.", report(fine()))
        assertEquals("Alles läuft: Gemma ist geladen, die Spracheingabe steht und der Akku ist bei 82 Prozent.", report(fine(), null))
        // Typed results for the stage: key, German label, state, short detail.
        assertEquals(listOf("brain", "speech_in", "speech_out", "internet", "files", "accessibility", "bridge", "battery",
            "thermal", "storage", "open"), checks.map { it.key })
        assertEquals("gemma-3n-e4b-it · geladen", checks.first().detail)
        assertEquals("Gehirn", checks.first().label)
        assertEquals("82 % · lädt nicht", check(fine(), "battery").detail)
        assertEquals("38 GB frei", check(fine(), "storage").detail)
    }

    @Test fun problemsComeFirstAndPlainly() {
        val two = fine().copy(shizukuReady = false, thermalStatus = 2)
        assertEquals("Zwei Dinge, Heinz: Shizuku läuft nicht, und das Gerät ist schon warm. In Ordnung: Gemma ist geladen, " +
            "die Spracheingabe steht und der Akku ist bei 82 Prozent.", report(two))
        val one = fine().copy(shizukuReady = false)
        assertTrue(report(one), report(one).startsWith("Ein Punkt, Heinz: Shizuku läuft nicht."))
        val three = fine().copy(shizukuReady = false, thermalStatus = 2, microphoneGranted = false)
        assertTrue(report(three), report(three).startsWith(
            "Drei Dinge, Heinz: Das Mikrofon ist nicht freigegeben, Shizuku läuft nicht und das Gerät ist schon warm."))
        // A failure is named before a warning, whatever the order of the checks.
        val failing = fine().copy(shizukuReady = false, brain = ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.NOT_LOADED, null))
        assertTrue(report(failing), report(failing).startsWith("Zwei Dinge, Heinz: Es ist kein lokales Modell geladen, und Shizuku läuft nicht."))
        // The brain is then not listed among what is in order.
        assertFalse(report(failing), report(failing).substringAfter("In Ordnung:").contains("geladen"))
    }

    /** The fixed rule: never „Alles läuft“ (or anything like „optimal“) unless every single check is OK. */
    @Test fun allFineIsNeverSaidWithAnyCheckThatIsNotOk() {
        val base = fine()
        val variants: List<ContinuumSystemFacts> = listOf(
            base.copy(brain = ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.NOT_LOADED, null)),
            base.copy(brain = ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.LOADING, null)),
            base.copy(brain = ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.FAILED, "gemma")),
            base.copy(brain = ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.UNKNOWN, null)),
            base.copy(brain = ContinuumSystemFacts.Brain.Cloud(keyPresent = false)),
            base.copy(brain = ContinuumSystemFacts.Brain.Cloud(keyPresent = true), networkOnline = false),
            base.copy(speechInputAvailable = false), base.copy(speechInputAvailable = null), base.copy(microphoneGranted = false),
            base.copy(microphoneGranted = null), base.copy(speechOutputEnabled = false), base.copy(speechOutputReady = false),
            base.copy(speechOutputReady = null), base.copy(internetMode = InternetAccessMode.OFF), base.copy(internetMode = null),
            base.copy(networkOnline = false), base.copy(networkOnline = null), base.copy(allFilesAccess = false),
            base.copy(allFilesAccess = null), base.copy(accessibilityOn = false), base.copy(accessibilityOn = null),
            base.copy(shizukuReady = false), base.copy(shizukuReady = null), base.copy(batteryPercent = 12),
            base.copy(batteryPercent = 3), base.copy(batteryPercent = null), base.copy(thermalStatus = 2),
            base.copy(thermalStatus = 3), base.copy(thermalStatus = 5), base.copy(thermalStatus = null),
            base.copy(storageFreeBytes = 10 * gb), base.copy(storageFreeBytes = gb / 2), base.copy(storageFreeBytes = null))
        variants.forEach { facts ->
            val checks = ContinuumSystemCheck.evaluate(facts)
            assertTrue("one check must not be OK: $facts", checks.any { it.state != OK })
            val text = ContinuumSystemCheck.report(checks, "Heinz")
            assertFalse(text, text.contains("Alles läuft"))
            assertFalse(text, text.contains("optimal", ignoreCase = true))
            assertFalse(text, text.contains("alle Systeme", ignoreCase = true))
        }
        // Every combination of two deviations as well.
        var pairs = 0
        for (i in variants.indices) for (j in i + 1 until variants.size) {
            val a = variants[i]
            val b = variants[j]
            val both = b.copy(brain = if (a.brain != base.brain) a.brain else b.brain,
                shizukuReady = if (a.shizukuReady != base.shizukuReady) a.shizukuReady else b.shizukuReady,
                thermalStatus = if (a.thermalStatus != base.thermalStatus) a.thermalStatus else b.thermalStatus,
                batteryPercent = if (a.batteryPercent != base.batteryPercent) a.batteryPercent else b.batteryPercent,
                internetMode = if (a.internetMode != base.internetMode) a.internetMode else b.internetMode)
            val text = report(both)
            assertFalse(text, text.contains("Alles läuft"))
            pairs++
        }
        assertTrue(pairs > 400)
    }

    @Test fun whatTheOwnerSwitchedOffIsNamedAsSwitchedOffNotAsAFault() {
        val local = fine().copy(internetMode = InternetAccessMode.OFF, speechOutputEnabled = false)
        assertEquals(OFF, state(local, "internet"))
        assertEquals(OFF, state(local, "speech_out"))
        assertEquals("Heinz, was eingeschaltet ist, läuft: Gemma ist geladen, die Spracheingabe steht und der Akku ist bei 82 Prozent. " +
            "Ausgeschaltet sind die Sprachausgabe und der Internetzugang.", report(local))
        val grant = fine().copy(allFilesAccess = false)
        assertEquals(OFF, state(grant, "files"))
        assertTrue(report(grant), report(grant).endsWith("Ausgeschaltet ist der Zugriff auf alle Dateien."))
        assertEquals(OFF, state(fine().copy(accessibilityOn = false), "accessibility"))
        // Off and a problem together: the problem first, then what is off.
        val mixed = local.copy(shizukuReady = false)
        assertTrue(report(mixed), report(mixed).startsWith("Ein Punkt, Heinz: Shizuku läuft nicht."))
        assertTrue(report(mixed), report(mixed).contains("Ausgeschaltet sind die Sprachausgabe und der Internetzugang."))
    }

    @Test fun theBrainCheckSaysWhichBrainIsInUseAndWhetherItIsReady() {
        fun local(state: ContinuumSystemFacts.Brain.State, name: String? = "gemma-3n-e4b-it") =
            fine().copy(brain = ContinuumSystemFacts.Brain.Local(state, name))
        assertEquals(OK, state(local(ContinuumSystemFacts.Brain.State.READY), "brain"))
        assertEquals("Gemma ist geladen und antwortet gerade", check(local(ContinuumSystemFacts.Brain.State.GENERATING), "brain").spoken)
        assertEquals(WARN, state(local(ContinuumSystemFacts.Brain.State.LOADING), "brain"))
        assertEquals(WARN, state(local(ContinuumSystemFacts.Brain.State.UNLOADING), "brain"))
        assertEquals(FAIL, state(local(ContinuumSystemFacts.Brain.State.NOT_LOADED), "brain"))
        assertEquals(FAIL, state(local(ContinuumSystemFacts.Brain.State.FAILED), "brain"))
        assertEquals(WARN, state(local(ContinuumSystemFacts.Brain.State.UNKNOWN), "brain"))
        // A model whose identifier gives no word is not given a name.
        assertEquals("das lokale Modell ist geladen", check(local(ContinuumSystemFacts.Brain.State.READY, "42-x"), "brain").spoken)
        assertEquals("das lokale Modell ist geladen", check(local(ContinuumSystemFacts.Brain.State.READY, null), "brain").spoken)
        val claude = fine().copy(brain = ContinuumSystemFacts.Brain.Cloud(keyPresent = true))
        assertEquals(OK, state(claude, "brain"))
        assertEquals("Claude ist ausgewählt und der Schlüssel ist hinterlegt", check(claude, "brain").spoken)
        assertEquals(FAIL, state(fine().copy(brain = ContinuumSystemFacts.Brain.Cloud(keyPresent = false)), "brain"))
        assertEquals(WARN, state(claude.copy(networkOnline = false), "brain"))
        // Nothing is claimed about reaching the service: only selection and key.
        assertFalse(check(claude, "brain").spoken.contains("erreichbar"))
    }

    @Test fun batteryHeatAndStorageThresholds() {
        assertEquals(OK, state(fine().copy(batteryPercent = 21), "battery"))
        assertEquals(WARN, state(fine().copy(batteryPercent = 20), "battery"))
        assertEquals(FAIL, state(fine().copy(batteryPercent = 5), "battery"))
        assertEquals("a low battery on the charger is no problem", OK, state(fine().copy(batteryPercent = 4, charging = true), "battery"))
        assertEquals("der Akku ist bei 4 Prozent und lädt", check(fine().copy(batteryPercent = 4, charging = true), "battery").spoken)
        assertEquals(WARN, state(fine().copy(batteryPercent = 150), "battery"))
        assertEquals(OK, state(fine().copy(thermalStatus = 1), "thermal"))
        assertEquals("das Gerät ist leicht erwärmt", check(fine().copy(thermalStatus = 1), "thermal").spoken)
        assertEquals(WARN, state(fine().copy(thermalStatus = 2), "thermal"))
        assertEquals(FAIL, state(fine().copy(thermalStatus = 3), "thermal"))
        // The pause of the local model is only said where a local model is the brain.
        assertTrue(check(fine().copy(thermalStatus = 3), "thermal").spoken.contains("das lokale Modell antwortet erst wieder"))
        assertEquals("das Gerät ist stark erwärmt", check(fine().copy(thermalStatus = 4,
            brain = ContinuumSystemFacts.Brain.Cloud(true)), "thermal").spoken)
        assertEquals(WARN, state(fine().copy(thermalStatus = 9), "thermal"))
        assertEquals(OK, state(fine().copy(storageFreeBytes = 26 * gb), "storage"))
        assertEquals(WARN, state(fine().copy(storageFreeBytes = 25 * gb), "storage"))
        assertEquals(FAIL, state(fine().copy(storageFreeBytes = 7 * gb), "storage"))
        assertEquals(FAIL, state(fine().copy(storageFreeBytes = gb - 1, storageTotalBytes = 8 * gb), "storage"))
        assertEquals("der Speicher wird knapp, noch 7,5 Gigabyte frei",
            check(fine().copy(storageFreeBytes = 7_500_000_000L, storageTotalBytes = 100 * gb), "storage").spoken)
        assertEquals(WARN, state(fine().copy(storageFreeBytes = 300 * gb), "storage"))
    }

    @Test fun openItemsAndRunningWorkAreNamedOnlyFromRealState() {
        val idle = report(fine())
        listOf("Agent", "Forschungsdaten", "koordinier", "Auftrag").forEach { assertFalse(idle, idle.contains(it)) }
        val busy = fine().copy(running = listOf("eine Auftragsfolge"), openWork = listOf("Vertrag.pdf: Entwurf prüfen und speichern"),
            openMessages = 2)
        assertEquals(OK, state(busy, "activity"))
        assertEquals("Alles läuft, Heinz: Gemma ist geladen, die Spracheingabe steht und der Akku ist bei 82 Prozent. " +
            "Gerade läuft eine Auftragsfolge. Offen sind noch eine Arbeit und zwei Nachrichten.", report(busy))
        assertEquals("1 offene Arbeit · 2 offene Nachrichten", check(busy, "open").detail)
        assertEquals("Offen ist noch eine Nachricht.", report(fine().copy(openMessages = 1)).substringAfter("Prozent. "))
        // No message list wired: nothing is said about messages.
        assertEquals("nichts offen", check(fine().copy(openMessages = null), "open").detail)
    }

    @Test fun manyProblemsAreCountedHonestlyAndTheListShowsEveryCheck() {
        val bad = fine().copy(shizukuReady = false, thermalStatus = 2, microphoneGranted = false, networkOnline = false,
            batteryPercent = 9, storageFreeBytes = 20 * gb)
        val text = report(bad)
        assertTrue(text, text.startsWith("Mehrere Dinge, Heinz: "))
        assertTrue(text, text.contains("Dazu zwei weitere Punkte in der Liste."))
        val lines = ContinuumSystemCheck.lines(ContinuumSystemCheck.evaluate(bad)).lines()
        assertEquals(11, lines.size)
        assertTrue(lines.toString(), lines.contains("! Shizuku: nicht erreichbar"))
        assertTrue(lines.toString(), lines.contains("✓ Gehirn: gemma-3n-e4b-it · geladen"))
        assertTrue(ContinuumSystemCheck.lines(ContinuumSystemCheck.evaluate(fine().copy(internetMode = InternetAccessMode.OFF)))
            .contains("– Internet: in der App ausgeschaltet"))
        assertEquals("Ich konnte den Systemcheck gerade nicht ausführen.", ContinuumSystemCheck.report(emptyList(), "Heinz"))
    }

    @Test fun theSnapshotHoldsTheChecksOfTheLastRun() {
        val checks = ContinuumSystemCheck.run(fine().copy(shizukuReady = false))
        assertEquals(checks, ContinuumSystemCheck.snapshot())
        assertEquals(WARN, ContinuumSystemCheck.snapshot().single { it.key == "bridge" }.state)
        ContinuumSystemCheck.run(fine())
        assertTrue(ContinuumSystemCheck.snapshot().all { it.state == OK })
    }
}
