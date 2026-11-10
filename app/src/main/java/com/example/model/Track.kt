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
    val bitRate: String = "320 kbps"
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

object DefaultTracks {
    val onlineTracks = listOf(
        Track(
            id = "stream_1",
            title = "Cyber Horizon",
            artist = "Neon Dreamer",
            album = "Retrowave 2099",
            durationMs = 214000,
            // High-reliability public domain audio stream
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
        ),
        Track(
            id = "stream_5",
            title = "Electro Odyssey",
            artist = "Volt Master",
            album = "Arcade Genesis",
            durationMs = 230000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Electronic",
            bitRate = "320 kbps"
        ),
        Track(
            id = "stream_6",
            title = "Vocal Velvet Groove",
            artist = "Luna Sky",
            album = "Nightfall Sessions",
            durationMs = 192000,
            sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            isOnline = true,
            genre = "Pop",
            bitRate = "256 kbps"
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
