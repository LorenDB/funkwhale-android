package audio.funkwhale.ffa.playback

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class FFAMediaLibraryService : MediaBrowserServiceCompat() {

  private val mediaSession: MediaSession by inject()
  private val scope = CoroutineScope(Job() + Main)

  override fun onCreate() {
    super.onCreate()
    sessionToken = mediaSession.session.sessionToken
  }

  override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
    return BrowserRoot("root", null)
  }

  override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
    if (parentId == "root") {
      result.detach()
      scope.launch {
        val channel = RequestBus.send(Request.GetQueue)
        val response = channel.receive()
        if (response is Response.Queue) {
          val items = response.queue.map { track ->
            val description = MediaDescriptionCompat.Builder()
              .setMediaId(track.id.toString())
              .setTitle(track.title)
              .setSubtitle(track.artist.name)
              .build()
            MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
          }.toMutableList()
          result.sendResult(items)
        } else {
          result.sendResult(mutableListOf())
        }
        channel.close()
      }
    } else {
      result.sendResult(mutableListOf())
    }
  }
}
