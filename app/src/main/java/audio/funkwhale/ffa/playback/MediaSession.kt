package audio.funkwhale.ffa.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import audio.funkwhale.ffa.model.Radio
import audio.funkwhale.ffa.repositories.AlbumsRepository
import audio.funkwhale.ffa.repositories.ArtistTracksRepository
import audio.funkwhale.ffa.repositories.PlaylistTracksRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.repositories.TracksRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MediaSession(private val context: Context) {

  var active = false
  private val scope = CoroutineScope(Job() + Main)

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
    }
  }

  val connector: MediaSessionConnector by lazy {
    MediaSessionConnector(session).also {
      it.setQueueNavigator(FFAQueueNavigator())

      it.setPlaybackPreparer(FFAPlaybackPreparer(context, this, scope))

      it.setMediaButtonEventHandler { _, intent ->
        if (!active) {
          ensureServiceStarted()

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

  fun ensureServiceStarted() {
    if (!active) {
      Intent(context, PlayerService::class.java).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(it)
        } else {
          context.startService(it)
        }
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

class FFAPlaybackPreparer(private val context: Context, private val mediaSession: MediaSession, private val scope: CoroutineScope) : MediaSessionConnector.PlaybackPreparer {
  override fun onCommand(player: Player, command: String, extras: Bundle?, cb: ResultReceiver?) = false

  override fun getSupportedPrepareActions(): Long {
    return PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
      PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
  }

  override fun onPrepare(playWhenReady: Boolean) {
    mediaSession.ensureServiceStarted()
  }

  override fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?) {
    mediaSession.ensureServiceStarted()

    scope.launch {
      when {
        mediaId.startsWith("radio_") -> {
          val id = mediaId.removePrefix("radio_").toInt()
          CommandBus.send(Command.PlayRadio(Radio(id, "custom", "", "")))
        }
        mediaId.contains("_track_") -> {
          val parts = mediaId.split("_")
          val type = parts[0]
          val containerId = parts[1].toInt()
          val trackId = parts[3].toInt()

          val tracks = when (type) {
            "album" -> {
              val repository = TracksRepository(context, containerId)
              repository.fetch(Repository.Origin.Cache.origin).first().data
            }
            "playlist" -> {
              val repository = PlaylistTracksRepository(context, containerId)
              repository.fetch(Repository.Origin.Cache.origin).first().data.map { it.track }
            }
            "artist" -> {
              val repository = ArtistTracksRepository(context, containerId)
              repository.fetch(Repository.Origin.Cache.origin).first().data
            }
            "queue" -> {
              val channel = RequestBus.send(Request.GetQueue)
              val response = channel.receive()
              val q = if (response is Response.Queue) response.queue else listOf()
              channel.close()
              q
            }
            else -> listOf()
          }

          if (tracks.isNotEmpty()) {
            if (type != "queue") {
              CommandBus.send(Command.ReplaceQueue(tracks))
            }
            val index = tracks.indexOfFirst { it.id == trackId }
            if (index != -1) {
              CommandBus.send(Command.PlayTrack(index))
            }
          }
        }
      }
    }
  }

  override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {
    mediaSession.ensureServiceStarted()
  }

  override fun onPrepareFromUri(uri: android.net.Uri, playWhenReady: Boolean, extras: Bundle?) {
    mediaSession.ensureServiceStarted()
  }
}
