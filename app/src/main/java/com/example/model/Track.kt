package com.example.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sourceUrl: String,
    val artworkUrl: String? = null,
    val isOnline: Boolean = false,
    val genre: String = "Electronic",
    val bitRate: String = "320 kbps",
    val isYouTube: Boolean = false,
    val youtubeVideoId: String? = null
) {
    val durationFormatted: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

object DefaultTracks {
    val youtubeTracks = listOf(
        Track(
            id = "track_kesariya",
            title = "Kesariya",
            artist = "Arijit Singh, Pritam",
            album = "Brahmastra Hits",
            durationMs = 268000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Bollywood",
            bitRate = "320 kbps",
            isYouTube = false,
            youtubeVideoId = null
        ),
        Track(
            id = "track_chaleya",
            title = "Chaleya",
            artist = "Arijit Singh, Shilpa Rao",
            album = "Jawan Audio",
            durationMs = 200000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Bollywood",
            bitRate = "320 kbps",
            isYouTube = false,
            youtubeVideoId = null
        ),
        Track(
            id = "track_softly",
            title = "Softly",
            artist = "Karan Aujla, Ikky",
            album = "Making Memories",
            durationMs = 155000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Punjabi",
            bitRate = "320 kbps",
            isYouTube = false,
            youtubeVideoId = null
        ),
        Track(
            id = "track_brown_munde",
            title = "Brown Munde",
            artist = "AP Dhillon, Gurinder Gill",
            album = "Punjabi Bangers",
            durationMs = 267000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Punjabi",
            bitRate = "320 kbps",
            isYouTube = false,
            youtubeVideoId = null
        ),
        Track(
            id = "track_tu_hai_kahan",
            title = "Tu Hai Kahan",
            artist = "AUR, Raffey, Usama",
            album = "Lo-Fi Romance",
            durationMs = 263000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Lo-Fi",
            bitRate = "320 kbps",
            isYouTube = false,
            youtubeVideoId = null
        ),
        Track(
            id = "track_starboy",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            album = "Global Pop",
            durationMs = 230000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Pop",
            bitRate = "320 kbps",
            isYouTube = false,
            youtubeVideoId = null
        )
    )

    val onlineTracks = listOf(
        Track(
            id = "stream_1",
            title = "Cyber Horizon",
            artist = "Neon Dreamer",
            album = "Retrowave 2099",
            durationMs = 214000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Synthwave",
            bitRate = "320 kbps"
        ),
        Track(
            id = "stream_2",
            title = "Midnight Lo-Fi Echoes",
            artist = "Aura Beats",
            album = "Chill Tokyo Rain",
            durationMs = 188000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Lo-Fi",
            bitRate = "256 kbps"
        )
    )

    val bundledLocalDemo = emptyList<Track>()
}
