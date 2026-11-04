package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ContinuumSourceResumeTest {
    private fun source():MentorSource {
        val excerpt="Synthetische Testquelle: Solarzellen wandeln Licht in elektrische Energie um."
        val sha=MessageDigest.getInstance("SHA-256").digest(excerpt.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return MentorSource("Solarzellen","https://example.org/solar",sha,excerpt)
    }

    private fun afterRestart(base:File,sha:String):String {
        val classpath=System.getProperty("java.class.path").split(File.pathSeparator).toMutableSet()
        var loader:ClassLoader?=javaClass.classLoader
        while(loader!=null) {
            if(loader is java.net.URLClassLoader) {
                loader.urLs.filter { it.protocol=="file" }.forEach {
                    classpath+=File(it.toURI()).absolutePath
                }
            }
            loader=loader.parent
        }
        val process=ProcessBuilder(
            File(System.getProperty("java.home"),"bin/java").absolutePath,
            "-Xmx96m","-cp",classpath.joinToString(File.pathSeparator),
            ContinuumSourceResumeRestartProbe::class.java.name,base.absolutePath,sha
        ).redirectErrorStream(true).start()
        try {
            assertTrue("Restart probe timed out",process.waitFor(10,TimeUnit.SECONDS))
            val output=process.inputStream.bufferedReader().readText()
            assertEquals(output,0,process.exitValue())
            return output.trim()
        } finally {
            process.destroyForcibly()
        }
    }

    @Test fun interruptedArchiveRemainsEligibleAcrossRestart() {
        val dir=Files.createTempDirectory("source-resume-archive").toFile()
        try {
            val source=source()
            ContinuumSourceNovelty.markStarted(dir,listOf(source))
            ContinuumKnowledgeArchive(dir).save(
                ContinuumKnowledgeArchive.Kind.PUBLIC_WEB_EXCERPT,source.subject,source.url,
                "Synthetischer Testauszug",source.excerpt
            )
            assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
            assertEquals("PENDING",afterRestart(dir,source.sha))
            assertEquals(listOf(source),ContinuumSourceNovelty.pending(
                listOf(source),ContinuumSourceNovelty.knownHashes(dir)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun deferredLedgerObservationRemainsEligibleAcrossRestart() {
        val dir=Files.createTempDirectory("source-resume-observed").toFile()
        try {
            val source=source()
            ContinuumSourceNovelty.markStarted(dir,listOf(source))
            val store=FileLearningExperienceStore(File(dir,"learning-core-v2"))
            val observed=LearningExperienceFactory.create(
                type=LearningExperienceType.SOURCE_TEXT,
                provenance=LearningProvenance.PUBLIC_WEB_SOURCE,
                trust=LearningTrustClass.SOURCE_OBSERVATION,
                status=LearningStatus.OBSERVED,content=source.excerpt,
                annotation=source.subject,sourceRef=source.url+"#sha256="+source.sha
            )
            assertTrue(store.append(observed))
            assertEquals("PENDING",afterRestart(dir,source.sha))
            assertTrue(store.updateStatus(observed.id,LearningStatus.REPLAY_PENDING))
            assertEquals("PENDING",afterRestart(dir,source.sha))
            assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}

object ContinuumSourceResumeRestartProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2)
        val known=ContinuumSourceNovelty.knownHashes(File(args[0]))
        print(if(args[1] in known)"KNOWN" else "PENDING")
    }
}
