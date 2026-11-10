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
    // Trending YouTube Music Catalog
    val youtubeTracks = listOf(
        Track(
            id = "yt_kesariya",
            title = "Kesariya",
            artist = "Arijit Singh, Pritam",
            album = "Brahmastra",
            durationMs = 268000,
            sourceUrl = "https://www.youtube.com/watch?v=BddP6PYo2gs",
            artworkUrl = "https://img.youtube.com/vi/BddP6PYo2gs/hqdefault.jpg",
            isOnline = true,
            genre = "Bollywood",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "BddP6PYo2gs"
        ),
        Track(
            id = "yt_chaleya",
            title = "Chaleya",
            artist = "Arijit Singh, Shilpa Rao",
            album = "Jawan",
            durationMs = 200000,
            sourceUrl = "https://www.youtube.com/watch?v=VAdGW7QDJUI",
            artworkUrl = "https://img.youtube.com/vi/VAdGW7QDJUI/hqdefault.jpg",
            isOnline = true,
            genre = "Bollywood",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "VAdGW7QDJUI"
        ),
        Track(
            id = "yt_mi_amor",
            title = "Mi Amor",
            artist = "Sharn, 408, The Paul",
            album = "Mi Amor EP",
            durationMs = 195000,
            sourceUrl = "https://www.youtube.com/watch?v=vIXPRy_vTqg",
            artworkUrl = "https://img.youtube.com/vi/vIXPRy_vTqg/hqdefault.jpg",
            isOnline = true,
            genre = "Punjabi",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "vIXPRy_vTqg"
        ),
        Track(
            id = "yt_softly",
            title = "Softly",
            artist = "Karan Aujla, Ikky",
            album = "Making Memories",
            durationMs = 155000,
            sourceUrl = "https://www.youtube.com/watch?v=cWMxCE2HTag",
            artworkUrl = "https://img.youtube.com/vi/cWMxCE2HTag/hqdefault.jpg",
            isOnline = true,
            genre = "Punjabi",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "cWMxCE2HTag"
        ),
        Track(
            id = "yt_brown_munde",
            title = "Brown Munde",
            artist = "AP Dhillon, Gurinder Gill",
            album = "Brown Munde Single",
            durationMs = 267000,
            sourceUrl = "https://www.youtube.com/watch?v=VNs_cCtdbPc",
            artworkUrl = "https://img.youtube.com/vi/VNs_cCtdbPc/hqdefault.jpg",
            isOnline = true,
            genre = "Punjabi",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "VNs_cCtdbPc"
        ),
        Track(
            id = "yt_kahani_suno",
            title = "Kahani Suno 2.0",
            artist = "Kaifi Khalil",
            album = "Kahani Suno",
            durationMs = 173000,
            sourceUrl = "https://www.youtube.com/watch?v=_XBVWlI8TsQ",
            artworkUrl = "https://img.youtube.com/vi/_XBVWlI8TsQ/hqdefault.jpg",
            isOnline = true,
            genre = "Acoustic",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "_XBVWlI8TsQ"
        ),
        Track(
            id = "yt_tu_hai_kahan",
            title = "Tu Hai Kahan",
            artist = "AUR, Raffey, Usama",
            album = "Tu Hai Kahan Single",
            durationMs = 263000,
            sourceUrl = "https://www.youtube.com/watch?v=AX6OrbgS8lI",
            artworkUrl = "https://img.youtube.com/vi/AX6OrbgS8lI/hqdefault.jpg",
            isOnline = true,
            genre = "Lo-Fi",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "AX6OrbgS8lI"
        ),
        Track(
            id = "yt_starboy",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            album = "Starboy",
            durationMs = 230000,
            sourceUrl = "https://www.youtube.com/watch?v=34Na4j8AVgA",
            artworkUrl = "https://img.youtube.com/vi/34Na4j8AVgA/hqdefault.jpg",
            isOnline = true,
            genre = "Pop",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "34Na4j8AVgA"
        ),
        Track(
            id = "yt_lofi_girl",
            title = "Lo-Fi Hip Hop Beats to Relax/Study",
            artist = "Lofi Girl",
            album = "Lofi Beats Collection",
            durationMs = 180000,
            sourceUrl = "https://www.youtube.com/watch?v=jfKfPfyJRdk",
            artworkUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            isOnline = true,
            genre = "Lo-Fi",
            bitRate = "YouTube HQ",
            isYouTube = true,
            youtubeVideoId = "jfKfPfyJRdk"
        )
    )

    // Direct MP3 Streaming tracks
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
        ),
        Track(
            id = "stream_3",
            title = "Quantum Bass Drive",
            artist = "Pulse X",
            album = "Hyperdrive Overload",
            durationMs = 245000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Bass Boost",
            bitRate = "320 kbps"
        ),
        Track(
            id = "stream_4",
            title = "Galactic Chillout",
            artist = "Starlight Sound",
            album = "Celestial Voyage",
            durationMs = 205000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Ambient",
            bitRate = "320 kbps"
        )
    )

    val bundledLocalDemo = listOf(
        Track(
            id = "local_demo_1",
            title = "Neon Boulevard",
            artist = "Akki Beat Resident",
            album = "Local Drive",
            durationMs = 210000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            isOnline = false,
            genre = "Electronic",
            bitRate = "320 kbps"
        ),
        Track(
            id = "local_demo_2",
            title = "Astral Serenade",
            artist = "Subwave Project",
            album = "Internal Flash",
            durationMs = 198000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            isOnline = false,
            genre = "Lo-Fi",
            bitRate = "320 kbps"
        )
    )
}
