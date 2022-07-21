package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.model.ArtistsCache
import audio.funkwhale.ffa.model.ArtistsResponse
import audio.funkwhale.ffa.model.FFAResponse
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import org.koin.java.KoinJavaComponent.inject

class ArtistsRepository(override val context: Context?) : Repository<Artist, ArtistsCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "artists"

  override val upstream = HttpUpstream<Artist, FFAResponse<Artist>>(
    context,
    HttpUpstream.Behavior.Progressive,
    "/api/v1/artists/?playable=true&ordering=name",
    object : TypeToken<ArtistsResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Artist>) = ArtistsCache(data)
  override fun uncache(json: String) =
    gsonDeserializerOf(ArtistsCache::class.java).deserialize(json.reader())
}
