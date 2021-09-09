package audio.funkwhale.ffa.model

data class TracksResponse(
  override val count: Int,
  override val next: String?,
  val results: List<Track>
) : FFAResponse<Track>() {
  override fun getData() = results
}
