package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.*
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import java.io.BufferedReader

class TracksSearchRepository(override val context: Context?, var query: String) :
  Repository<Track, TracksCache>() {

  private val exoCache: Cache by inject(Cache::class.java, named("exoCache"))
  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)
  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId: String? = null

  override val upstream: Upstream<Track>
    get() = HttpUpstream(
      context,
      HttpUpstream.Behavior.AtOnce,
      "/api/v1/tracks/?playable=true&q=$query",
      object : TypeToken<TracksResponse>() {}.type,
      oAuth
    )

  override fun cache(data: List<Track>) = TracksCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(TracksCache::class.java).deserialize(reader)

  override fun onDataFetched(data: List<Track>): List<Track> = runBlocking {
    val favorites = FavoritedRepository(context).fetch(Origin.Cache.origin)
      .map { it.data }
      .toList()
      .flatten()

    val downloaded = TracksRepository.getDownloadedIds(exoDownloadManager) ?: listOf()

    data.map { track ->
      track.favorite = favorites.contains(track.id)
      track.downloaded = downloaded.contains(track.id)

      track.bestUpload()?.let { upload ->
        val url = mustNormalizeUrl(upload.listen_url)

        track.cached = exoCache.isCached(url, 0, upload.duration * 1000L)
      }

      track
    }
  }
}

class ArtistsSearchRepository(override val context: Context?, var query: String) :
  Repository<Artist, ArtistsCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId: String? = null
  override val upstream: Upstream<Artist>
    get() = HttpUpstream(
      context,
      HttpUpstream.Behavior.AtOnce,
      "/api/v1/artists/?playable=true&q=$query",
      object : TypeToken<ArtistsResponse>() {}.type,
      oAuth
    )

  override fun cache(data: List<Artist>) = ArtistsCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(ArtistsCache::class.java).deserialize(reader)
}

class AlbumsSearchRepository(override val context: Context?, var query: String) :
  Repository<Album, AlbumsCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId: String? = null
  override val upstream: Upstream<Album>
    get() = HttpUpstream(
      context,
      HttpUpstream.Behavior.AtOnce,
      "/api/v1/albums/?playable=true&q=$query",
      object : TypeToken<AlbumsResponse>() {}.type,
      oAuth
    )

  override fun cache(data: List<Album>) = AlbumsCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(AlbumsCache::class.java).deserialize(reader)
}
