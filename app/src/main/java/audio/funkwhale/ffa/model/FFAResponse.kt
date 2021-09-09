package audio.funkwhale.ffa.model

abstract class FFAResponse<D : Any> {
  abstract val count: Int
  abstract val next: String?

  abstract fun getData(): List<D>
}
