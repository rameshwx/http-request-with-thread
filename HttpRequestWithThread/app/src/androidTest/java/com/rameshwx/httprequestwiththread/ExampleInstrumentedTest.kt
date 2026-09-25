package com.rameshwx.httprequestwiththread

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Base64
import com.rameshwx.httprequestwiththread.data.image.AndroidProductImageDecoder

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.rameshwx.httprequestwiththread", appContext.packageName)
    }

    @Test
    fun decodesBinaryPngImageResponse() {
        val png = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADElEQVR4nGNgYGAAAAAEAAH2FzhVAAAAAElFTkSuQmCC",
            Base64.DEFAULT
        )

        val bitmap = AndroidProductImageDecoder().decode(png)

        assertNotNull(bitmap)
        assertEquals(1, bitmap!!.width)
        assertEquals(1, bitmap.height)
        bitmap.recycle()
    }
}
