package audio.funkwhale.ffa.playback

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.FFACache
import audio.funkwhale.ffa.utils.HeadphonesUnpluggedReceiver
import audio.funkwhale.ffa.utils.ProgressBus
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import audio.funkwhale.ffa.utils.log
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.onApi
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.IllegalSeekPositionException
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Tracks
import com.preference.PowerPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject

class PlayerService : Service() {
  companion object {
    const val INITIAL_COMMAND_KEY = "start_command"
  }

  private val mediaSession: MediaSession by inject(MediaSession::class.java)

  private var started = false
  private val scope: CoroutineScope = CoroutineScope(Job() + Main)

  private lateinit var audioManager: AudioManager
  private var audioFocusRequest: AudioFocusRequest? = null
  private val audioFocusChangeListener = AudioFocusChange()
  private var stateWhenLostFocus = false

  private lateinit var queue: QueueManager
  private lateinit var mediaControlsManager: MediaControlsManager
  private lateinit var player: ExoPlayer

  private val mediaMetadataBuilder = MediaMetadataCompat.Builder()

  private lateinit var playerEventListener: PlayerEventListener
  private val headphonesUnpluggedReceiver = HeadphonesUnpluggedReceiver()

  private var progressCache = Triple(0, 0, 0)

  private lateinit var radioPlayer: RadioPlayer

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    intent?.action?.let {
      if (it == Intent.ACTION_MEDIA_BUTTON) {
        intent.extras?.getParcelable<KeyEvent>(Intent.EXTRA_KEY_EVENT)?.let { key ->
          when (key.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
              if (hasAudioFocus(true)) MediaButtonReceiver.handleIntent(
                mediaSession.session,
                intent
              )
              Unit
            }
            else -> MediaButtonReceiver.handleIntent(mediaSession.session, intent)
          }
        }
      }
    }

    if (!started) {
      watchEventBus()
    }

    started = true

    return START_STICKY
  }

  @SuppressLint("NewApi")
  override fun onCreate() {
    super.onCreate()

    queue = QueueManager(this)
    radioPlayer = RadioPlayer(this, scope)

    audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    Build.VERSION_CODES.O.onApi {
      audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
        setAudioAttributes(
          AudioAttributes.Builder().run {
            setUsage(AudioAttributes.USAGE_MEDIA)
            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)

            setAcceptsDelayedFocusGain(true)
            setOnAudioFocusChangeListener(audioFocusChangeListener)

            build()
          }
        )

        build()
      }
    }

    mediaControlsManager = MediaControlsManager(this, scope, mediaSession.session)

    player = ExoPlayer.Builder(this).build().apply {
      playWhenReady = false

      playerEventListener = PlayerEventListener().also {
        addListener(it)
      }
      EventBus.send(Event.StateChanged(this.isPlaying()))
    }

    mediaSession.active = true

    mediaSession.connector.apply {
      setPlayer(player)

      setMediaMetadataProvider {
        buildTrackMetadata(queue.current())
      }
    }

    if (queue.current > -1) {
      player.setMediaSource(queue.dataSources)
      player.prepare()



      FFACache.getLine(this, "progress")?.let {
        try {
          player.seekTo(queue.current, it.toLong())
          val (current, duration, percent) = getProgress(true)
          ProgressBus.send(current, duration, percent)
        } catch (e: IllegalSeekPositionException) {
          // The app remembered an incorrect position, let's reset it
          FFACache.set(this, "current", "-1")
        }
      }
    }

    registerReceiver(
      headphonesUnpluggedReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )
  }

  private fun watchEventBus() {
    scope.launch(Main) {
      CommandBus.get().collect { command ->
        if (command is Command.RefreshService) {
          if (queue.metadata.isNotEmpty()) {
            CommandBus.send(Command.RefreshTrack(queue.current()))
            EventBus.send(Event.StateChanged(player.playWhenReady))
          }
        } else if (command is Command.ReplaceQueue) {
          if (!command.fromRadio) radioPlayer.stop()

          queue.replace(command.queue, command.startIndex)
          player.setMediaSource(queue.dataSources)
          player.prepare()
          
          if (command.startIndex > 0) {
            player.seekTo(command.startIndex, C.TIME_UNSET)
          }

          setPlaybackState(true)

          CommandBus.send(Command.RefreshTrack(queue.current()))
        } else if (command is Command.AddToQueue) {
          queue.append(command.tracks)
        } else if (command is Command.PlayNext) {
          queue.insertNext(command.track)
        } else if (command is Command.RemoveFromQueue) {
          queue.remove(command.track)
        } else if (command is Command.MoveFromQueue) {
          queue.move(command.oldPosition, command.newPosition)
        } else if (command is Command.PlayTrack) {
          queue.current = command.index
          player.seekTo(command.index, C.TIME_UNSET)

          setPlaybackState(true)

          CommandBus.send(Command.RefreshTrack(queue.current()))
        } else if (command is Command.ToggleState) {
          togglePlayback()
        } else if (command is Command.SetState) {
          setPlaybackState(command.state)
        } else if (command is Command.NextTrack) {
          skipToNextTrack()
        } else if (command is Command.PreviousTrack) {
          skipToPreviousTrack()
        } else if (command is Command.Seek) {
          seek(command.progress)
        } else if (command is Command.ClearQueue) {
          queue.clear()
          player.stop()
        } else if (command is Command.ShuffleQueue) {
          queue.shuffle()
        } else if (command is Command.PlayRadio) {
          queue.clear()
          radioPlayer.play(command.radio)
        } else if (command is Command.SetRepeatMode) {
          player.repeatMode = command.mode
        } else if (command is Command.PinTrack) {
          PinService.download(this@PlayerService, command.track)
        } else if (command is Command.PinTracks) {
          command.tracks.forEach {
            PinService.download(
              this@PlayerService,
              it
            )
          }
        }
      }
    }

    scope.launch(Main) {
      RequestBus.get().collect { request ->
        if (request is Request.GetCurrentTrack) {
          request.channel?.trySend(Response.CurrentTrack(queue.current()))?.isSuccess
        } else if (request is Request.GetCurrentTrackIndex) {
          request.channel?.trySend(Response.CurrentTrackIndex(queue.currentIndex()))?.isSuccess
        } else if (request is Request.GetState) {
          request.channel?.trySend(Response.State(player.playWhenReady))?.isSuccess
        } else if (request is Request.GetQueue) {
          request.channel?.trySend(Response.Queue(queue.get()))?.isSuccess
        }
      }
    }

    scope.launch(Main) {
      while (true) {
        delay(1000)

        val (current, duration, percent) = getProgress()

        if (player.playWhenReady) {
          ProgressBus.send(current, duration, percent)
        }
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)

    if (!player.playWhenReady) {
      NotificationManagerCompat.from(this).cancelAll()
      stopSelf()
    }
  }

  @SuppressLint("NewApi")
  override fun onDestroy() {
    scope.cancel()

    try {
      unregisterReceiver(headphonesUnpluggedReceiver)
    } catch (_: Exception) {
    }

    Build.VERSION_CODES.O.onApi(
      {
        audioFocusRequest?.let {
          audioManager.abandonAudioFocusRequest(it)
        }
      },
      {
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(audioFocusChangeListener)
      }
    )

    player.removeListener(playerEventListener)
    setPlaybackState(false)
    player.release()

    mediaSession.active = false

    super.onDestroy()
  }

  private fun setPlaybackState(state: Boolean) {
    if (!state) {
      val (progress, _, _) = getProgress()

      FFACache.set(this@PlayerService, "progress", progress.toString())
    }

    if (state && player.playbackState == Player.STATE_IDLE) {
      player.setMediaSource(queue.dataSources)
      player.prepare()
    }

    if (hasAudioFocus(state)) {
      player.playWhenReady = state

      EventBus.send(Event.StateChanged(state))
    }
  }

  private fun togglePlayback() {
    setPlaybackState(!player.isPlaying)
  }

  private fun skipToPreviousTrack() {
    if (player.currentPosition > 5000) {
      return player.seekTo(0)
    }

    player.seekToPrevious()
  }

  private fun skipToNextTrack() {
    player.seekToNext()

    FFACache.set(this@PlayerService, "progress", "0")
    ProgressBus.send(0, 0, 0)
  }

  private fun getProgress(force: Boolean = false): Triple<Int, Int, Int> {
    if (!player.playWhenReady && !force) return progressCache

    return queue.current()?.bestUpload()?.let { upload ->
      val current = player.currentPosition
      val duration = upload.duration.toFloat()
      val percent = ((current / (duration * 1000)) * 100).toInt()

      progressCache = Triple(current.toInt(), duration.toInt(), percent)
      progressCache
    } ?: Triple(0, 0, 0)
  }

  private fun seek(value: Int) {
    val duration = ((queue.current()?.bestUpload()?.duration ?: 0) * (value.toFloat() / 100)) * 1000

    progressCache = Triple(duration.toInt(), queue.current()?.bestUpload()?.duration ?: 0, value)

    player.seekTo(duration.toLong())
  }

  private fun buildTrackMetadata(track: Track?): MediaMetadataCompat {
    track?.let {
      val coverUrl = maybeNormalizeUrl(track.album?.cover())

      return mediaMetadataBuilder.apply {
        putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
        putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist.name)
        putLong(
          MediaMetadata.METADATA_KEY_DURATION,
          (track.bestUpload()?.duration?.toLong() ?: 0L) * 1000
        )

        try {
          runBlocking(IO) {
            this@apply.putBitmap(
              MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
              CoverArt.requestCreator(coverUrl).get()
            )
          }
        } catch (_: Exception) {
        }
      }.build()
    }

    return mediaMetadataBuilder.build()
  }

  @SuppressLint("NewApi")
  private fun hasAudioFocus(state: Boolean): Boolean {
    var allowed = !state

    if (!allowed) {
      Build.VERSION_CODES.O.onApi(
        {
          audioFocusRequest?.let {
            allowed = when (audioManager.requestAudioFocus(it)) {
              AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> true
              else -> false
            }
          }
        },
        {

          @Suppress("DEPRECATION")
          audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioAttributes.CONTENT_TYPE_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
          ).let {
            allowed = when (it) {
              AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> true
              else -> false
            }
          }
        }
      )
    }

    return allowed
  }

  private fun skipBackwardsAfterPause(): Int {
    val deltaPref = PowerPreference.getDefaultFile().getString("auto_skip_backwards_on_pause")
    val delta = deltaPref.toFloatOrNull()
    return if (delta == null) 0 else (delta * 1000).toInt()
  }

  @SuppressLint("NewApi")
  inner class PlayerEventListener : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      super.onIsPlayingChanged(isPlaying)
      mediaControlsManager.updateNotification(queue.current(), isPlaying)
      if (!isPlaying) {
        val delta = skipBackwardsAfterPause()
        val (current, duration, _) = getProgress(true)
        val position = if (current > delta) current - delta else 0
        player.seekTo(position.toLong())
        ProgressBus.send(position, duration, ((position.toFloat()) / duration / 10).toInt())
      }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      super.onPlayWhenReadyChanged(playWhenReady, reason)

      EventBus.send(Event.StateChanged(playWhenReady))

      if (queue.current == -1) {
        CommandBus.send(Command.RefreshTrack(queue.current()))
      }

      if (!playWhenReady) {
        Build.VERSION_CODES.N.onApi(
          { stopForeground(STOP_FOREGROUND_DETACH) },
          { stopForeground(false) }
        )
      }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      super.onPlaybackStateChanged(playbackState)

      when (playbackState) {
        Player.STATE_BUFFERING -> {
          EventBus.send(Event.Buffering(true))
        }
        Player.STATE_ENDED -> {
          setPlaybackState(false)

          queue.current = 0
          player.seekTo(0, C.TIME_UNSET)

          ProgressBus.send(0, 0, 0)
        }

        Player.STATE_IDLE -> {
          setPlaybackState(false)

          EventBus.send(Event.PlaybackStopped)

          if (!player.playWhenReady) {
            mediaControlsManager.remove()
          }
        }

        Player.STATE_READY -> {
          EventBus.send(Event.Buffering(false))
        }
      }
    }

    override fun onTracksChanged(tracks: Tracks) {
      super.onTracksChanged(tracks)

      if (queue.current != player.currentMediaItemIndex) {
        queue.current = player.currentMediaItemIndex
        mediaControlsManager.updateNotification(queue.current(), player.isPlaying)
      }

      if (queue.get().isNotEmpty() &&
        queue.current() == queue.get().last() && radioPlayer.isActive()
      ) {
        scope.launch(IO) {
          if (radioPlayer.lock.tryAcquire()) {
            radioPlayer.prepareNextTrack()
            radioPlayer.lock.release()
          }
        }
      }

      FFACache.set(this@PlayerService, "current", queue.current.toString())

      CommandBus.send(Command.RefreshTrack(queue.current()))
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int
    ) {
      super.onPositionDiscontinuity(oldPosition, newPosition, reason)
      if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
        val currentTrack = queue.current().also {
          it.log("Track finished")
        }
        EventBus.send(Event.TrackFinished(currentTrack))
      }
    }

    override fun onPlayerError(error: PlaybackException) {
      EventBus.send(Event.PlaybackError(getString(R.string.error_playback)))

      if (player.playWhenReady) {
        queue.current++
        player.setMediaSource(queue.dataSources, true)
        player.seekTo(queue.current, 0)
        player.prepare()

        CommandBus.send(Command.RefreshTrack(queue.current()))
      }
    }
  }

  inner class AudioFocusChange : AudioManager.OnAudioFocusChangeListener {
    override fun onAudioFocusChange(focus: Int) {
      when (focus) {
        AudioManager.AUDIOFOCUS_GAIN -> {
          player.volume = 1f

          setPlaybackState(stateWhenLostFocus)
          stateWhenLostFocus = false
        }

        AudioManager.AUDIOFOCUS_LOSS -> {
          stateWhenLostFocus = false
          setPlaybackState(false)
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          stateWhenLostFocus = player.playWhenReady
          setPlaybackState(false)
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          stateWhenLostFocus = player.playWhenReady
          player.volume = 0.3f
        }
      }
    }
  }
}
