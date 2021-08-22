package audio.funkwhale.ffa.model

data class AlbumsResponse(
  override val count: Int,
  override val next: String?,
  val results: AlbumList
) : FFAResponse<Album>() {
  override fun getData() = results
}