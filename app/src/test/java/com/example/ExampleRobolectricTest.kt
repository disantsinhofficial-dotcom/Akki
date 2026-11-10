package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DefaultTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Akki Beat", appName)
  }

  @Test
  fun `verify default streaming tracks catalog`() {
    val tracks = DefaultTracks.onlineTracks
    assertTrue(tracks.isNotEmpty())
    assertTrue(tracks.any { it.genre == "Synthwave" })
  }
}
