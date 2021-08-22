package audio.funkwhale.ffa.model

data class Radio(
  val id: Int,
  var radio_type: String,
  val name: String,
  val description: String,
  var related_object_id: String? = null
)