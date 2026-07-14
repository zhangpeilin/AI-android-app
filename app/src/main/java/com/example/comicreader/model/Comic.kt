package com.example.comicreader.model

data class Comic(
    val id: String,
    val title: String,
    val artist: String = "Unknown",
    val filePath: String,
    val fileSize: Long = 0L,
    val pageCount: Int = 0,
    val coverImageBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Comic) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class Chapter(
    val number: String,
    val images: List<String>
)
