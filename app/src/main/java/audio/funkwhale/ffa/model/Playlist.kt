package audio.funkwhale.ffa.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Playlist(
  val id: Int,
  val name: String,
  val album_covers: List<String>,
  val tracks_count: Int,
  val duration: Int
) : Parcelable
