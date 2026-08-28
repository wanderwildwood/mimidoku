package com.wanderwildwood.mimidoku.library

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream

/** A place inside one file that the reader would call a chapter. */
data class Mark(
    val title: String,
    val startMs: Long,
)

/**
 * Reads the chapter marks a file carries inside it.
 *
 * A twelve-hour novel delivered as one file is unusable without them: everything the transport
 * offers moves in twenty-second steps, and finding chapter nine means dragging a bar across half
 * an hour of audio per pixel. The marks are already in the file; this gets them out.
 *
 * Only ID3, which covers mp3. An m4b keeps its chapters in an MP4 atom and is not handled here.
 */
object ChapterMarks {

    fun read(resolver: ContentResolver, uri: Uri): List<Mark> = runCatching {
        resolver.openInputStream(uri)?.use { parse(it) }.orEmpty()
    }.getOrDefault(emptyList())

    private fun parse(stream: InputStream): List<Mark> {
        val header = stream.readAtMost(HEADER)
        if (header.size < HEADER || header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()
        ) {
            return emptyList()
        }

        val version = header[3].toInt()
        val flags = header[5].toInt()
        // Unsynchronisation rewrites the bytes of the whole tag, and nothing that writes chapters
        // also sets it. Reading it wrong would invent chapters, so it is not read at all.
        if (flags and 0x80 != 0) return emptyList()

        val size = syncSafe(header, 6)
        if (size <= 0 || size > MOST) return emptyList()
        var body = stream.readAtMost(size)

        // An extended header sits before the frames and says how long it is.
        if (flags and 0x40 != 0 && body.size > 4) {
            val extended = if (version >= 4) syncSafe(body, 0) else beInt(body, 0) + 4
            if (extended in 1..body.size) body = body.copyOfRange(extended, body.size)
        }

        return frames(body, version)
            .mapNotNull { (id, frame) -> if (id == "CHAP") chapter(frame, version) else null }
            .sortedBy { it.startMs }
    }

    /** A chapter frame: an id, four times, and the ordinary frames that name it. */
    private fun chapter(frame: ByteArray, version: Int): Mark? {
        var at = 0
        while (at < frame.size && frame[at] != 0.toByte()) at++
        at++
        if (at + 16 > frame.size) return null
        val startMs = beInt(frame, at).toLong()
        val title = frames(frame.copyOfRange(at + 16, frame.size), version)
            .firstOrNull { it.first == "TIT2" }
            ?.second
            ?.let { text(it) }
            .orEmpty()
        return Mark(title.ifBlank { "Chapter" }, startMs)
    }

    private fun frames(body: ByteArray, version: Int): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        var at = 0
        while (at + 10 <= body.size) {
            val id = String(body, at, 4, Charsets.ISO_8859_1)
            // Padding at the end of a tag is zero bytes, which is where the frames stop.
            if (id.isBlank() || id[0] == ' ') break
            // 2.4 writes frame sizes syncsafe and 2.3 writes them plainly; a 2.3 file read as 2.4
            // yields sizes that walk off the end, so the version has to be honoured.
            val size = if (version >= 4) syncSafe(body, at + 4) else beInt(body, at + 4)
            val from = at + 10
            if (size <= 0 || from + size > body.size) break
            out += id to body.copyOfRange(from, from + size)
            at = from + size
        }
        return out
    }

    /** A text frame is an encoding byte and then the text in that encoding. */
    private fun text(frame: ByteArray): String? {
        if (frame.isEmpty()) return null
        val rest = frame.copyOfRange(1, frame.size)
        val charset = when (frame[0].toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(rest, charset).trim { it <= ' ' }
    }

    private fun syncSafe(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0x7f) shl 21) or
            ((bytes[at + 1].toInt() and 0x7f) shl 14) or
            ((bytes[at + 2].toInt() and 0x7f) shl 7) or
            (bytes[at + 3].toInt() and 0x7f)

    private fun beInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xff) shl 24) or
            ((bytes[at + 1].toInt() and 0xff) shl 16) or
            ((bytes[at + 2].toInt() and 0xff) shl 8) or
            (bytes[at + 3].toInt() and 0xff)

    private fun InputStream.readAtMost(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = read(buffer, filled, count - filled)
            if (read <= 0) break
            filled += read
        }
        return if (filled == count) buffer else buffer.copyOf(filled)
    }

    private const val HEADER = 10

    /**
     * A tag holding chapters and cover art runs to a few hundred kilobytes. A claimed size far
     * past that is a corrupt header, and reading it would pull megabytes off the card for nothing.
     */
    private const val MOST = 4 * 1024 * 1024
}
