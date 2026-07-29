package com.splitcruiser.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun useAppContext() {
    // Context of the app under test. This asserted "com.example" — the template's package, not
    // this app's — so it could only ever have passed by never being run.
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.splitcruiser.app", appContext.packageName)
  }
}
