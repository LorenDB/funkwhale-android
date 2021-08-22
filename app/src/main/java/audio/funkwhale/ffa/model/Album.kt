package audio.funkwhale.ffa.model

data class Album(
  val id: Int,
  val artist: Artist,
  val title: String,
  val cover: Covers?,
  val release_date: String?
) : SearchResult {
  data class Artist(val name: String)

  override fun cover() = cover?.urls?.original
  override fun title() = title
  override fun subtitle() = artist.name
}

typealias AlbumList = List<Album>
