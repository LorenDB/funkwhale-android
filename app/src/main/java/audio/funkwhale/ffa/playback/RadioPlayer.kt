package audio.funkwhale.ffa.playback

import android.content.Context
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.model.Radio
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.FavoritedRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.utils.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObjectResponseResult
import com.github.kittinunf.fuel.coroutines.awaitObjectResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

data class RadioSessionBody(
  val radio_type: String,
  var custom_radio: Int? = null,
  var related_object_id: String? = null
)

data class RadioSession(val id: Int)
data class RadioTrackBody(val session: Int)
data class RadioTrack(val position: Int, val track: RadioTrackID)
data class RadioTrackID(val id: Int)

class RadioPlayer(val context: Context, val scope: CoroutineScope) {
  val lock = Semaphore(1)

  private val oAuth: OAuth by inject(OAuth::class.java)
  private var currentRadio: Radio? = null
  private var session: Int? = null
  private var cookie: String? = null

  private val favoritedRepository = FavoritedRepository(context)

  init {
    FFACache.get(context, "radio_type")?.readLine()?.let { radio_type ->
      FFACache.get(context, "radio_id")?.readLine()?.toInt()?.let { radio_id ->
        FFACache.get(context, "radio_session")?.readLine()?.toInt()?.let { radio_session ->
          val cachedCookie = FFACache.get(context, "radio_cookie")?.readLine()

          currentRadio = Radio(radio_id, radio_type, "", "")
          session = radio_session
          cookie = cachedCookie
        }
      }
    }
  }

  fun play(radio: Radio) {
    currentRadio = radio
    session = null

    scope.launch(IO) {
      createSession()
    }
  }

  fun stop() {
    currentRadio = null
    session = null

    FFACache.delete(context, "radio_type")
    FFACache.delete(context, "radio_id")
    FFACache.delete(context, "radio_session")
    FFACache.delete(context, "radio_cookie")
  }

  fun isActive() = currentRadio != null && session != null

  private suspend fun createSession() {
    currentRadio?.let { radio ->
      try {
        val request =
          RadioSessionBody(radio.radio_type, related_object_id = radio.related_object_id).apply {
            if (radio_type == "custom") {
              custom_radio = radio.id
            }
          }

        val body = Gson().toJson(request)
        val (_, response, result) = Fuel.post(mustNormalizeUrl("/api/v1/radios/sessions/"))
          .authorize(context, oAuth)
          .header("Content-Type", "application/json")
          .body(body)
          .awaitObjectResponseResult(gsonDeserializerOf(RadioSession::class.java))

        session = result.get().id
        cookie = response.header("set-cookie").joinToString(";")

        FFACache.set(context, "radio_type", radio.radio_type.toByteArray())
        FFACache.set(context, "radio_id", radio.id.toString().toByteArray())
        FFACache.set(context, "radio_session", session.toString().toByteArray())
        FFACache.set(context, "radio_cookie", cookie.toString().toByteArray())

        prepareNextTrack(true)
      } catch (e: Exception) {
        e.logError()
        withContext(Main) {
          context.toast(context.getString(R.string.radio_playback_error))
        }
      }
    }
  }

  suspend fun prepareNextTrack(first: Boolean = false) {
    session?.let { session ->
      try {
        val body = Gson().toJson(RadioTrackBody(session))
        val result = Fuel.post(mustNormalizeUrl("/api/v1/radios/tracks/"))
          .authorize(context, oAuth)
          .header("Content-Type", "application/json")
          .apply {
            cookie?.let {
              header("cookie", it)
            }
          }
          .body(body)
          .awaitObjectResult(gsonDeserializerOf(RadioTrack::class.java))

        val trackResponse = Fuel.get(mustNormalizeUrl("/api/v1/tracks/${result.get().track.id}/"))
          .authorize(context, oAuth)
          .awaitObjectResult(gsonDeserializerOf(Track::class.java))

        val favorites = favoritedRepository.fetch(Repository.Origin.Cache.origin)
          .map { it.data }
          .toList()
          .flatten()

        val track = trackResponse.get().apply {
          favorite = favorites.contains(id)
        }

        if (first) {
          CommandBus.send(Command.ReplaceQueue(listOf(track), true))
        } else {
          CommandBus.send(Command.AddToQueue(listOf(track)))
        }
      } catch (e: Exception) {
        withContext(Main) {
          context.toast(context.getString(R.string.radio_playback_error))
        }
      } finally {
        EventBus.send(Event.RadioStarted)
      }
    }
  }
}
