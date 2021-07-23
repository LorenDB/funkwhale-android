package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.OtterResponse
import audio.funkwhale.ffa.utils.Playlist
import audio.funkwhale.ffa.utils.PlaylistsCache
import audio.funkwhale.ffa.utils.PlaylistsResponse
import audio.funkwhale.ffa.utils.Settings
import audio.funkwhale.ffa.utils.Track
import audio.funkwhale.ffa.utils.authorize
import audio.funkwhale.ffa.utils.mustNormalizeUrl
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitObjectResponseResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader

data class PlaylistAdd(val tracks: List<Int>, val allow_duplicates: Boolean)

class PlaylistsRepository(override val context: Context?) : Repository<Playlist, PlaylistsCache>() {

  override val cacheId = "tracks-playlists"

  override val upstream = HttpUpstream<Playlist, OtterResponse<Playlist>>(
    context!!,
    HttpUpstream.Behavior.Progressive,
    "/api/v1/playlists/?playable=true&ordering=name",
    object : TypeToken<PlaylistsResponse>() {}.type
  )

  override fun cache(data: List<Playlist>) = PlaylistsCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(PlaylistsCache::class.java).deserialize(reader)
}

class ManagementPlaylistsRepository(override val context: Context?) :
  Repository<Playlist, PlaylistsCache>() {

  override val cacheId = "tracks-playlists-management"

  override val upstream = HttpUpstream<Playlist, OtterResponse<Playlist>>(
    context,
    HttpUpstream.Behavior.AtOnce,
    "/api/v1/playlists/?scope=me&ordering=name",
    object : TypeToken<PlaylistsResponse>() {}.type
  )

  override fun cache(data: List<Playlist>) = PlaylistsCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(PlaylistsCache::class.java).deserialize(reader)

  suspend fun new(name: String): Int? {
    context?.let {

      val body = mapOf("name" to name, "privacy_level" to "me")

      val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/")).apply {
        if (!Settings.isAnonymous()) {
          authorize(context)
          header("Authorization", "Bearer ${OAuth.state().accessToken}")
        }
      }

      val (_, response, result) = request
        .header("Content-Type", "application/json")
        .body(Gson().toJson(body))
        .awaitObjectResponseResult(gsonDeserializerOf(Playlist::class.java))

      if (response.statusCode != 201) return null

      return result.get().id
    }
    throw IllegalStateException("Illegal state: context is null")
  }

  fun add(id: Int, tracks: List<Track>) {
    context?.let {
      val body = PlaylistAdd(tracks.map { it.id }, false)

      val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/$id/add/")).apply {
        if (!Settings.isAnonymous()) {
          authorize(context)
          header("Authorization", "Bearer ${OAuth.state().accessToken}")
        }
      }

      scope.launch(Dispatchers.IO) {
        request
          .header("Content-Type", "application/json")
          .body(Gson().toJson(body))
          .awaitByteArrayResponseResult()
      }
    }
    throw IllegalStateException("Illegal state: context is null")
  }

  suspend fun remove(id: Int, track: Track, index: Int) {
    context?.let {
      val body = mapOf("index" to index)

      val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/$id/remove/")).apply {
        if (!Settings.isAnonymous()) {
          authorize(context)
          header("Authorization", "Bearer ${OAuth.state().accessToken}")
        }
      }

      request
        .header("Content-Type", "application/json")
        .body(Gson().toJson(body))
        .awaitByteArrayResponseResult()
    }
    throw IllegalStateException("Illegal state: context is null")
  }

  fun move(id: Int, from: Int, to: Int) {
    context?.let {
      val body = mapOf("from" to from, "to" to to)

      val request = Fuel.post(mustNormalizeUrl("/api/v1/playlists/$id/move/")).apply {
        if (!Settings.isAnonymous()) {
          authorize(context)
          header("Authorization", "Bearer ${OAuth.state().accessToken}")
        }
      }

      scope.launch(Dispatchers.IO) {
        request
          .header("Content-Type", "application/json")
          .body(Gson().toJson(body))
          .awaitByteArrayResponseResult()
      }
    }
    throw IllegalStateException("Illegal state: context is null")
  }
}
