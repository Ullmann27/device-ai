package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalQwenPromptPolicyTest {
    @Test fun qwen3DirectPromptNoLongerDisablesThinkingWithoutUserChoice() {
        val prompt = "Erkläre die Ursache des Compilerfehlers."
        val actual = LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B", prompt, 16_000)
        assertEquals(prompt, actual)
        assertFalse(actual.contains("/no_think"))
    }

    @Test fun gemmaAndUnknownModelReceiveOriginalPrompt() {
        val text = "Schreibe ein nachvollziehbares Kotlin-Programm."
        assertEquals(text, LocalQwenPromptPolicy.directUserPrompt("gemma4-e4b-it", text, 16_000))
        assertEquals(text, LocalQwenPromptPolicy.directUserPrompt(null, text, 16_000))
    }

    @Test fun explicitUserSwitchPreservedWithoutDuplicateInjection() {
        val prompt = "Antworte knapp.\n\n/no_think"
        assertEquals(prompt, LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B", prompt, 200))
        val maxed = "x".repeat(30)
        assertEquals(maxed, LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B", maxed, 30))
    }

    @Test fun originalPromptNeverReceivesSyntheticRoleOrToolDelimiters() {
        val result = LocalQwenPromptPolicy.directUserPrompt("Qwen3-0.6B", "Frage", 100)
        assertEquals("Frage", result)
        assertFalse(result.contains("<think>"))
        assertFalse(result.contains("<|im_start|>"))
    }

    @Test fun rejectInvalidPromptOrBudgetInsteadOfTruncating() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenPromptPolicy.directUserPrompt("gemma4-e4b-it", " ", 200)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenPromptPolicy.directUserPrompt("Qwen3", "long", 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenPromptPolicy.directUserPrompt("Qwen3", "ok", 0)
        }
    }
}
