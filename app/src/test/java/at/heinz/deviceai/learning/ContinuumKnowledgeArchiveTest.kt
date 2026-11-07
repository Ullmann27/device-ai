package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class ContinuumKnowledgeArchiveTest {
    @Test fun sourceIdReadIsBoundedVerifiedAndDoesNotChangeTheArchive() {
        val dir=Files.createTempDirectory("continuum-source-by-id").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(dir)
            val saved=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Quarzforschung","https://de.wikipedia.org/wiki/Quarzforschung","Synthetischer Test",
                "Die Quarzforschung untersucht die Resonanz und stellt geprüfte Messverfahren bereit.")
            val state=archive.indexState()
            assertEquals(saved,archive.readSource(saved.id))
            assertNull(archive.readSource("0".repeat(64)))
            assertThrows(IllegalArgumentException::class.java) { archive.readSource("../outside") }
            assertEquals(state,archive.indexState())
            File(dir,"continuum-knowledge-v1/${saved.id}.rec").writeBytes(byteArrayOf(1,2,3))
            assertNull(archive.readSource(saved.id))
        } finally { dir.deleteRecursively() }
    }
    private fun publicRecord(archive:ContinuumKnowledgeArchive,topic:String) = archive.save(
        ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,topic,
        "https://de.wikipedia.org/wiki/$topic","Wikipedia-Mitwirkende · CC BY-SA 4.0",
        "Der synthetische Quellenauszug behandelt $topic und nachprüfbare Grundlagen."
    )

    @Test fun interruptedCommitResumesOnReopenAndRetryDoesNotDuplicateOrOverwriteOldSource() {
        val source=Files.createTempDirectory("archive-recovery-source").toFile()
        val target=Files.createTempDirectory("archive-recovery-target").toFile()
        try {
            val origin=ContinuumKnowledgeArchive(source)
            publicRecord(origin,"Astronomie")
            publicRecord(origin,"Biologie")
            publicRecord(origin,"Chemie")
            val bytes=java.io.ByteArrayOutputStream().also { origin.exportZip(it) }.toByteArray()
            val destination=ContinuumKnowledgeArchive(target)
            val original=publicRecord(destination,"Astronomie")
            val root=java.io.File(target,"continuum-knowledge-v1")
            val originalBytes=java.io.File(root,"${original.id}.rec").readBytes()
            destination.beforeImportPublish={ index ->
                if(index==1)throw java.io.IOException("Injected second-publish interruption")
            }
            assertThrows(java.io.IOException::class.java) {
                destination.importZip(java.io.ByteArrayInputStream(bytes))
            }
            assertTrue(java.io.File(root,".import-pending-v1/manifest.v1").isFile)
            assertEquals(2,root.listFiles()!!.count { it.name.endsWith(".rec") })

            val reopened=ContinuumKnowledgeArchive(target)
            assertEquals(3,reopened.stats().values.sum())
            assertFalse(java.io.File(root,".import-pending-v1").exists())
            assertArrayEquals(originalBytes,java.io.File(root,"${original.id}.rec").readBytes())
            val before=root.listFiles()!!.filter { it.name.endsWith(".rec") }
                .associate { it.name to it.readBytes().toList() }
            assertEquals(3,reopened.importZip(java.io.ByteArrayInputStream(bytes)))
            assertEquals(before,root.listFiles()!!.filter { it.name.endsWith(".rec") }
                .associate { it.name to it.readBytes().toList() })
        } finally {source.deleteRecursively();target.deleteRecursively()}
    }

    @Test fun pendingRecoveryValidatesAllRemainingRecordsBeforePublishingAnyMore() {
        val source=Files.createTempDirectory("archive-recovery-corrupt-source").toFile()
        val target=Files.createTempDirectory("archive-recovery-corrupt-target").toFile()
        try {
            val origin=ContinuumKnowledgeArchive(source)
            listOf("Astronomie","Biologie","Chemie").forEach { publicRecord(origin,it) }
            val bytes=java.io.ByteArrayOutputStream().also { origin.exportZip(it) }.toByteArray()
            val destination=ContinuumKnowledgeArchive(target)
            destination.beforeImportPublish={ index ->
                if(index==1)throw java.io.IOException("Injected interrupted filesystem commit")
            }
            assertThrows(java.io.IOException::class.java) {
                destination.importZip(java.io.ByteArrayInputStream(bytes))
            }
            val root=java.io.File(target,"continuum-knowledge-v1")
            val pending=java.io.File(root,".import-pending-v1")
            val remaining=pending.listFiles()!!.filter { it.name.endsWith(".rec") }
            assertEquals(2,remaining.size)
            remaining.last().writeBytes(byteArrayOf(1,2,3))
            assertThrows(IllegalArgumentException::class.java) { ContinuumKnowledgeArchive(target) }
            assertEquals("No later item is published before all staged records validate",
                1,root.listFiles()!!.count { it.name.endsWith(".rec") })
            assertTrue(java.io.File(pending,"manifest.v1").exists())
        } finally {source.deleteRecursively();target.deleteRecursively()}
    }

    @Test fun uncommittedStagingIsDiscardedWithoutPublishingOrRemovingExistingSources() {
        val target=Files.createTempDirectory("archive-recovery-uncommitted").toFile()
        try {
            val existing=publicRecord(ContinuumKnowledgeArchive(target),"Astronomie")
            val root=java.io.File(target,"continuum-knowledge-v1")
            val existingFile=java.io.File(root,"${existing.id}.rec")
            val before=existingFile.readBytes()
            val pending=java.io.File(root,".import-pending-v1").also { assertTrue(it.mkdir()) }
            java.io.File(pending,"a".repeat(64)+".rec").writeBytes(byteArrayOf(1,2,3))
            java.io.File(pending,"manifest.tmp").writeText("incomplete staging")
            val reopened=ContinuumKnowledgeArchive(target)
            assertEquals(1,reopened.stats().values.sum())
            assertArrayEquals(before,existingFile.readBytes())
            assertFalse(pending.exists())
        } finally {target.deleteRecursively()}
    }

    @Test fun unknownPendingFilesFailClosedAndAreNeverAutomaticallyDeleted() {
        val target=Files.createTempDirectory("archive-recovery-unknown").toFile()
        try {
            ContinuumKnowledgeArchive(target)
            val pending=java.io.File(target,"continuum-knowledge-v1/.import-pending-v1")
                .also { assertTrue(it.mkdir()) }
            val unknown=java.io.File(pending,"owner-export.json").also { it.writeText("synthetic") }
            assertThrows(IllegalArgumentException::class.java) { ContinuumKnowledgeArchive(target) }
            assertTrue(unknown.exists())
        } finally {target.deleteRecursively()}
    }
    @Test fun indexedOfflineArchiveStoresCitationsAndRevisions() {
        val temp=Files.createTempDirectory("continuum-archive").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val source="https://de.wikipedia.org/wiki/Robotik"
            val original=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik",source,"Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Robotik untersucht die Entwicklung und den Einsatz technischer Roboter.")
            val duplicate=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik",source,"Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Robotik untersucht die Entwicklung und den Einsatz technischer Roboter.")
            assertEquals(original.id,duplicate.id)
            assertEquals(1,archive.stats()[ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT])
            val found=ContinuumKnowledgeArchive(temp).search("robotik")
            assertEquals(1,found.size)
            assertEquals(source,found.single().source.url)
            assertEquals(original.sha256,found.single().source.sha256)
            val changed=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik",source,"Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Die moderne Robotik verbindet Automatisierung, Sensoren und Antriebstechnik.")
            assertNotEquals(original.id,changed.id)
            assertEquals(2,archive.stats()[ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT])
        } finally { temp.deleteRecursively() }
    }
    @Test fun invalidDomainsAndUnsafeReferencesAreRejected() {
        val temp=Files.createTempDirectory("continuum-archive-guard").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val text="Ein ausreichend langer amtlicher Rechtsauszug zu einem Sachverhalt."
            for(url in listOf("https://evil.example/RIS","http://www.ris.bka.gv.at/gesetz",
                "https://www.ris.bka.gv.at.evil.example/gesetz",
                "https://user:pass@www.ris.bka.gv.at/doc")) {
                try {
                    archive.save(ContinuumKnowledgeArchive.Kind.RIS_LEGISLATION,
                        "Paragraph eins",url,"Rechtsinformationssystem",text)
                    fail("Untrusted domain accepted")
                } catch (_: IllegalArgumentException) {}
            }
            assertTrue(archive.stats().values.all { it==0 })
        } finally { temp.deleteRecursively() }
    }
    @Test fun zipRoundTripPersistsAndVerifiesOriginalCitationRecords() {
        val base=Files.createTempDirectory("continuum-archive-export").toFile()
        val restored=Files.createTempDirectory("continuum-archive-import").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(base)
            val record=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Elektrotechnik","https://de.wikipedia.org/wiki/Elektrotechnik",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Die Elektrotechnik beschreibt elektrische Systeme und ihre Anwendungen.")
            val bytes=java.io.ByteArrayOutputStream()
            assertEquals(1,archive.exportZip(bytes))
            assertEquals(1,ContinuumKnowledgeArchive(restored)
                .importZip(java.io.ByteArrayInputStream(bytes.toByteArray())))
            val hit=ContinuumKnowledgeArchive(restored).search("Elektrotechnik").single()
            assertEquals(record.id,hit.source.id)
            assertEquals(record.sha256,hit.source.sha256)
        } finally { base.deleteRecursively(); restored.deleteRecursively() }
    }

    @Test fun unsafeZipNamesAreRejectedWithoutWritingAnyRecords() {
        val base=Files.createTempDirectory("continuum-archive-zip-slip").toFile()
        try {
            val out=java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("../outside.rec"))
                zip.write(byteArrayOf(1,2,3))
                zip.closeEntry()
            }
            try {
                ContinuumKnowledgeArchive(base)
                    .importZip(java.io.ByteArrayInputStream(out.toByteArray()))
                fail("Traversal archive should be rejected")
            } catch (_: IllegalArgumentException) {}
            assertTrue(ContinuumKnowledgeArchive(base).stats().values.all { it==0 })
        } finally { base.deleteRecursively() }
    }

    @Test fun genericWikipediaTitleWordCannotHijackMarsFollowUp() {
        val temp=Files.createTempDirectory("continuum-mars-follow-up").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val economics=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Alfred-Nobel-Gedächtnispreis für Wirtschaftswissenschaften",
                "https://de.wikipedia.org/wiki/Alfred-Nobel-Gedächtnispreis_für_Wirtschaftswissenschaften",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Der Alfred-Nobel-Gedächtnispreis wird jährlich für die Wirtschaftswissenschaften vergeben.")
            val question="Welche Folgen hat das für Menschen, die dort leben möchten?"
            val lexicalHits=archive.search(question,limit=20)
            assertTrue("Regression setup must reproduce the old generic-title-word hit",
                lexicalHits.any { it.score>=5 && it.source.id==economics.id })
            assertNull("A follow-up without a self-contained topic must reach dialogue context",
                ContinuumArchiveAnswerPolicy.select(question,lexicalHits))
            assertEquals(1,archive.stats()[ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT])
        } finally { temp.deleteRecursively() }
    }

    @Test fun directArchiveQuestionNeedsAnExplicitSourceTitleTopic() {
        val temp=Files.createTempDirectory("continuum-archive-topic-gate").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val mars=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Mars","https://de.wikipedia.org/wiki/Mars",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Der Mars besitzt eine dünne Atmosphäre und gilt als Planet unseres Sonnensystems.")
            archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Alfred-Nobel-Gedächtnispreis für Wirtschaftswissenschaften",
                "https://de.wikipedia.org/wiki/Alfred-Nobel-Gedächtnispreis_für_Wirtschaftswissenschaften",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Dieser Wirtschaftspreis zeichnet Arbeiten im Bereich der Wirtschaftswissenschaften aus.")
            val direct="Was ist der Mars?"
            val selected=ContinuumArchiveAnswerPolicy.select(direct,archive.search(direct,limit=20))
            assertEquals(mars.id,selected?.source?.id)
            assertNull(ContinuumArchiveAnswerPolicy.select(
                "Was folgt daraus?",archive.search("Was folgt daraus?",limit=20)))
            assertNull(ContinuumArchiveAnswerPolicy.select(
                "Warum ist Mars anders?",archive.search("Wirtschaftswissenschaften",limit=20)))
            assertEquals(2,archive.stats()[ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT])
        } finally { temp.deleteRecursively() }
    }

    @Test fun archivedAndroidTitleCannotReplaceAGenerativeCodingHelpRequest() {
        val temp=Files.createTempDirectory("continuum-archive-coding-intent").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val android=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Android (Betriebssystem)", "https://de.wikipedia.org/wiki/Android_(Betriebssystem)",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Android ist ein mobiles Betriebssystem auf Basis eines Linux-Kernels.")
            val userQuestion="Erkläre mir in zwei Sätzen, wie du mir beim Programmieren einer Android-App helfen kannst."
            val candidates=archive.search(userQuestion,limit=20)
            assertTrue("Reproduce the real title-level Android false positive",
                candidates.any { it.source.id==android.id && it.score>=5 })
            for(question in listOf(
                userQuestion,
                "Kannst du mir beim Programmieren einer Android-App helfen?",
                "Wie entwickle ich eine Android-App?",
                "Was ist Android und wie entwickle ich damit eine App?",
                "Warum ist Android für meine App geeignet?",
                "Erkläre mir, wie Android funktioniert."
            )) {
                assertNull("A help, how-to or reasoning request is not answered by a raw excerpt: $question",
                    ContinuumArchiveAnswerPolicy.select(question,archive.search(question,limit=20)))
            }
            for(question in listOf("Was ist Android?", "Erkläre Android kurz.")) {
                assertEquals("Direct factual archive lookups must continue to work: $question",
                    android.id,
                    ContinuumArchiveAnswerPolicy.select(question,archive.search(question,limit=20))?.source?.id)
            }
            assertEquals(1,archive.stats()[ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT])
        } finally { temp.deleteRecursively() }
    }

    @Test fun legalArchiveIsNotMisreportedAsLegalValidity() {
        val temp=Files.createTempDirectory("continuum-ris-source").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val record=archive.save(ContinuumKnowledgeArchive.Kind.RIS_CASE_LAW,
                "Musterentscheid", "https://www.ris.bka.gv.at/JustizEntscheidung.wxe",
                "RIS · Rechtsinformationssystem des Bundes / BKA",
                "Dies ist ein öffentlicher, nicht verbindlich verifizierter Beispielauszug.",
                sourceDate="2026-10-20")
            assertEquals("2026-10-20",record.sourceDate)
            assertEquals(1,archive.search("Musterentscheid",
                ContinuumKnowledgeArchive.Kind.RIS_CASE_LAW).size)
        } finally { temp.deleteRecursively() }
    }
    @Test fun corruptedLaterZipRecordCannotPartiallyMigrateAnEarlierValidSource() {
        val old=Files.createTempDirectory("continuum-migrate-old").toFile()
        val target=Files.createTempDirectory("continuum-migrate-target").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(old)
            archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik", "https://de.wikipedia.org/wiki/Robotik",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Roboter können Wahrnehmung und Bewegungen anhand von Sensoren steuern.")
            val exported=java.io.ByteArrayOutputStream()
            archive.exportZip(exported)
            val combined=java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(combined).use { output ->
                java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(exported.toByteArray())).use { input ->
                    val entry=input.nextEntry!!
                    output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    output.write(input.readBytes())
                    output.closeEntry()
                }
                output.putNextEntry(java.util.zip.ZipEntry("f".repeat(64)+".rec"))
                output.write(byteArrayOf(1,2,3,4))
                output.closeEntry()
            }
            val destination=ContinuumKnowledgeArchive(target)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                destination.importZip(java.io.ByteArrayInputStream(combined.toByteArray()))
            }
            assertTrue("No earlier source may be committed when a later entry is corrupt",
                destination.stats().values.all { it==0 })
        } finally {old.deleteRecursively();target.deleteRecursively()}
    }

    @Test fun unsafeLaterZipNameCannotCommitEarlierValidatedSources() {
        val old=Files.createTempDirectory("continuum-unsafe-old").toFile()
        val target=Files.createTempDirectory("continuum-unsafe-target").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(old)
            archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Physik", "https://de.wikipedia.org/wiki/Physik",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Physik beschäftigt sich mit Materie und Wechselwirkungen in der Natur.")
            val original=java.io.ByteArrayOutputStream()
            archive.exportZip(original)
            val combined=java.io.ByteArrayOutputStream()
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(original.toByteArray())).use { input ->
                val item=input.nextEntry!!
                val bytes=input.readBytes()
                java.util.zip.ZipOutputStream(combined).use { out ->
                    out.putNextEntry(java.util.zip.ZipEntry(item.name))
                    out.write(bytes)
                    out.closeEntry()
                    out.putNextEntry(java.util.zip.ZipEntry("../outside.rec"))
                    out.write(byteArrayOf(1,2,3))
                    out.closeEntry()
                }
            }
            val destination=ContinuumKnowledgeArchive(target)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                destination.importZip(java.io.ByteArrayInputStream(combined.toByteArray()))
            }
            assertTrue(destination.stats().values.all { it==0 })
        } finally {old.deleteRecursively();target.deleteRecursively()}
    }

    @Test fun existingDamagedCollisionBlocksImportWithoutAddingEarlierRecords() {
        val old=Files.createTempDirectory("continuum-old-two").toFile()
        val target=Files.createTempDirectory("continuum-target-corrupt").toFile()
        try {
            val src=ContinuumKnowledgeArchive(old)
            val a=src.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Astronomie","https://de.wikipedia.org/wiki/Astronomie",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Astronomie ist die Wissenschaft der Gestirne und ihrer Bewegungen.")
            val b=src.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Biologie","https://de.wikipedia.org/wiki/Biologie",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Biologie untersucht Lebewesen und grundlegende Prozesse des Lebens.")
            val exported=java.io.ByteArrayOutputStream()
            src.exportZip(exported)
            val destination=ContinuumKnowledgeArchive(target)
            val archiveRoot=java.io.File(target,"continuum-knowledge-v1")
            java.io.File(archiveRoot,b.id+".rec").writeBytes(byteArrayOf(8,7,6))
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                destination.importZip(java.io.ByteArrayInputStream(exported.toByteArray()))
            }
            assertFalse("No valid first record may be imported before a later corrupt collision",
                java.io.File(archiveRoot,a.id+".rec").exists())
            assertTrue("Never erase the original malformed record automatically",
                java.io.File(archiveRoot,b.id+".rec").exists())
        } finally {old.deleteRecursively();target.deleteRecursively()}
    }

}
