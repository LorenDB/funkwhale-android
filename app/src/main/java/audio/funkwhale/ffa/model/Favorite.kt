package audio.funkwhale.ffa.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Favorite(
  val id: Int = 0,
  val track: Track
) : Parcelable
