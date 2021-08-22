package audio.funkwhale.ffa.model

data class Playlist(
  val id: Int,
  val name: String,
  val album_covers: List<String>,
  val tracks_count: Int,
  val duration: Int
)