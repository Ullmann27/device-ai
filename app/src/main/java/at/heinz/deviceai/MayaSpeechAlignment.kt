package at.heinz.deviceai

import kotlin.math.roundToInt

internal object MayaSpeechAlignment {
    data class Range(val start: Int, val end: Int, val startFrame: Int, val endFrame: Int)
    private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}'’\\-]*")

    fun ranges(text: String, characters: List<String>, starts: List<Double>, ends: List<Double>,
               frameCount: Int): List<Range> {
        if (text.length !in 1..800 || characters.isEmpty() || characters.size > text.length ||
            starts.size != characters.size || ends.size != characters.size || frameCount <= 0 ||
            characters.any { it.isEmpty() || it.codePointCount(0, it.length) != 1 } ||
            characters.joinToString("") != text) return emptyList()
        val firstFrames = IntArray(text.length)
        val lastFrames = IntArray(text.length)
        var offset = 0
        var previousStart = 0
        var previousEnd = 0
        for (index in characters.indices) {
            val start = starts[index]
            val end = ends[index]
            if (!start.isFinite() || !end.isFinite() || start < 0 || end < start ||
                end > frameCount.toDouble() / MayaSpeech.SAMPLE_RATE + 0.5 / MayaSpeech.SAMPLE_RATE) return emptyList()
            val startFrame = (start * MayaSpeech.SAMPLE_RATE).roundToInt()
            val endFrame = (end * MayaSpeech.SAMPLE_RATE).roundToInt()
            if (startFrame < previousStart || endFrame < previousEnd || endFrame > frameCount) return emptyList()
            repeat(characters[index].length) {
                firstFrames[offset] = startFrame
                lastFrames[offset++] = endFrame
            }
            previousStart = startFrame
            previousEnd = endFrame
        }
        return word.findAll(text).mapNotNull { match ->
            val start = match.range.first
            val end = match.range.last + 1
            Range(start, end, firstFrames[start], lastFrames[end - 1])
                .takeIf { it.endFrame > it.startFrame }
        }.toList()
    }

    fun validated(text: String, frameCount: Int, ranges: List<Range>): List<Range> {
        var end = 0
        var frame = 0
        for (range in ranges) {
            if (range.start < end || range.end <= range.start || range.end > text.length ||
                range.startFrame < frame || range.endFrame <= range.startFrame ||
                range.endFrame > frameCount) return emptyList()
            end = range.end
            frame = range.startFrame
        }
        return ranges.toList()
    }

    class Cursor(private val ranges: List<Range>) {
        private var index = 0
        private var previousFrame = -1
        fun advance(frame: Int): Range? {
            if (frame < 0 || frame < previousFrame) return null
            previousFrame = frame
            while (index < ranges.size && ranges[index].endFrame <= frame) index++
            if (index >= ranges.size || ranges[index].startFrame > frame) return null
            return ranges[index++]
        }
    }
}
