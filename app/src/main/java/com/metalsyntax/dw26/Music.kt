package com.metalsyntax.dw26

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.ido.ble.BLEManager
import com.ido.ble.protocol.model.MusicControlInfo
import com.ido.ble.protocol.model.PhoneVoice

/**
 * Envía al reloj la canción que suena en el teléfono (título, artista, estado, volumen).
 * Lee las sesiones multimedia gracias al permiso de acceso a notificaciones.
 */
object Music {
    private lateinit var ctx: Context
    private var controller: MediaController? = null
    private var last = ""
    private var started = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = send()
        override fun onPlaybackStateChanged(state: PlaybackState?) = send()
    }

    /** Idempotente: se reintenta al volver a la app, por si el permiso se dio después. */
    fun start(c: Context) {
        ctx = c.applicationContext
        if (started) return
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(ctx, NotifListener::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener({ attach(it) }, listener)
            attach(msm.getActiveSessions(listener))
            started = true
        } // sin permiso de notificaciones lanza SecurityException: la música queda apagada
    }

    private fun attach(sessions: List<MediaController>?) {
        val c = sessions?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions?.firstOrNull()
        if (c?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = c?.apply { registerCallback(callback) }
        send()
    }

    /** Al conectar: activar el modo música del reloj y mandar el estado actual. */
    fun onConnected() {
        if (!Watch.musicEnabled) return
        BLEManager.setMusicSwitch(true)
        BLEManager.enterMusicMode()
        last = ""
        send()
        val am = ctx.getSystemService(AudioManager::class.java)
        BLEManager.setPhoneVoice(PhoneVoice().apply {
            total_voice = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            now_voice = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        })
    }

    fun send() {
        if (!Watch.musicEnabled || !Watch.connected) return
        val c = controller ?: return
        val meta = c.metadata
        val state = c.playbackState
        val info = MusicControlInfo().apply {
            status = when (state?.state) {
                PlaybackState.STATE_PLAYING -> MusicControlInfo.STATUS_PLAY
                PlaybackState.STATE_PAUSED -> MusicControlInfo.STATUS_PAUSE
                PlaybackState.STATE_STOPPED -> MusicControlInfo.STATUS_STOP
                else -> MusicControlInfo.STATUS_INVALID
            }
            musicName = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            singerName = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            totalTimeSecond = ((meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0) / 1000).toInt()
            curTimeSecond = ((state?.position ?: 0) / 1000).toInt()
        }
        val key = "${info.status}|${info.musicName}|${info.singerName}"
        if (key == last) return // los reproductores avisan varias veces por segundo
        last = key
        BLEManager.setMusicControlInfo(info)
    }

    val nowPlaying: String?
        get() = controller?.metadata?.let { m ->
            listOfNotNull(m.getString(MediaMetadata.METADATA_KEY_TITLE), m.getString(MediaMetadata.METADATA_KEY_ARTIST)).joinToString(" · ").ifEmpty { null }
        }
}
