package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictJsonTest {
    @Test fun parsesNestedManifestValues() {
        val value=StrictJson.parseObject(
            """{"a":1,"b":-2.5e1,"c":"x\u00e4\n\"","d":[true,false,null],"e":{"f":{}},"g":0,"h":12345678901234}"""
        )
        assertEquals(1L,value["a"])
        assertEquals(-25.0,value["b"])
        assertEquals("xä\n\"",value["c"])
        assertEquals(listOf(true,false,null),value["d"])
        assertEquals(mapOf("f" to emptyMap<String,Any?>()),value["e"])
        assertEquals(0L,value["g"])
        assertEquals(12345678901234L,value["h"])
        assertTrue(value.containsKey("d"))
    }

    @Test fun surrogatePairsAreAccepted() {
        assertEquals("\uD83D\uDE00",StrictJson.parse("\"\\ud83d\\ude00\""))
    }

    @Test fun duplicateKeysAreRejectedEvenWhenNested() {
        for(text in listOf(
            """{"privacy":"PUBLIC","privacy":"PRIVATE"}""",
            """{"a":{"x":1,"x":2}}"""
        )) assertThrows(IllegalArgumentException::class.java) { StrictJson.parse(text) }
    }

    @Test fun lenientSyntaxIsRejected() {
        val invalid=listOf(
            "{'a':1}", "{a:1}", "{\"a\":1,}", "[1,]", "{\"a\":01}", "{\"a\":1} x",
            "{\"a\":1}/*c*/", "{\"a\":NaN}", "{\"a\":.5}", "{\"a\":1.}", "{\"a\":1e}",
            "{\"a\":\"\u0001\"}", "\"\\ud800\"", "\"\\udc00x\"", "\"\\x41\"", "\"abc",
            "tru", "nul", "", " ", "{\"a\" 1}", "{\"a\":+1}", "[1 2]"
        )
        invalid.forEach { text ->
            assertThrows("accepted: $text",IllegalArgumentException::class.java) { StrictJson.parse(text) }
        }
    }

    @Test fun depthAndSizeAreBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            StrictJson.parse("[".repeat(StrictJson.MAX_DEPTH+2)+"]".repeat(StrictJson.MAX_DEPTH+2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StrictJson.parse("\""+"a".repeat(StrictJson.MAX_INPUT_CHARS)+"\"")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StrictJson.parse("1"+"0".repeat(80))
        }
    }

    @Test fun typedAccessorsFailClosed() {
        val value=StrictJson.parseObject("""{"s":"x","n":5,"z":null,"d":1.5}""")
        assertEquals("x",value.requiredString("s"))
        assertEquals(5L,value.requiredLong("n",0L..10L))
        assertNull(value.optionalString("z"))
        assertNull(value.optionalLong("missing",0L..1L))
        assertThrows(IllegalArgumentException::class.java) { value.requiredLong("n",0L..4L) }
        assertThrows(IllegalArgumentException::class.java) { value.requiredLong("d",0L..4L) }
        assertThrows(IllegalArgumentException::class.java) { value.requiredString("n") }
        assertThrows(IllegalArgumentException::class.java) { value.requiredString("missing") }
    }
}
