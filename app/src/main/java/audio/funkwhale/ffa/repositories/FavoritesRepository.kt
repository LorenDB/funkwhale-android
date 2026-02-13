package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.model.FFAResponse
import audio.funkwhale.ffa.model.Favorite
import audio.funkwhale.ffa.model.FavoritesResponse
import audio.funkwhale.ffa.model.FavoritedCache
import audio.funkwhale.ffa.model.FavoritedResponse
import audio.funkwhale.ffa.model.FavoritesCache
import audio.funkwhale.ffa.utils.FFACache
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.Settings
import audio.funkwhale.ffa.utils.authorize
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.mustNormalizeUrl
import audio.funkwhale.ffa.utils.untilNetwork
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.inject

class FavoritesRepository(override val context: Context?) : Repository<Favorite, FavoritesCache>() {

  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)
  private val exoCache: Cache by inject(Cache::class.java, named("exoCache"))
  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "favorites.v2"

  override val upstream = HttpUpstream<Favorite, FFAResponse<Favorite>>(
    context!!,
    HttpUpstream.Behavior.AtOnce,
    "/api/v1/favorites/tracks/?scope=all&ordering=-creation_date",
    object : TypeToken<FavoritesResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Favorite>) = FavoritesCache(data)
  override fun uncache(json: String) =
    gsonDeserializerOf(FavoritesCache::class.java).deserialize(json.reader())

  private val favoritedRepository = FavoritedRepository(context!!)

  override fun onDataFetched(data: List<Favorite>): List<Favorite> = runBlocking {
    val downloaded = TracksRepository.getDownloadedIds(exoDownloadManager) ?: listOf()

    data.map { favorite ->
      favorite.track.favorite = true
      favorite.track.downloaded = downloaded.contains(favorite.track.id)

      favorite.track.bestUpload()?.let { upload ->
        maybeNormalizeUrl(upload.listen_url)?.let { url ->
          favorite.track.cached = exoCache.isCached(url, 0, upload.duration * 1000L)
        }
      }

      favorite
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
  override val upstream = HttpUpstream<Int, FFAResponse<Int>>(
    context,
    HttpUpstream.Behavior.Single,
    "/api/v1/favorites/tracks/all/?playable=true",
    object : TypeToken<FavoritedResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Int>) = FavoritedCache(data)
  override fun uncache(json: String) =
    gsonDeserializerOf(FavoritedCache::class.java).deserialize(json.reader())

  fun update(context: Context?, scope: CoroutineScope) {
    fetch(Origin.Network.origin).untilNetwork(scope, IO) { favorites, _, _, _ ->
      // Only update cache if we received data. This prevents clearing cache on network errors.
      // Note: This also means if the server legitimately returns empty (e.g., user deleted 
      // their last favorite), we won't clear the cache. This trade-off prioritizes data 
      // preservation over immediate consistency when we can't distinguish between empty-due-to-error
      // versus legitimately-empty responses.
      if (favorites.isNotEmpty()) {
        FFACache.set(context, cacheId, Gson().toJson(cache(favorites)).toString())
      }
    }
  }
}
