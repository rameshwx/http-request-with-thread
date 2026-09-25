package com.rameshwx.httprequestwiththread.data.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {
    @Test
    fun parsesNestedValuesAndEscapedStrings() {
        val json = MiniJson.parse("""{"name":"A \"fresh\" item","count":3,"active":true,"items":[null,1.25]}""") as JsonValue.Obj

        assertEquals("A \"fresh\" item", json.string("name"))
        assertEquals(3.0, json.number("count")!!, 0.0)
        assertTrue(json.bool("active")!!)
        assertEquals(2, (json["items"] as JsonValue.Arr).values.size)
    }

    @Test
    fun writesEscapedPayloadAndRoundTripsIt() {
        val value = MiniJson.obj(
            "text" to MiniJson.str("Bell\\ring\nnext"),
            "quantity" to MiniJson.num(2)
        )
        val encoded = MiniJson.stringify(value)

        assertEquals(value, MiniJson.parse(encoded))
        assertTrue(encoded.contains("\\n"))
    }
}
