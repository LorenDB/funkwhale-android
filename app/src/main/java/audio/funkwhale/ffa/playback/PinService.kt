package audio.funkwhale.ffa.playback

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.model.DownloadInfo
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.*
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.scheduler.Scheduler
import com.google.android.exoplayer2.ui.DownloadNotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import java.util.*

class PinService : DownloadService(AppContext.NOTIFICATION_DOWNLOADS) {

  private val scope: CoroutineScope = CoroutineScope(Job() + Main)
  private val exoDownloadManager: DownloadManager by KoinJavaComponent.inject(DownloadManager::class.java)

  companion object {

    fun download(context: Context, track: Track) {
      track.bestUpload()?.let { upload ->
        val url = mustNormalizeUrl(upload.listen_url)
        val data = Gson().toJson(
          DownloadInfo(
            track.id,
            url,
            track.title,
            track.artist.name,
            null
          )
        ).toByteArray()

        val request = DownloadRequest.Builder(url.toUri().toString(), url.toUri())
          .setData(data)
          .setStreamKeys(Collections.emptyList())
          .build()

        sendAddDownload(context, PinService::class.java, request, false)
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    buildResumeDownloadsIntent(this, PinService::class.java, true)

    scope.launch(Main) {
      RequestBus.get().collect { request ->
        when (request) {
          is Request.GetDownloads -> request.channel?.trySend(Response.Downloads(getDownloads()))?.isSuccess
        }
      }
    }

    return super.onStartCommand(intent, flags, startId)
  }

  override fun getDownloadManager(): DownloadManager {
    return exoDownloadManager.apply {
      addListener(DownloadListener())
    }
  }

  override fun getScheduler(): Scheduler? = null

  override fun getForegroundNotification(downloads: MutableList<Download>): Notification {
    val description =
      resources.getQuantityString(R.plurals.downloads_description, downloads.size, downloads.size)

    return DownloadNotificationHelper(
      this,
      AppContext.NOTIFICATION_CHANNEL_DOWNLOADS
    ).buildProgressNotification(this, R.drawable.downloads, null, description, downloads)
  }

  private fun getDownloads() = downloadManager.downloadIndex.getDownloads()

  inner class DownloadListener : DownloadManager.Listener {

    override fun onDownloadChanged(
      downloadManager: DownloadManager,
      download: Download,
      finalException: Exception?
    ) {
      super.onDownloadChanged(downloadManager, download, finalException)

      EventBus.send(Event.DownloadChanged(download))
    }
  }
}
