package audio.funkwhale.ffa.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Album(
  val id: Int,
  val artist: Artist,
  val title: String,
  val cover: Covers?,
  val release_date: String?
) : SearchResult, Parcelable {
  @Parcelize
  data class Artist(val name: String) : Parcelable

  override fun cover() = cover?.urls?.original
  override fun title() = title
  override fun subtitle() = artist.name
}

typealias AlbumList = List<Album>
