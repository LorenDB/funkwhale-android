package audio.funkwhale.ffa.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import com.google.android.exoplayer2.Player
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

class NowPlayingViewModel(app: Application) : AndroidViewModel(app) {
  val isBuffering = MutableLiveData(false)
  val isPlaying = MutableLiveData(false)
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
