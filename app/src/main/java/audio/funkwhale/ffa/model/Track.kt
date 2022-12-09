package audio.funkwhale.ffa.model

import audio.funkwhale.ffa.utils.containsIgnoringCase
import com.preference.PowerPreference

data class Track(
  val id: Int = 0,
  val title: String,
  val artist: Artist,
  val album: Album?,
  val disc_number: Int = 0,
  val position: Int = 0,
  val uploads: List<Upload> = listOf(),
  val copyright: String? = null,
  val license: String? = null
) : SearchResult {
  var current: Boolean = false
  var favorite: Boolean = false
  var cached: Boolean = false
  var downloaded: Boolean = false

  companion object {

    fun fromDownload(download: DownloadInfo): Track = Track(
      id = download.id,
      title = download.title,
      artist = Artist(0, download.artist, listOf()),
      album = Album(0, Album.Artist(""), "", Covers(CoverUrls("")), ""),
      uploads = listOf(Upload(download.contentId, 0, 0))
    )
  }

  data class Upload(val listen_url: String, val duration: Int, val bitrate: Int)

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

    return when (PowerPreference.getDefaultFile().getString("media_cache_quality")) {
      "quality" -> uploads.maxByOrNull { it.bitrate } ?: uploads[0]
      "size" -> uploads.minByOrNull { it.bitrate } ?: uploads[0]
      else -> uploads.maxByOrNull { it.bitrate } ?: uploads[0]
    }
  }

  override fun cover() = album?.cover?.urls?.original
  override fun title() = title
  override fun subtitle() = artist.name

  val formatted: String get() = "$id $artist ($album): $title"
}
