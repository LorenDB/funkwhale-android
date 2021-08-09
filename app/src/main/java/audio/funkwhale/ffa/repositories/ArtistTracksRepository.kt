package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.*
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import org.koin.java.KoinJavaComponent.inject
import java.io.BufferedReader

class ArtistTracksRepository(override val context: Context?, private val artistId: Int) :
  Repository<Track, TracksCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "tracks-artist-$artistId"

  override val upstream = HttpUpstream<Track, OtterResponse<Track>>(
    context,
    HttpUpstream.Behavior.AtOnce,
    "/api/v1/tracks/?playable=true&artist=$artistId",
    object : TypeToken<TracksResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Track>) = TracksCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(TracksCache::class.java).deserialize(reader)
}
