package audio.funkwhale.ffa.model

data class PlaylistTracksResponse(
  override val count: Int,
  override val next: String?,
  val results: List<PlaylistTrack>
) : FFAResponse<PlaylistTrack>() {
  override fun getData() = results
}