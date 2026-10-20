package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQwenPromptPolicyTest {
    @Test fun qwen3DirectRequestGetsDocumentedSoftSwitch() {
        val plain="Erkläre in einem Satz, was ein Compiler macht."
        assertEquals("$plain\n\n/no_think",
            LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B",plain,16000))
        assertEquals("Hallo\n\n/no_think",
            LocalQwenPromptPolicy.directUserPrompt("litert-qwen3_0.6b","Hallo",200))
    }

    @Test fun otherModelsAreUntouched() {
        val prompt="Antwort ohne Zusätze."
        assertEquals(prompt,LocalQwenPromptPolicy.directUserPrompt("gemma3-1b",prompt,200))
        assertEquals(prompt,LocalQwenPromptPolicy.directUserPrompt(null,prompt,200))
    }

    @Test fun noDuplicateSwitchAndNoTruncation() {
        val prompt="Antworte knapp.\n\n/no_think"
        assertEquals(prompt,LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B",prompt,200))
        val maxed="x".repeat(30)
        assertEquals(maxed,LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B",maxed,30))
        assertFalse(LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B",maxed,30)
            .contains("/no_think"))
    }

    @Test fun untrustedAssistantPrefixIsNotInjected() {
        val result=LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B","Frage",100)
        assertFalse(result.contains("<think>"))
        assertFalse(result.contains("<|im_start|>"))
        assertTrue(result.endsWith("/no_think"))
    }
}
