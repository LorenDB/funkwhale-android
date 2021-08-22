package audio.funkwhale.ffa.model

data class ArtistsResponse(
  override val count: Int,
  override val next: String?,
  val results: List<Artist>
) : FFAResponse<Artist>() {
  override fun getData() = results
}