package audio.funkwhale.ffa.model

import androidx.media3.exoplayer.offline.Download

data class DownloadInfo(
  val id: Int,
  val contentId: String,
  val title: String,
  val artist: String,
  var download: Download?
)
