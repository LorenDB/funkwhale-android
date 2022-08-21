package audio.funkwhale.ffa.utils

import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.model.Radio
import audio.funkwhale.ffa.model.Track
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadCursor
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch

sealed class Command {
  class StartService(val command: Command) : Command()
  object RefreshService : Command()

  object ToggleState : Command()
  class SetState(val state: Boolean) : Command()

  object NextTrack : Command()
  object PreviousTrack : Command()
  class Seek(val progress: Int) : Command()

  class AddToQueue(val tracks: List<Track>) : Command()
  class AddToPlaylist(val tracks: List<Track>) : Command()
  class PlayNext(val track: Track) : Command()
  class ReplaceQueue(val queue: List<Track>, val fromRadio: Boolean = false) : Command()
  class RemoveFromQueue(val track: Track) : Command()
  class MoveFromQueue(val oldPosition: Int, val newPosition: Int) : Command()
  object ClearQueue : Command()
  object ShuffleQueue : Command()
  class PlayRadio(val radio: Radio) : Command()

  class SetRepeatMode(val mode: Int) : Command()

  class PlayTrack(val index: Int) : Command()
  class PinTrack(val track: Track) : Command()
  class PinTracks(val tracks: List<Track>) : Command()

  class RefreshTrack(val track: Track?) : Command()
}

sealed class Event {
  object LogOut : Event()

  class PlaybackError(val message: String) : Event()
  object PlaybackStopped : Event()
  class Buffering(val value: Boolean) : Event()
  class TrackFinished(val track: Track?) : Event()
  class StateChanged(val playing: Boolean) : Event()
  object QueueChanged : Event()
  object RadioStarted : Event()
  object ListingsChanged : Event()
  class DownloadChanged(val download: Download) : Event()
}

sealed class Request(var channel: Channel<Response>? = null) {
  object GetState : Request()
  object GetQueue : Request()
  object GetCurrentTrack : Request()
  object GetDownloads : Request()
}

sealed class Response {
  class State(val playing: Boolean) : Response()
  class Queue(val queue: List<Track>) : Response()
  class CurrentTrack(val track: Track?) : Response()
  class Downloads(val cursor: DownloadCursor) : Response()
}

object EventBus {
  private var _events = MutableSharedFlow<Event>()
  val events = _events.asSharedFlow()
  fun send(event: Event) {
    GlobalScope.launch(IO) {
      _events.emit(event)
    }
  }

  fun get() = events
}

object CommandBus {
  private var _commands = MutableSharedFlow<Command>()
  var commands = _commands.asSharedFlow()
  fun send(command: Command) {
    GlobalScope.launch(IO) {
    _commands.emit(command)
    }
  }

  fun get() = commands
}

object RequestBus {
  fun send(request: Request): Channel<Response> {
    return Channel<Response>().also {
      GlobalScope.launch(IO) {
        request.channel = it

        FFA.get().requestBus.trySend(request).isSuccess
      }
    }
  }

  fun get() = FFA.get().requestBus.asFlow()
}

object ProgressBus {
  private var _progress = MutableStateFlow(Triple(0, 0, 0))
  val progress = _progress.asStateFlow()
  fun send(current: Int, duration: Int, percent: Int) {
    _progress.value = Triple(current, duration, percent)
  }

  fun get() = progress
}

suspend inline fun <reified T> Channel<Response>.wait(): T? {
  return when (val response = this.receive()) {
    is T -> response
    else -> null
  }
}
