package audio.funkwhale.ffa.model

data class Artist(
  val id: Int,
  val name: String,
  val albums: List<Album>?
) : SearchResult {
  data class Album(
    val title: String,
    val cover: Covers?
  )

  override fun cover(): String? = albums?.getOrNull(0)?.cover?.urls?.original
  override fun title() = name
  override fun subtitle() = "Artist"
}
