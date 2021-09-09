package audio.funkwhale.ffa.model

data class RadiosResponse(
  override val count: Int,
  override val next: String?,
  val results: List<Radio>
) : FFAResponse<Radio>() {
  override fun getData() = results
}
