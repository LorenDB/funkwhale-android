package audio.funkwhale.ffa.model

import android.os.Parcelable
import audio.funkwhale.ffa.utils.containsIgnoringCase
import com.preference.PowerPreference
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class Track(
  val id: Int = 0,
  val title: String,
  private val cover: Covers? ,
  val artist: Artist,
  val album: Album?,
  val disc_number: Int = 0,
  val position: Int = 0,
  val uploads: List<Upload> = listOf(),
  val copyright: String? = null,
  val license: String? = null
) : SearchResult, Parcelable {

  @IgnoredOnParcel
  var current: Boolean = false

  @IgnoredOnParcel
  var favorite: Boolean = false

  @IgnoredOnParcel
  var cached: Boolean = false

  @IgnoredOnParcel
  var downloaded: Boolean = false

  companion object {

    fun fromDownload(download: DownloadInfo): Track = Track(
      id = download.id,
      title = download.title,
      cover = Covers(CoverUrls("")),
      artist = Artist(0, download.artist, listOf()),
      album = Album(0, Album.Artist(""), "", Covers(CoverUrls("")), ""),
      uploads = listOf(Upload(download.contentId, 0, 0))
    )
  }

  @Parcelize
  data class Upload(val listen_url: String, val duration: Int, val bitrate: Int) : Parcelable

  fun matchesFilter(filter: String): Boolean {
    return title.containsIgnoringCase(filter) ||
      artist.name.containsIgnoringCase(filter) ||
      album?.title.containsIgnoringCase(filter)
  }

  override fun equals(other: Any?): Boolean {
    return when (other) {
      is Track -> other.id == id
      else -> false
    }
  }

  override fun hashCode(): Int {
    return id
  }

  fun bestUpload(): Upload? {
    if (uploads.isEmpty()) return null

    var bestUpload = when (PowerPreference.getDefaultFile().getString("media_cache_quality")) {
      "quality" -> uploads.maxByOrNull { it.bitrate } ?: uploads[0]
      "size" -> uploads.minByOrNull { it.bitrate } ?: uploads[0]
      else -> uploads.maxByOrNull { it.bitrate } ?: uploads[0]
    }

    return when (PowerPreference.getDefaultFile().getString("bandwidth_limitation")) {
      "unlimited" -> bestUpload
      "limited" -> {
        var listenUrl = bestUpload.listen_url
        Upload(listenUrl.plus("&to=mp3&max_bitrate=320"), uploads[0].duration, 320_000)
      }
      else -> bestUpload
    }
  }

  override fun cover(): String? {
    return if (cover?.urls?.original != null) {
      cover.urls.original
    } else {
      album?.cover()
    }
  }

  override fun title() = title
  override fun subtitle() = artist.name

  val formatted: String get() = "$id $artist ($album): $title"
}
