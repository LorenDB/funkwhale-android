package audio.funkwhale.ffa.model

data class FavoritedResponse(
  override val count: Int,
  override val next: String?,
  val results: List<Favorited>
) : FFAResponse<Int>() {
  override fun getData() = results.map { it.track }
}
