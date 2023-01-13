package audio.funkwhale.ffa.model

data class FavoritesResponse(
  override val count: Int,
  override val next: String?,
  val results: List<Favorite>
) : FFAResponse<Favorite>() {
  override fun getData() = results
}
