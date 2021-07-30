package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.utils.Album
import audio.funkwhale.ffa.utils.AlbumsCache
import audio.funkwhale.ffa.utils.AlbumsResponse
import audio.funkwhale.ffa.utils.Artist
import audio.funkwhale.ffa.utils.ArtistsCache
import audio.funkwhale.ffa.utils.ArtistsResponse
import audio.funkwhale.ffa.utils.OAuthFactory
import audio.funkwhale.ffa.utils.Track
import audio.funkwhale.ffa.utils.TracksCache
import audio.funkwhale.ffa.utils.TracksResponse
import audio.funkwhale.ffa.utils.mustNormalizeUrl
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader

class TracksSearchRepository(override val context: Context?, var query: String) :
  Repository<Track, TracksCache>() {

  private val oAuth = OAuthFactory.instance()

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

    val downloaded = TracksRepository.getDownloadedIds() ?: listOf()

    data.map { track ->
      track.favorite = favorites.contains(track.id)
      track.downloaded = downloaded.contains(track.id)

      track.bestUpload()?.let { upload ->
        val url = mustNormalizeUrl(upload.listen_url)

        track.cached = FFA.get().exoCache.isCached(url, 0, upload.duration * 1000L)
      }

      track
    }
  }
}

class ArtistsSearchRepository(override val context: Context?, var query: String) :
  Repository<Artist, ArtistsCache>() {

  private val oAuth = OAuthFactory.instance()

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

  private val oAuth = OAuthFactory.instance()

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
