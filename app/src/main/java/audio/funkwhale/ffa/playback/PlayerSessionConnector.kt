package audio.funkwhale.ffa.playback

import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player

/**
 * Bridges a Media3 [Player] with a [MediaSessionCompat], replacing the
 * now-removed ExoPlayer 2 MediaSessionConnector.
 */
class PlayerSessionConnector(private val mediaSession: MediaSessionCompat) {

  interface QueueNavigator {
    fun onSkipToQueueItem(player: Player, id: Long)
    fun onCommand(player: Player, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean
    fun getSupportedQueueNavigatorActions(player: Player): Long
    fun onSkipToNext(player: Player)
    fun getActiveQueueItemId(player: Player?): Long
    fun onSkipToPrevious(player: Player)
    fun onTimelineChanged(player: Player)
  }

  interface PlaybackPreparer {
    fun onCommand(player: Player, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean
    fun getSupportedPrepareActions(): Long
    fun onPrepare(playWhenReady: Boolean)
    fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?)
    fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?)
    fun onPrepareFromUri(uri: android.net.Uri, playWhenReady: Boolean, extras: Bundle?)
  }

  private var player: Player? = null
  private var queueNavigator: QueueNavigator? = null
  private var playbackPreparer: PlaybackPreparer? = null
  private var mediaMetadataProvider: ((Player) -> MediaMetadataCompat)? = null
  private var mediaButtonEventHandler: ((Player, Intent) -> Boolean)? = null
  private val sessionCallback = SessionCallback()

  fun setPlayer(player: Player) {
    this.player = player
    mediaSession.setCallback(sessionCallback)
  }

  fun setQueueNavigator(navigator: QueueNavigator) {
    this.queueNavigator = navigator
  }

  fun setPlaybackPreparer(preparer: PlaybackPreparer) {
    this.playbackPreparer = preparer
  }

  fun setMediaMetadataProvider(provider: (Player) -> MediaMetadataCompat) {
    this.mediaMetadataProvider = provider
  }

  fun setMediaButtonEventHandler(handler: (Player, Intent) -> Boolean) {
    this.mediaButtonEventHandler = handler
  }

  fun invalidateMediaSessionMetadata() {
    player?.let { p ->
      mediaMetadataProvider?.invoke(p)?.let { metadata ->
        mediaSession.setMetadata(metadata)
      }
    }
  }

  private inner class SessionCallback : MediaSessionCompat.Callback() {
    override fun onPlay() {
      player?.play()
    }

    override fun onPause() {
      player?.pause()
    }

    override fun onSkipToNext() {
      player?.let { queueNavigator?.onSkipToNext(it) }
    }

    override fun onSkipToPrevious() {
      player?.let { queueNavigator?.onSkipToPrevious(it) }
    }

    override fun onSkipToQueueItem(id: Long) {
      player?.let { queueNavigator?.onSkipToQueueItem(it, id) }
    }

    override fun onSeekTo(pos: Long) {
      player?.seekTo(pos)
    }

    override fun onPrepare() {
      playbackPreparer?.onPrepare(true)
    }

    override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
      mediaId?.let { playbackPreparer?.onPrepareFromMediaId(it, true, extras) }
    }

    override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
      query?.let { playbackPreparer?.onPrepareFromSearch(it, true, extras) }
    }

    override fun onPrepareFromUri(uri: android.net.Uri?, extras: Bundle?) {
      uri?.let { playbackPreparer?.onPrepareFromUri(it, true, extras) }
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
      if (mediaButtonEvent != null) {
        player?.let { p ->
          mediaButtonEventHandler?.let { handler ->
            if (handler(p, mediaButtonEvent)) return true
          }
        }
      }
      return super.onMediaButtonEvent(mediaButtonEvent)
    }
  }
}
