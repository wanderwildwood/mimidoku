package com.wanderwildwood.mimidoku.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the two kinds of file say about their own chapters.
 *
 * Written against bytes rather than against a book, because the files this has to survive are the
 * ones nobody here can produce on purpose: a chapter list written the older way, a length that
 * claims more than the file holds, a book that is thirteen hours of audio and must not be read
 * through to find nine hundred bytes at the end of it.
 */
class ChapterMarksTest {

    @Test
    fun `an m4b chapter list is read`() {
        val marks = ChapterMarks.read(
            m4b(chpl(version = 1, chapters = listOf(0L to "Chapter One", 363_214L to "Chapter Two"))),
        )

        assertEquals(listOf("Chapter One", "Chapter Two"), marks.map { it.title })
        assertEquals(listOf(0L, 363_214L), marks.map { it.startMs })
    }

    @Test
    fun `a chapter list written the older way is read`() {
        val marks = ChapterMarks.read(m4b(chpl(version = 0, chapters = listOf(0L to "One", 5_000L to "Two"))))

        assertEquals(listOf("One", "Two"), marks.map { it.title })
        assertEquals(listOf(0L, 5_000L), marks.map { it.startMs })
    }

    @Test
    fun `the audio is stepped over rather than read`() {
        // The audio in front of the chapter list rather than behind it, which is where some
        // writers put it. The stream counts what is read: a parser that walked the audio to get
        // past it would read all of this, and over a real book that is three hundred megabytes.
        val counted = Counting(
            m4b(
                chpl(version = 1, chapters = listOf(0L to "One", 5_000L to "Two")),
                audioBytes = 4 * 1024 * 1024,
                audioFirst = true,
            ),
        )

        val marks = ChapterMarks.read(counted)

        assertEquals(2, marks.size)
        assertTrue("read ${counted.read} bytes", counted.read < 64 * 1024)
    }

    @Test
    fun `a chapter titled with its own number is named`() {
        val marks = ChapterMarks.read(m4b(chpl(version = 1, chapters = listOf(0L to "001", 60_000L to "002"))))

        assertEquals(listOf("Chapter 1", "Chapter 2"), marks.map { it.title })
    }

    @Test
    fun `a chapter with no title at all is named for where it falls`() {
        val marks = ChapterMarks.read(m4b(chpl(version = 1, chapters = listOf(0L to "", 60_000L to ""))))

        assertEquals(listOf("Chapter 1", "Chapter 2"), marks.map { it.title })
    }

    @Test
    fun `a chapter list that runs off the end is not read`() {
        val truncated = chpl(version = 1, chapters = listOf(0L to "One", 5_000L to "Two"))
            .let { it.copyOf(it.size - 3) }
            // The box header has to keep agreeing with what follows it, or the walk stops before
            // the list is ever parsed and this would pass without testing anything.
            .also { writeInt(it, 0, it.size) }

        assertEquals(emptyList<Mark>(), ChapterMarks.read(m4b(truncated)))
    }

    @Test
    fun `a file that is neither is left alone`() {
        val counted = Counting(ByteArray(4096) { 0x7f })

        assertEquals(emptyList<Mark>(), ChapterMarks.read(counted))
        assertTrue("read ${counted.read} bytes", counted.read <= 16)
    }

    @Test
    fun `an mp3 says where its chapters are`() {
        val marks = ChapterMarks.read(
            id3(listOf(0L to "The Ferry", 90_000L to "The Flood")),
        )

        assertEquals(listOf("The Ferry", "The Flood"), marks.map { it.title })
        assertEquals(listOf(0L, 90_000L), marks.map { it.startMs })
    }

    @Test
    fun `chapters are given back in the order they are heard`() {
        val marks = ChapterMarks.read(
            m4b(chpl(version = 1, chapters = listOf(0L to "One", 5_000L to "Two"))),
        )

        assertEquals(marks.sortedBy { it.startMs }, marks)
    }

    // ---- fixtures --------------------------------------------------------------------------

    private fun ChapterMarks.read(bytes: ByteArray): List<Mark> = read(ByteArrayInputStream(bytes))

    /** A file shaped like an m4b: the boxes an audiobook has, around the list under test. */
    private fun m4b(chpl: ByteArray, audioBytes: Int = 1024, audioFirst: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(box("ftyp", "isomiso2mp41".toByteArray()))
        val moov = box("moov", box("mvhd", ByteArray(100)) + box("udta", chpl))
        val mdat = box("mdat", ByteArray(audioBytes))
        if (audioFirst) {
            out.write(mdat)
            out.write(moov)
        } else {
            out.write(moov)
            out.write(mdat)
        }
        return out.toByteArray()
    }

    /**
     * A chapter list. Version 1 puts four bytes of nothing before the count, which is what tools
     * write today; version 0 does not, which is what the format said first.
     */
    private fun chpl(version: Int, chapters: List<Pair<Long, String>>): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf(version.toByte(), 0, 0, 0))
        if (version >= 1) body.write(ByteArray(4))
        body.write(chapters.size)
        for ((startMs, title) in chapters) {
            val bytes = title.toByteArray()
            body.write(long(startMs * 10_000))
            body.write(bytes.size)
            body.write(bytes)
        }
        return box("chpl", body.toByteArray())
    }

    /** An ID3v2.3 tag with a chapter frame for each chapter, which is how an mp3 carries them. */
    private fun id3(chapters: List<Pair<Long, String>>): ByteArray {
        val frames = ByteArrayOutputStream()
        for ((index, chapter) in chapters.withIndex()) {
            val (startMs, title) = chapter
            val body = ByteArrayOutputStream()
            body.write("ch$index".toByteArray())
            body.write(0)
            body.write(int(startMs.toInt()))
            body.write(int(0))
            body.write(int(0))
            body.write(int(0))
            val text = byteArrayOf(0) + title.toByteArray(Charsets.ISO_8859_1)
            body.write("TIT2".toByteArray())
            body.write(int(text.size))
            body.write(ByteArray(2))
            body.write(text)
            val chap = body.toByteArray()
            frames.write("CHAP".toByteArray())
            frames.write(int(chap.size))
            frames.write(ByteArray(2))
            frames.write(chap)
        }
        val body = frames.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray())
        out.write(byteArrayOf(3, 0, 0))
        out.write(syncSafe(body.size))
        out.write(body)
        return out.toByteArray()
    }

    private fun box(type: String, contents: ByteArray): ByteArray =
        header(type, (contents.size + 8).toLong()) + contents

    private fun header(type: String, size: Long): ByteArray =
        int(size.toInt()) + type.toByteArray(Charsets.ISO_8859_1)

    private fun int(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun long(value: Long) = ByteArray(8) { (value ushr (56 - it * 8)).toByte() }

    private fun syncSafe(value: Int) = byteArrayOf(
        ((value ushr 21) and 0x7f).toByte(),
        ((value ushr 14) and 0x7f).toByte(),
        ((value ushr 7) and 0x7f).toByte(),
        (value and 0x7f).toByte(),
    )

    private fun writeInt(bytes: ByteArray, at: Int, value: Int) {
        int(value).copyInto(bytes, at)
    }

    /** A stream that remembers how much of itself was actually read. */
    private class Counting(bytes: ByteArray) : InputStream() {
        private val inner = ByteArrayInputStream(bytes)
        var read = 0L
            private set

        override fun read(): Int = inner.read().also { if (it >= 0) read++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            inner.read(buffer, offset, length).also { if (it > 0) read += it }

        override fun skip(count: Long): Long = inner.skip(count)
    }
}
