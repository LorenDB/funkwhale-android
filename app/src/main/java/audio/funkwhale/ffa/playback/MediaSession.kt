package audio.funkwhale.ffa.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector

class MediaSession(private val context: Context) {

  var active = false

  private val playbackStateBuilder = PlaybackStateCompat.Builder().apply {
    setActions(
      PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_PLAY or
        PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
        PlaybackStateCompat.ACTION_SEEK_TO or
        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
    )
  }

  val session: MediaSessionCompat by lazy {
    MediaSessionCompat(context, context.packageName).apply {
      setPlaybackState(playbackStateBuilder.build())

      isActive = true
      active = true
    }
  }

  val connector: MediaSessionConnector by lazy {
    MediaSessionConnector(session).also {
      it.setQueueNavigator(FFAQueueNavigator())

      it.setMediaButtonEventHandler { _, intent ->
        if (!active) {
          Intent(context, PlayerService::class.java).let { player ->
            player.action = intent.action

            intent.extras?.let { extras -> player.putExtras(extras) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              context.startForegroundService(player)
            } else {
              context.startService(player)
            }
          }

          return@setMediaButtonEventHandler true
        }

        false
      }
    }
  }
}

class FFAQueueNavigator : MediaSessionConnector.QueueNavigator {
  override fun onSkipToQueueItem(player: Player, id: Long) {
    CommandBus.send(Command.PlayTrack(id.toInt()))
  }

  override fun onCommand(player: Player, command: String, extras: Bundle?, cb: ResultReceiver?) = true

  override fun getSupportedQueueNavigatorActions(player: Player): Long {
    return PlaybackStateCompat.ACTION_PLAY_PAUSE or
      PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
      PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
      PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
  }

  override fun onSkipToNext(player: Player) {
    CommandBus.send(Command.NextTrack)
  }

  override fun getActiveQueueItemId(player: Player?) = player?.currentMediaItemIndex?.toLong() ?: 0

  override fun onSkipToPrevious(player: Player) {
    CommandBus.send(Command.PreviousTrack)
  }

  override fun onTimelineChanged(player: Player) {}
}
