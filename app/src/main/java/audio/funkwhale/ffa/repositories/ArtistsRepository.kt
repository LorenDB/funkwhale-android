package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.Artist
import audio.funkwhale.ffa.utils.ArtistsCache
import audio.funkwhale.ffa.utils.ArtistsResponse
import audio.funkwhale.ffa.utils.OAuthFactory
import audio.funkwhale.ffa.utils.OtterResponse
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader

class ArtistsRepository(override val context: Context?) : Repository<Artist, ArtistsCache>() {

  private val oAuth = OAuthFactory.instance()

  override val cacheId = "artists"

  override val upstream = HttpUpstream<Artist, OtterResponse<Artist>>(
    context,
    HttpUpstream.Behavior.Progressive,
    "/api/v1/artists/?playable=true&ordering=name",
    object : TypeToken<ArtistsResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Artist>) = ArtistsCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(ArtistsCache::class.java).deserialize(reader)
}
