package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.model.FFAResponse
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.model.TracksCache
import audio.funkwhale.ffa.model.TracksResponse
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import org.koin.java.KoinJavaComponent.inject

class ArtistTracksRepository(override val context: Context?, private val artistId: Int) :
  Repository<Track, TracksCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "tracks-artist-$artistId"

  override val upstream = HttpUpstream<Track, FFAResponse<Track>>(
    context,
    HttpUpstream.Behavior.AtOnce,
    "/api/v1/tracks/?playable=true&artist=$artistId",
    object : TypeToken<TracksResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Track>) = TracksCache(data)
  override fun uncache(json: String) =
    gsonDeserializerOf(TracksCache::class.java).deserialize(json.reader())
}
