package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.Album
import audio.funkwhale.ffa.utils.AlbumsCache
import audio.funkwhale.ffa.utils.AlbumsResponse
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader

class AlbumsRepository(override val context: Context?, artistId: Int? = null) :
  Repository<Album, AlbumsCache>() {

  override val cacheId: String by lazy {
    if (artistId == null) "albums"
    else "albums-artist-$artistId"
  }

  override val upstream: Upstream<Album> by lazy {
    val url =
      if (artistId == null) "/api/v1/albums/?playable=true&ordering=title"
      else "/api/v1/albums/?playable=true&artist=$artistId&ordering=release_date"

    HttpUpstream(
      context!!,
      HttpUpstream.Behavior.Progressive,
      url,
      object : TypeToken<AlbumsResponse>() {}.type
    )
  }

  override fun cache(data: List<Album>) = AlbumsCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(AlbumsCache::class.java).deserialize(reader)
}
