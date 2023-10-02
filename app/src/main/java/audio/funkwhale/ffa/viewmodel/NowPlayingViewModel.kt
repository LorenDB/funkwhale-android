package audio.funkwhale.ffa.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NowPlayingViewModel(app: Application) : AndroidViewModel(app) {
  val isBuffering = EventBus.get()
    .filter { it is Event.Buffering }
    .map { (it as Event.Buffering).value }
    .stateIn(viewModelScope, SharingStarted.Lazily, false)
    .asLiveData(viewModelScope.coroutineContext)
    .distinctUntilChanged()

  val isPlaying = EventBus.get()
    .filter { it is Event.StateChanged }
    .map { (it as Event.StateChanged).playing }
    .stateIn(viewModelScope, SharingStarted.Lazily, false)
    .asLiveData(viewModelScope.coroutineContext)
    .distinctUntilChanged()

  val repeatMode = MutableLiveData(0)
  val progress = MutableLiveData(0)
  val currentTrack = MutableLiveData<Track?>(null)
  val currentProgressText = MutableLiveData("")
  val currentDurationText = MutableLiveData("")

  // Calling distinctUntilChanged() prevents triggering an event when the track hasn't changed
  val currentTrackTitle = currentTrack.distinctUntilChanged().map { it?.title ?: "" }
  val currentTrackArtist = currentTrack.distinctUntilChanged().map { it?.artist?.name ?: "" }

  // Not calling distinctUntilChanged() here as we need to process every event
  val isCurrentTrackFavorite = currentTrack.map {
    it?.favorite ?: false
  }

  val repeatModeResource = repeatMode.distinctUntilChanged().map {
    when (it) {
      Player.REPEAT_MODE_ONE -> AppCompatResources.getDrawable(context, R.drawable.repeat_one)
      else -> AppCompatResources.getDrawable(context, R.drawable.repeat)
    }
  }

  val repeatModeAlpha = repeatMode.distinctUntilChanged().map {
    when (it) {
      Player.REPEAT_MODE_OFF -> 0.2f
      else -> 1f
    }
  }

  private val context: Context
    get() = getApplication<FFA>().applicationContext
}
