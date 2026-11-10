package com.example

import com.example.model.DefaultTracks
import com.example.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testTrackDurationFormatting() {
    val track = Track(
      id = "test_1",
      title = "Test Song",
      artist = "Test Artist",
      album = "Test Album",
      durationMs = 195000,
      sourceUrl = "https://example.com/audio.mp3"
    )
    assertEquals("3:15", track.durationFormatted)
  }

  @Test
  fun testDefaultTracksCatalogNotEmpty() {
    assertTrue(DefaultTracks.onlineTracks.size >= 4)
    assertTrue(DefaultTracks.bundledLocalDemo.isNotEmpty())
  }
}
