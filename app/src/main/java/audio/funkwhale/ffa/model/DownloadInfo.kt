package audio.funkwhale.ffa.model

import com.google.android.exoplayer2.offline.Download

data class DownloadInfo(
  val id: Int,
  val contentId: String,
  val title: String,
  val artist: String,
  var download: Download?
)