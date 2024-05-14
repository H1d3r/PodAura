package com.skyd.anivu.ui.mpv

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_FLAG
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_INT64
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_NONE
import `is`.xyz.mpv.MPVLib.mpvFormat.MPV_FORMAT_STRING
import `is`.xyz.mpv.R
import kotlin.math.log
import kotlin.reflect.KProperty

class MPVView(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs),
    SurfaceHolder.Callback {
    private var surfaceCreated = false

    fun initialize(configDir: String, cacheDir: String, logLvl: String = "v", vo: String = "gpu") {
        MPVLib.create(this.context, logLvl)
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)
        initOptions(vo) // do this before init() so user-supplied config can override our choices
        MPVLib.init()
        /* Hardcoded options: */
        // we need to call write-watch-later manually
        MPVLib.setOptionString("save-position-on-quit", "no")
        // would crash before the surface is attached
        MPVLib.setOptionString("force-window", "no")
        // "no" wouldn't work and "yes" is not intended by the UI
        MPVLib.setOptionString("idle", "yes")

        holder.addCallback(this)
        observeProperties()
    }

    private var voInUse: String = ""

    private fun initOptions(vo: String) {
        // apply phone-optimized defaults
        MPVLib.setOptionString("profile", "fast")

        // vo
        voInUse = vo

        // hwdec
        val hwdec = "auto"//"no"

        // vo: set display fps as reported by android
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val disp = wm.defaultDisplay
        val refreshRate = disp.mode.refreshRate

        Log.v(TAG, "Display ${disp.displayId} reports FPS of $refreshRate")
        MPVLib.setOptionString("display-fps-override", refreshRate.toString())
        MPVLib.setOptionString("video-sync", "audio")

        MPVLib.setOptionString("vo", vo)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", hwdec)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("input-default-bindings", "yes")
        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
        //
        val screenshotDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        MPVLib.setOptionString("screenshot-directory", screenshotDir.path)
    }

    private var filePath: String? = null

    // Called when back button is pressed, or app is shutting down
    fun destroy() {
        // Disable surface callbacks to avoid using unintialized mpv state
        holder.removeCallback(this)

        MPVLib.destroy()
    }

    private fun observeProperties() {
        // This observes all properties needed by MPVView, MPVActivity or other classes
        data class Property(val name: String, val format: Int = MPV_FORMAT_NONE)

        val p = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("duration", MPV_FORMAT_INT64),
            Property("demuxer-cache-time", MPV_FORMAT_INT64),
            Property("video-rotate", MPV_FORMAT_INT64),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("seeking", MPV_FORMAT_FLAG),
            Property("pause", MPV_FORMAT_FLAG),
            Property("eof-reached", MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("track-list"),
            // observing double properties is not hooked up in the JNI code, but doing this
            // will restrict updates to when it actually changes
            Property("video-zoom", MPV_FORMAT_DOUBLE),
            Property("video-params/aspect", MPV_FORMAT_DOUBLE),
            Property("video-pan-x", MPV_FORMAT_DOUBLE),
            Property("video-pan-y", MPV_FORMAT_DOUBLE),
            Property("speed", MPV_FORMAT_DOUBLE),
            Property("playlist-pos", MPV_FORMAT_INT64),
            Property("playlist-count", MPV_FORMAT_INT64),
            Property("video-format"),
            Property("media-title", MPV_FORMAT_STRING),
            Property("metadata/by-key/Artist", MPV_FORMAT_STRING),
            Property("metadata/by-key/Album", MPV_FORMAT_STRING),
            Property("loop-playlist"),
            Property("loop-file"),
            Property("shuffle", MPV_FORMAT_FLAG),
            Property("hwdec-current")
        )

        for ((name, format) in p) {
            MPVLib.observeProperty(name, format)
        }
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }

    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    data class Track(val mpvId: Int, val name: String)

    var tracks = mapOf<String, MutableList<Track>>(
        "audio" to arrayListOf(),
        "video" to arrayListOf(),
        "sub" to arrayListOf()
    )

    fun loadTracks() {
        for (list in tracks.values) {
            list.clear()
            // pseudo-track to allow disabling audio/subs
            list.add(Track(-1, context.getString(R.string.track_off)))
        }
        val count = MPVLib.getPropertyInt("track-list/count")!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!tracks.containsKey(type)) {
                Log.w(TAG, "Got unknown track type: $type")
                continue
            }
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val title = MPVLib.getPropertyString("track-list/$i/title")

            val trackName = if (!lang.isNullOrEmpty() && !title.isNullOrEmpty())
                context.getString(R.string.ui_track_title_lang, mpvId, title, lang)
            else if (!lang.isNullOrEmpty() || !title.isNullOrEmpty())
                context.getString(R.string.ui_track_text, mpvId, (lang ?: "") + (title ?: ""))
            else
                context.getString(R.string.ui_track, mpvId)
            tracks.getValue(type).add(
                Track(
                    mpvId = mpvId,
                    name = trackName
                )
            )
        }
    }

    data class PlaylistItem(val index: Int, val filename: String, val title: String?)

    fun loadPlaylist(): MutableList<PlaylistItem> {
        val playlist = mutableListOf<PlaylistItem>()
        val count = MPVLib.getPropertyInt("playlist-count")!!
        for (i in 0 until count) {
            val filename = MPVLib.getPropertyString("playlist/$i/filename")!!
            val title = MPVLib.getPropertyString("playlist/$i/title")
            playlist.add(PlaylistItem(index = i, filename = filename, title = title))
        }
        return playlist
    }

    data class Chapter(val index: Int, val title: String?, val time: Double)

    fun loadChapters(): MutableList<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val count = MPVLib.getPropertyInt("chapter-list/count")!!
        for (i in 0 until count) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title")
            val time = MPVLib.getPropertyDouble("chapter-list/$i/time")!!
            chapters.add(
                Chapter(
                    index = i,
                    title = title,
                    time = time
                )
            )
        }
        return chapters
    }

    // Property getters/setters
    var paused: Boolean
        get() = MPVLib.getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused)
    val isIdling: Boolean
        get() = MPVLib.getPropertyBoolean("idle-active")
    val eofReached: Boolean
        get() = MPVLib.getPropertyBoolean("eof-reached")
    val keepOpen: Boolean
        get() = MPVLib.getPropertyBoolean("keep-open")

    val duration: Int?
        get() = MPVLib.getPropertyInt("duration")

    var timePos: Int
        get() = MPVLib.getPropertyInt("time-pos")
        set(progress) = MPVLib.setPropertyInt("time-pos", progress)

    val hwdecActive: String
        get() = MPVLib.getPropertyString("hwdec-current") ?: "no"

    var playbackSpeed: Double
        get() = MPVLib.getPropertyDouble("speed")
        set(speed) = MPVLib.setPropertyDouble("speed", speed)

    val videoRotate: Int
        get() = MPVLib.getPropertyInt("video-rotate")

    val videoZoom: Double
        get() = MPVLib.getPropertyDouble("video-zoom")

    val videoPanX: Double
        get() = MPVLib.getPropertyDouble("video-pan-x")

    val videoPanY: Double
        get() = MPVLib.getPropertyDouble("video-pan-y")

    val estimatedVfFps: Double?
        get() = MPVLib.getPropertyDouble("estimated-vf-fps")

    val videoW: Int?
        get() = MPVLib.getPropertyInt("video-params/w")

    val videoH: Int?
        get() = MPVLib.getPropertyInt("video-params/h")

    val videoDW: Int?
        get() = MPVLib.getPropertyInt("video-params/dw")

    val videoDH: Int?
        get() = MPVLib.getPropertyInt("video-params/dh")

    val videoAspect: Double?
        get() = MPVLib.getPropertyDouble("video-params/aspect")

    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.setPropertyString(name, "no")
            else
                MPVLib.setPropertyInt(name, value)
        }
    }

    var vid: Int by TrackDelegate("vid")
    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    // Commands

    fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))
    fun cycleAudio() = MPVLib.command(arrayOf("cycle", "audio"))
    fun cycleSub() = MPVLib.command(arrayOf("cycle", "sub"))
    fun cycleHwdec() = MPVLib.command(arrayOf("cycle-values", "hwdec", "auto", "no"))

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    fun getRepeat(): Int {
        return when (MPVLib.getPropertyString("loop-playlist") +
                MPVLib.getPropertyString("loop-file")) {
            "noinf" -> 2
            "infno" -> 1
            else -> 0
        }
    }

    fun cycleRepeat() {
        when (val state = getRepeat()) {
            0, 1 -> {
                MPVLib.setPropertyString("loop-playlist", if (state == 1) "no" else "inf")
                MPVLib.setPropertyString("loop-file", if (state == 1) "inf" else "no")
            }

            2 -> MPVLib.setPropertyString("loop-file", "no")
        }
    }

    fun getShuffle(): Boolean {
        return MPVLib.getPropertyBoolean("shuffle")
    }

    fun changeShuffle(cycle: Boolean, value: Boolean = true) {
        // Use the 'shuffle' property to store the shuffled state, since changing
        // it at runtime doesn't do anything.
        val state = getShuffle()
        val newState = if (cycle) state.xor(value) else value
        if (state == newState)
            return
        MPVLib.command(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
        MPVLib.setPropertyBoolean("shuffle", newState)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")

        surfaceCreated = true
        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
    }

    fun loadFile(filePath: String) {
        if (surfaceCreated) MPVLib.command(arrayOf("loadfile", filePath))
        else this.filePath = filePath
    }

    fun stop() {
        MPVLib.command(arrayOf("stop"))
    }

    fun seek(position: Int, precise: Boolean = false) {
        if (precise) {
            timePos = position
        } else {
            // seek faster than assigning to timePos but less precise
            MPVLib.command(arrayOf("seek", position.toString(), "absolute+keyframes"))
        }
    }

    fun zoom(value: Float) {
        MPVLib.setOptionString("video-zoom", log(value, 2f).toString())
    }

    fun rotate(value: Int) {
        var scaledValue = value % 360
        scaledValue = if (scaledValue >= 0) scaledValue else scaledValue + 360
        MPVLib.setOptionString("video-rotate", scaledValue.toString())
    }

    fun offset(x: Int, y: Int) {
        val dw = videoDW
        val dh = videoDH
        if (dw == null || dh == null) return
        MPVLib.setOptionString("video-pan-x", (x.toFloat() / dw).toString())
        MPVLib.setOptionString("video-pan-y", (y.toFloat() / dh).toString())
    }

    fun playMediaAtIndex(index: Int? = null) {
        when (index) {
            null -> MPVLib.command(arrayOf("playlist-play-index", "none"))
            -1 -> MPVLib.command(arrayOf("playlist-play-index", "current"))
            else -> MPVLib.command(arrayOf("playlist-play-index", index.toString()))
        }
    }

    companion object {
        private const val TAG = "mpv"
    }
}
