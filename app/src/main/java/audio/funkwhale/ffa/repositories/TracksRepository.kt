package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.model.FFAResponse
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.model.TracksCache
import audio.funkwhale.ffa.model.TracksResponse
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.getMetadata
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import audio.funkwhale.ffa.utils.mustNormalizeUrl
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject

class TracksRepository(override val context: Context?, albumId: Int) :
  Repository<Track, TracksCache>() {

  private val exoCache: Cache by inject(Cache::class.java, named("exoCache"))
  private val oAuth: OAuth by inject(OAuth::class.java)
  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)

  override val cacheId = "tracks-album-$albumId"

  override val upstream = HttpUpstream<Track, FFAResponse<Track>>(
    context,
    HttpUpstream.Behavior.AtOnce,
    "/api/v1/tracks/?playable=true&album=$albumId&ordering=disc_number,position",
    object : TypeToken<TracksResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Track>) = TracksCache(data)
  override fun uncache(json: String) =
    gsonDeserializerOf(TracksCache::class.java).deserialize(json.reader())

  companion object {
    fun getDownloadedIds(exoDownloadManager: DownloadManager): List<Int>? {
      val ids: MutableList<Int> = mutableListOf()
      exoDownloadManager.downloadIndex.getDownloads()
        .use { cursor ->
          while (cursor.moveToNext()) {
            val download = cursor.download
            download.getMetadata()?.let {
              if (download.state == Download.STATE_COMPLETED) {
                ids.add(it.id)
              }
            }
          }
        }
      return ids
    }
  }

  override fun onDataFetched(data: List<Track>): List<Track> = runBlocking {
    val favorites = FavoritedRepository(context).fetch(Origin.Cache.origin)
      .map { it.data }
      .toList()
      .flatten()

    val downloaded = getDownloadedIds(exoDownloadManager) ?: listOf()

    data.map { track ->
      track.favorite = favorites.contains(track.id)
      track.downloaded = downloaded.contains(track.id)

      track.bestUpload()?.let { upload ->
        val url = mustNormalizeUrl(upload.listen_url)

        track.cached = exoCache.isCached(url, 0, upload.duration * 1000L)
      }

      track
    }.sortedWith(compareBy({ it.disc_number }, { it.position }))
  }
}
