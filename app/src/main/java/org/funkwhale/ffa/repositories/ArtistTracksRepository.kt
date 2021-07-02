package org.funkwhale.ffa.repositories

import android.content.Context
import org.funkwhale.ffa.utils.OtterResponse
import org.funkwhale.ffa.utils.Track
import org.funkwhale.ffa.utils.TracksCache
import org.funkwhale.ffa.utils.TracksResponse
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader

class ArtistTracksRepository(override val context: Context?, private val artistId: Int) : Repository<Track, TracksCache>() {
  override val cacheId = "tracks-artist-$artistId"
  override val upstream = HttpUpstream<Track, OtterResponse<Track>>(HttpUpstream.Behavior.AtOnce, "/api/v1/tracks/?playable=true&artist=$artistId", object : TypeToken<TracksResponse>() {}.type)

  override fun cache(data: List<Track>) = TracksCache(data)
  override fun uncache(reader: BufferedReader) = gsonDeserializerOf(TracksCache::class.java).deserialize(reader)
}
