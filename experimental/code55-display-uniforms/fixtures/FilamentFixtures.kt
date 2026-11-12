package com.google.android.filament

import android.hardware.HardwareBuffer
import android.os.Handler

class Engine {
    val events = mutableListOf<String>()
    val releases = ArrayDeque<Runnable>()
    var streamCount = 0
    var textureCount = 0
    var failNextImport = false
    fun destroyTexture(texture: Texture) { events += "destroy-texture:${texture.id}" }
    fun destroyStream(stream: Stream) { events += "destroy-stream:${stream.id}" }
    fun flushAndWait() { events += "flush" }
    fun releaseOne() { releases.removeFirst().run() }
    fun releaseAll() { while (releases.isNotEmpty()) releaseOne() }
}

class Stream(private val engine: Engine, val id: Int) {
    fun setDimensions(width: Int, height: Int) { engine.events += "dimensions:$id:$width:$height" }
    fun setAcquiredImage(buffer: HardwareBuffer, handler: Handler, release: Runnable) {
        check(!buffer.isClosed)
        if (engine.failNextImport) { engine.failNextImport = false; throw IllegalStateException("IMPORT_FAILURE") }
        engine.events += "import:$id:${buffer.image}"
        engine.releases += release
    }
    class Builder {
        fun width(width: Int) = this
        fun height(height: Int) = this
        fun build(engine: Engine) = Stream(engine, ++engine.streamCount)
    }
}

class Texture(val id: Int) {
    enum class Sampler { SAMPLER_EXTERNAL }
    enum class InternalFormat { RGB8 }
    fun setExternalStream(engine: Engine, stream: Stream) { engine.events += "external:$id:${stream.id}" }
    class Builder {
        fun sampler(sampler: Sampler) = this
        fun format(format: InternalFormat) = this
        fun width(width: Int) = this
        fun height(height: Int) = this
        fun levels(levels: Int) = this
        fun build(engine: Engine) = Texture(++engine.textureCount)
    }
}

class TextureSampler(min: MinFilter, mag: MagFilter, wrap: WrapMode) {
    enum class MinFilter { LINEAR }
    enum class MagFilter { LINEAR }
    enum class WrapMode { CLAMP_TO_EDGE }
}

class MaterialInstance {
    val values = sortedMapOf<String, String>()
    val writes = mutableListOf<Pair<String, String>>()
    var failNext: String? = null
    var failAfterWrite: String? = null
    private fun write(name: String, value: String) {
        if (failNext == name) { failNext = null; throw IllegalStateException("MATERIAL_FAILURE:$name") }
        writes += name to value
        values[name] = value
        if (failAfterWrite == name) { failAfterWrite = null; throw IllegalStateException("PARTIAL_WRITE:$name") }
    }
    fun setParameter(name: String, value: Boolean) = write(name, value.toString())
    fun setParameter(name: String, value: Float) = write(name, value.toString())
    fun setParameter(name: String, x: Float, y: Float) = write(name, "$x,$y")
    fun setParameter(name: String, x: Float, y: Float, z: Float, w: Float) = write(name, "$x,$y,$z,$w")
    fun setParameter(name: String, texture: Texture, sampler: TextureSampler) = write(name, "texture:${texture.id}")
}
