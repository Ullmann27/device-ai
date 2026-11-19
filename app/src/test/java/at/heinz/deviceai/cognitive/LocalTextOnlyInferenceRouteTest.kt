package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalTextOnlyInferenceRouteTest {
    @Test fun ordinaryExplanationsAndLongFormSummariesDoNotNeedToolSchemas() {
        listOf(
            "Erkläre mir ausführlich den Unterschied zwischen einem Modellfenster und dem Langzeitgedächtnis.",
            "Bitte beschreibe mir in zehn Sätzen, wie Androids Speicherverwaltung funktioniert.",
            "Was sind die wichtigsten Gründe, warum ein Dialog bei vier Tausend Tokens abbricht?",
            "Fasse die bereitgestellte Dokumentpassage ausführlich in eigenen Worten zusammen."
        ).forEach { question ->
            assertTrue(question, LocalTextOnlyInferenceRoute.useToolFree(
                question, false, false, false, false))
        }
    }

    /** Code69: the forms the speech recogniser really delivers – colloquial imperatives, mostly without „?“. */
    @Test fun spokenExplanationsGreetingsAndThanksDoNotNeedToolSchemas() {
        listOf(
            "Erklär mir den Unterschied zwischen Kernspaltung und Kernfusion",
            "Erklär mir wie man Brot macht",
            "Erzähl mir einen Witz",
            "Erzähl mir was über das alte Rom",
            "Sag mir was über Wien",
            "Sag mal, wer war Marie Curie",
            "Beschreib mir kurz die Relativitätstheorie",
            "Fass das bitte in drei Sätzen zusammen",
            "Kannst du mir erklären, warum der Himmel blau ist",
            "Könntest du mir sagen, wie viele Planeten das Sonnensystem hat",
            "Kannst du das kurz zusammenfassen",
            "Ich möchte wissen, wie ein Otto-Motor funktioniert",
            "Weißt du, wer die Glühbirne erfunden hat",
            "Gib mir ein Beispiel für eine Metapher",
            "Schreib mir ein kurzes Gedicht über den Herbst",
            "Übersetz das Wort Schmetterling ins Englische",
            "Wo liegt Graz",
            "Wann war die Französische Revolution",
            "Hallo",
            "Guten Morgen",
            "Danke",
            "Vielen Dank, das war hilfreich",
            "Wie geht es dir"
        ).forEach { spoken ->
            assertTrue(spoken, LocalTextOnlyInferenceRoute.useToolFree(spoken, false, false, false, false))
        }
    }

    /** The spoken forms keep the tool route whenever a device thing or something current is named. */
    @Test fun spokenFormsAboutTheDeviceOrTheOutsideWorldKeepTheToolRoute() {
        listOf(
            "Sag mir den Akkustand",
            "Sag mir wie voll der Akku ist",
            "Sag mir wie spät es ist",
            "Erzähl mir was heute in der Welt passiert ist",
            "Erklär mir den Speicher",
            "Sag mir welche Apps installiert sind",
            "Sag mir wann mein nächster Termin ist",
            "Schreib mir eine Mail an Anna",
            "Sag Anna, dass ich später komme",
            "Schreib Anna, dass ich später komme",
            "Kannst du mir sagen, wie das Wetter wird",
            "Kannst du mir die Kamera öffnen",
            "Erzähl mir einen Witz und stell dann einen Timer",
            "Sag mir Bescheid und mach das Licht an",
            "Wo ist mein Handy",
            "Wann kommt das nächste Update",
            "Fass Seite 2 zusammen und speichere es als Word",
            "Guten Morgen, was steht morgen im Kalender",
            "Mach das Licht an",
            "Stell einen Timer auf fünf Minuten",
            "Mir ist langweilig"
        ).forEach { spoken ->
            assertFalse(spoken, LocalTextOnlyInferenceRoute.useToolFree(spoken, false, false, false, false))
        }
    }

    @Test fun deviceAndWebOperationsNeverLoseTheirExistingToolRoute() {
        listOf(
            "Öffne meinen Downloads-Ordner und klicke auf die PDF.",
            "Suche die installierte WhatsApp-App und sende eine Nachricht.",
            "Recherchiere im Internet die aktuellen Aktienkurse.",
            "Lösche unnötige Dateien auf dem Handy.",
            "Analysiere mein Handy mit Shizuku."
        ).forEach { instruction ->
            assertFalse(instruction, LocalTextOnlyInferenceRoute.useToolFree(
                instruction, false, false, false, false))
        }
    }

    @Test fun observedWorkBotAndCodingTurnsKeepToolAndConsentBoundaries() {
        val question = "Erkläre mir genau, was passiert ist."
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, true, false, false, false))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, false, true, false, false))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, false, false, true, false))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, false, false, false, true))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree("", false, false, false, false))
    }

    @Test fun continuationRetainsTheConfirmedTextOnlyRoute() {
        for (request in listOf("Weiter", "Bitte weiterrrr!", "Schreibe weiter", "Kannst du fortsetzen?")) {
            assertTrue(request, LocalTextOnlyInferenceRoute.useToolFree(request,
                false, false, false, false, priorTextOnlyResponse = true))
            assertFalse(request, LocalTextOnlyInferenceRoute.useToolFree(request,
                false, false, false, false, priorTextOnlyResponse = false))
        }
    }

    @Test fun previousTextNeverOverridesToolWorkOrCurrentActions() {
        for (index in 0..3) {
            val flags = List(4) { it == index }
            assertFalse(LocalTextOnlyInferenceRoute.useToolFree("Weiter", flags[0], flags[1],
                flags[2], flags[3], priorTextOnlyResponse = true))
        }
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree("Weiter und öffne WhatsApp",
            false, false, false, false, priorTextOnlyResponse = true))
    }
}
