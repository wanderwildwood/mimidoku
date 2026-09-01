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
 * Two ways of writing them down, because the two kinds of file a book arrives as write them
 * differently: ID3 frames in an mp3, and a chapter list in an m4b.
 */
object ChapterMarks {

    fun read(resolver: ContentResolver, uri: Uri): List<Mark> = runCatching {
        resolver.openInputStream(uri)?.use { read(it) }.orEmpty()
    }.getOrDefault(emptyList())

    /**
     * Taken as bytes rather than as a file, so that what the parsers do with a header can be
     * asked of them directly.
     */
    fun read(stream: InputStream): List<Mark> {
        // Eight bytes is a box header, which is the shorter of the two things this can be
        // looking at; the other two bytes of an ID3 header are read once it is one.
        val head = stream.readAtMost(8)
        if (head.size < 8) return emptyList()
        val found = if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() &&
            head[2] == '3'.code.toByte()
        ) {
            val rest = stream.readAtMost(2)
            if (rest.size < 2) return emptyList()
            id3(stream, head + rest)
        } else {
            mp4(stream, head)
        }
        return found.sortedBy { it.startMs }.mapIndexed { index, mark -> mark.named(index) }
    }

    /**
     * What to call a chapter that the file did not name.
     *
     * Tools that cut a book into chapters title them "001", "002", which on the playback screen
     * reads as a serial number rather than as a place in a book. A title that is only its own
     * number is not a name, so it is given the one a reader would say out loud.
     */
    private fun Mark.named(index: Int): Mark {
        if (title.isNotEmpty() && !title.all { it.isDigit() }) return this
        return copy(title = "Chapter ${title.trimStart('0').toIntOrNull() ?: (index + 1)}")
    }

    // ---- mp3 -------------------------------------------------------------------------------

    private fun id3(stream: InputStream, header: ByteArray): List<Mark> {
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
        return Mark(title, startMs)
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

    // ---- m4b -------------------------------------------------------------------------------

    /**
     * The chapters an MP4 file — which is what an m4b is — keeps in a list of its own, at
     * moov/udta/chpl.
     *
     * A file can also carry its chapters as a track of text running alongside the audio. Reading
     * that means walking sample tables out into the middle of a three hundred megabyte file, and
     * every book that has arrived here carrying the text track carried this list as well.
     *
     * Nothing but the boxes on the way is read: the audio is stepped over rather than pulled off
     * the card, so a thirteen-hour book costs the same as a short one.
     */
    private fun mp4(stream: InputStream, first: ByteArray): List<Mark> {
        // A file that is neither an mp3 nor an MP4 - and a bare mp3 carrying no tag at all is
        // one - would otherwise be walked as though its first four bytes were a length, and
        // stepped through to the end for nothing. Every MP4 opens with one of a few boxes.
        if (String(first, 4, 4, Charsets.ISO_8859_1) !in OPENS) return emptyList()
        var head: ByteArray? = first
        while (true) {
            val box = box(stream, head) ?: return emptyList()
            head = null
            if (box.type == "moov") {
                return descend(stream, box.contents, listOf("udta", "chpl"))
                    ?.let { chapters(it) }
                    .orEmpty()
            }
            if (!stream.skipFully(box.contents)) return emptyList()
        }
    }

    /** A box, and how much of it was its own header. */
    private class Box(val type: String, val contents: Long, val header: Int)

    /** Four bytes of length, four of name, and for a big box eight more of length. */
    private fun box(stream: InputStream, read: ByteArray? = null): Box? {
        val head = read ?: stream.readAtMost(8).takeIf { it.size == 8 } ?: return null
        if (head.size < 8) return null
        val type = String(head, 4, 4, Charsets.ISO_8859_1)
        val declared = beInt(head, 0).toLong() and 0xffffffffL
        return when {
            // A length of one says the real length is the eight bytes that follow, which is how a
            // file gets past four gigabytes.
            declared == 1L -> {
                val extended = stream.readAtMost(8)
                if (extended.size < 8) return null
                val contents = beLong(extended, 0) - 16
                if (contents < 0) null else Box(type, contents, 16)
            }
            // A length of zero says the box runs to the end of the file, which only the audio
            // itself does — and there is nothing to look for past it.
            declared >= 8L -> Box(type, declared - 8, 8)
            else -> null
        }
    }

    /**
     * Walks the boxes inside one box looking for a path — udta, then chpl — and hands back the
     * contents of the one at the end of it.
     *
     * Reads exactly as far as the box goes when it finds nothing, so whoever called it can carry
     * on with the next one; when it finds something the walk stops there for good.
     */
    private fun descend(stream: InputStream, within: Long, path: List<String>): ByteArray? {
        var left = within
        while (left >= 8) {
            val box = box(stream) ?: return null
            left -= box.header
            if (box.contents < 0 || box.contents > left) return null
            if (box.type == path.first()) {
                if (path.size > 1) return descend(stream, box.contents, path.drop(1))
                if (box.contents > MOST) return null
                return stream.readAtMost(box.contents.toInt())
            }
            if (!stream.skipFully(box.contents)) return null
            left -= box.contents
        }
        return null
    }

    /**
     * A chapter list: how many there are, and then for each one when it starts and what it is
     * called. The times are in ten-millionths of a second, which is what this format counts in.
     *
     * The entries are preceded by a version, and by a few bytes that writers disagree about, so
     * rather than trust a version number the list is read from each place it could begin and the
     * reading that comes out exactly whole is the one used. A wrong start runs off the end of the
     * list, or back in time, within a chapter or two.
     */
    private fun chapters(chpl: ByteArray): List<Mark> =
        STARTS.firstNotNullOfOrNull { entries(chpl, it) }.orEmpty()

    private fun entries(chpl: ByteArray, from: Int): List<Mark>? {
        val out = mutableListOf<Mark>()
        var at = from
        while (at < chpl.size) {
            if (at + 9 > chpl.size) return null
            val startMs = beLong(chpl, at) / 10_000
            val length = chpl[at + 8].toInt() and 0xff
            if (startMs < 0 || at + 9 + length > chpl.size) return null
            out += Mark(String(chpl, at + 9, length, Charsets.UTF_8).trim { it <= ' ' }, startMs)
            at += 9 + length
        }
        return out.takeIf { list ->
            list.isNotEmpty() && list.zipWithNext().all { (before, after) -> before.startMs <= after.startMs }
        }
    }

    /**
     * Where the entries can begin: after a version, four bytes of nothing and a count, which is
     * what everything writes today, or after a version and a count, which is what the format says.
     */
    private val STARTS = listOf(9, 5)

    /** Steps over a box rather than reading it. Beside the audio, everything here is a rounding. */
    private fun InputStream.skipFully(count: Long): Boolean {
        var left = count
        val spare by lazy { ByteArray(8 * 1024) }
        while (left > 0) {
            val moved = skip(left)
            if (moved > 0) {
                left -= moved
                continue
            }
            // A stream that will not skip will still be read from, and reading it a byte at a
            // time to step over three hundred megabytes would take all afternoon.
            val read = read(spare, 0, minOf(left, spare.size.toLong()).toInt())
            if (read <= 0) return false
            left -= read
        }
        return true
    }

    /** What an MP4 file can begin with. */
    private val OPENS = setOf("ftyp", "moov", "free", "skip", "wide", "pnot", "mdat")

    // ---- bytes -----------------------------------------------------------------------------

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

    private fun beLong(bytes: ByteArray, at: Int): Long {
        var value = 0L
        for (index in at until at + 8) value = (value shl 8) or (bytes[index].toLong() and 0xff)
        return value
    }

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

    /**
     * A tag holding chapters and cover art runs to a few hundred kilobytes, and so does a chapter
     * list. A claimed size far past that is a corrupt header, and reading it would pull megabytes
     * off the card for nothing.
     */
    private const val MOST = 4 * 1024 * 1024
}
