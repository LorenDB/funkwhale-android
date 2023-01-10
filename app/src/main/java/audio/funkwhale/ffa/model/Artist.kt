package audio.funkwhale.ffa.model

import java.util.Calendar.DAY_OF_YEAR
import java.util.GregorianCalendar

data class Artist(
  val id: Int,
  val name: String,
  val albums: List<Album>?
) : SearchResult {
  data class Album(
    val title: String,
    val cover: Covers?
  )

  override fun cover(): String? = albums?.mapNotNull { it.cover?.urls?.original }?.let { covers ->
    if (covers.isEmpty()) {
      return@let null
    }
    // Inject a little whimsy: rotate through the album covers daily
    val index = GregorianCalendar().get(DAY_OF_YEAR) % covers.size
    covers.getOrNull(index)
  }
  override fun title() = name
  override fun subtitle() = "Artist"
}
