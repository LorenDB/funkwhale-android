package audio.funkwhale.ffa.model

data class PlaylistsResponse(
  override val count: Int,
  override val next: String?,
  val results: List<Playlist>
) : FFAResponse<Playlist>() {
  override fun getData() = results
}
