package com.rameshwx.httprequestwiththread.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderFormValidationTest {
    @Test
    fun deliveryAddressUsesTrimmedEightToThreeHundredCharacterRule() {
        assertEquals(
            "Address must be at least 8 characters after trimming.",
            OrderFormValidation.deliveryAddressError(" 123456 ")
        )
        assertNull(OrderFormValidation.deliveryAddressError(" 12345678 "))
        assertNull(OrderFormValidation.deliveryAddressError("x".repeat(300)))
        assertEquals(
            "Address must be 300 characters or fewer.",
            OrderFormValidation.deliveryAddressError("x".repeat(301))
        )
    }
}
