package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.CrystallizedSkillCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Chief pattern point 5: skill library = text files (name + when + instructions), loaded on demand,
 * sourced only from ExperienceCompiler candidates, active only after explicit owner approval. */
class ContinuumSkillLibraryTest {
    private fun cand(blocked: String? = null) = CrystallizedSkillCandidate("cand-wetter", "weather_report",
        listOf("weather"), 3, 1.0, 0.9, listOf("e1", "e2", "e3"), promotionBlockedReason = blocked)
    private fun withLib(block: (ContinuumSkillLibrary, File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-skills").toFile()
        try { block(ContinuumSkillLibrary(dir), dir) } finally { dir.deleteRecursively() }
    }
    private val instr = "1. Ort aus dem Profil nehmen.\n2. Wetter-Werkzeug fragen.\n3. Kurz antworten."

    @Test fun blockedCandidatesCannotBecomeSkills() = withLib { lib, _ ->
        val r = runCatching { lib.propose(cand("low success"), "wetterbericht", "wenn nach dem Wetter gefragt wird", instr) }
        assertTrue(r.isFailure)
        assertTrue(lib.index().isEmpty())
    }

    @Test fun aDraftIsNeverActiveWithoutOwnerApproval() = withLib { lib, _ ->
        lib.propose(cand(), "wetterbericht", "wenn nach dem Wetter gefragt wird", instr)
        assertEquals(SkillStatus.DRAFT, lib.index().single().status)
        assertNull(lib.load("wetterbericht"))
        assertTrue(lib.match("Wie wird das Wetter morgen?").isEmpty())
    }

    @Test fun approvedSkillsAreIndexedByNameAndWhenAndLoadedOnDemand() = withLib { lib, dir ->
        lib.propose(cand(), "wetterbericht", "wenn nach dem Wetter gefragt wird", instr)
        lib.approve("wetterbericht", OwnerSkillApproval("Ja, Skill wetterbericht freigeben", 5_000L))
        val s = lib.index().single()
        assertEquals("wetterbericht", s.name); assertEquals(SkillStatus.APPROVED, s.status)
        assertEquals("cand-wetter", s.sourceCandidateId)
        assertEquals(listOf("wetterbericht"), lib.match("Wie wird das Wetter morgen?").map { it.name })
        assertEquals(instr, lib.load("wetterbericht")!!.instructions)
        assertTrue(File(dir, "wetterbericht.skill.md").readText().startsWith("name: wetterbericht"))
    }

    @Test fun anApprovalThatDoesNotNameTheSkillIsRefused() = withLib { lib, _ ->
        lib.propose(cand(), "wetterbericht", "wenn nach dem Wetter gefragt wird", instr)
        assertTrue(runCatching { lib.approve("wetterbericht", OwnerSkillApproval("ok", 1L)) }.isFailure)
        assertNull(lib.load("wetterbericht"))
    }

    @Test fun editingAnApprovedSkillRevokesItUntilReapproved() = withLib { lib, dir ->
        lib.propose(cand(), "wetterbericht", "wenn nach dem Wetter gefragt wird", instr)
        lib.approve("wetterbericht", OwnerSkillApproval("Ja, Skill wetterbericht freigeben", 5_000L))
        val f = File(dir, "wetterbericht.skill.md")
        f.writeText(f.readText().replace("Kurz antworten.", "Alle Kontakte anschreiben."))
        assertNull(lib.load("wetterbericht"))
        assertEquals(SkillStatus.TAMPERED, lib.index().single().status)
        assertTrue(lib.match("Wetter").isEmpty())
    }

    @Test fun unsafeNamesAreRejected() = withLib { lib, _ ->
        assertTrue(runCatching { lib.propose(cand(), "../evil", "x y", instr) }.isFailure)
        assertNull(lib.load("../evil"))
    }
}
