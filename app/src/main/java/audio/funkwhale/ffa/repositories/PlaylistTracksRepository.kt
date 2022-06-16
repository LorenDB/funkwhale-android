package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.model.FFAResponse
import audio.funkwhale.ffa.model.PlaylistTrack
import audio.funkwhale.ffa.model.PlaylistTracksCache
import audio.funkwhale.ffa.model.PlaylistTracksResponse
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject

class PlaylistTracksRepository(override val context: Context?, playlistId: Int) :
  Repository<PlaylistTrack, PlaylistTracksCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "tracks-playlist-$playlistId"

  override val upstream = HttpUpstream<PlaylistTrack, FFAResponse<PlaylistTrack>>(
    context,
    HttpUpstream.Behavior.Single,
    "/api/v1/playlists/$playlistId/tracks/?playable=true",
    object : TypeToken<PlaylistTracksResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<PlaylistTrack>) = PlaylistTracksCache(data)
  override fun uncache(json: String) =
    gsonDeserializerOf(PlaylistTracksCache::class.java).deserialize(json)

  override fun onDataFetched(data: List<PlaylistTrack>): List<PlaylistTrack> = runBlocking {
    val favorites = FavoritedRepository(context).fetch(Origin.Network.origin)
      .map { it.data }
      .toList()
      .flatten()

    data.map { track ->
      track.track.favorite = favorites.contains(track.track.id)
      track
    }
  }
}
