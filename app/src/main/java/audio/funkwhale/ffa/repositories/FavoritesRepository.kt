package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject
import java.io.BufferedReader

class FavoritesRepository(override val context: Context?) : Repository<Track, TracksCache>() {

  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)
  private val exoCache: Cache by inject(Cache::class.java, named("exoCache"))
  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "favorites.v2"

  override val upstream = HttpUpstream<Track, OtterResponse<Track>>(
    context!!,
    HttpUpstream.Behavior.AtOnce,
    "/api/v1/tracks/?favorites=true&playable=true&ordering=title",
    object : TypeToken<TracksResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Track>) = TracksCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(TracksCache::class.java).deserialize(reader)

  private val favoritedRepository = FavoritedRepository(context!!)

  override fun onDataFetched(data: List<Track>): List<Track> = runBlocking {
    val downloaded = TracksRepository.getDownloadedIds(exoDownloadManager) ?: listOf()

    data.map { track ->
      track.favorite = true
      track.downloaded = downloaded.contains(track.id)

      track.bestUpload()?.let { upload ->
        maybeNormalizeUrl(upload.listen_url)?.let { url ->
          track.cached = exoCache.isCached(url, 0, upload.duration * 1000L)
        }
      }

      track
    }
  }

  fun addFavorite(id: Int) {
    context?.let {
      val body = mapOf("track" to id)

      val request = Fuel.post(mustNormalizeUrl("/api/v1/favorites/tracks/")).apply {
        if (!Settings.isAnonymous()) {
          authorize(context, oAuth)
          header("Authorization", "Bearer ${oAuth.state().accessToken}")
        }
      }

      scope.launch(IO) {
        request
          .header("Content-Type", "application/json")
          .body(Gson().toJson(body))
          .awaitByteArrayResponseResult()

        favoritedRepository.update(context, scope)
      }
    }
  }

  fun deleteFavorite(id: Int) {
    context?.let {
      val body = mapOf("track" to id)

      val request = Fuel.post(mustNormalizeUrl("/api/v1/favorites/tracks/remove/")).apply {
        if (!Settings.isAnonymous()) {
          authorize(context, oAuth)
          request.header("Authorization", "Bearer ${oAuth.state().accessToken}")
        }
      }

      scope.launch(IO) {
        request
          .header("Content-Type", "application/json")
          .body(Gson().toJson(body))
          .awaitByteArrayResponseResult()

        favoritedRepository.update(context, scope)
      }
    }
  }
}

class FavoritedRepository(override val context: Context?) : Repository<Int, FavoritedCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "favorited"
  override val upstream = HttpUpstream<Int, OtterResponse<Int>>(
    context,
    HttpUpstream.Behavior.Single,
    "/api/v1/favorites/tracks/all/?playable=true",
    object : TypeToken<FavoritedResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Int>) = FavoritedCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(FavoritedCache::class.java).deserialize(reader)

  fun update(context: Context?, scope: CoroutineScope) {
    fetch(Origin.Network.origin).untilNetwork(scope, IO) { favorites, _, _, _ ->
      FFACache.set(context, cacheId, Gson().toJson(cache(favorites)).toByteArray())
    }
  }
}
