package audio.funkwhale.ffa.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import audio.funkwhale.ffa.model.FFAResponse
import audio.funkwhale.ffa.utils.AppContext
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.RefreshError
import audio.funkwhale.ffa.utils.Settings
import audio.funkwhale.ffa.utils.authorize
import audio.funkwhale.ffa.utils.mustNormalizeUrl
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResponseResult
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.Reader
import java.lang.reflect.Type
import kotlin.math.ceil

class HttpUpstream<D : Any, R : FFAResponse<D>>(
  val context: Context?,
  val behavior: Behavior,
  private val url: String,
  private val type: Type,
  private val oAuth: OAuth
) : Upstream<D> {

  enum class Behavior {
    Single,
    AtOnce,
    Progressive
  }

  override fun fetch(size: Int): Flow<Repository.Response<D>> = flow<Repository.Response<D>> {

    context?.let {
      if (behavior == Behavior.Single && size != 0) return@flow

      val page = ceil(size / AppContext.PAGE_SIZE.toDouble()).toInt() + 1

      fetchPage(page, size).collect { emit(it) }
    }
  }.flowOn(IO)

  private fun fetchPage(page: Int, size: Int): Flow<Repository.Response<D>> = flow {
    context?.let {
      val url = Uri.parse(url)
        .buildUpon()
        .appendQueryParameter("page_size", AppContext.PAGE_SIZE.toString())
        .appendQueryParameter("page", page.toString())
        .appendQueryParameter("scope", Settings.getScopes().joinToString(" "))
        .build()
        .toString()

      get(it, url).fold(
        { response ->
          val data = response.getData()

          when (behavior) {
            Behavior.Single -> emit(networkResponse(data, page, false))
            Behavior.Progressive -> emit(networkResponse(data, page, response.next != null))
            else -> {
              emit(networkResponse(data, page, response.next != null))
              if (response.next != null) fetchPage(page + 1, size + data.size).collect { emit(it) }
            }
          }
        },
        { error ->
          when (error.exception) {
            is RefreshError -> EventBus.send(Event.LogOut)
            else -> emit(Repository.Response(Repository.Origin.Network, listOf(), page, false))
          }
        }
      )
    }
  }

  private fun networkResponse(data: List<D>, page: Int, hasMore: Boolean) = Repository.Response(
    Repository.Origin.Network,
    data,
    page,
    hasMore
  )

  class GenericDeserializer<T : FFAResponse<*>>(val type: Type) : ResponseDeserializable<T> {
    override fun deserialize(reader: Reader): T? {
      return Gson().fromJson(reader, type)
    }
  }

  suspend fun get(context: Context, url: String): Result<R, FuelError> {
    Log.i("HttpUpstream", "get() - url: $url")
    return try {
      val normalizedUrl = mustNormalizeUrl(url)
      val request = Fuel.get(normalizedUrl).apply {
        authorize(context, oAuth)
      }
      val (_, response, result) = request.awaitObjectResponseResult(GenericDeserializer<R>(type))
      if (response.statusCode == 401) {
        return retryGet(normalizedUrl)
      }
      result
    } catch (e: Exception) {
      Result.error(FuelError.wrap(e))
    }
  }

  private suspend fun retryGet(url: String): Result<R, FuelError> {
    Log.i("HttpUpstream", "retryGet() - url: $url")
    context?.let {
      return try {
        oAuth.refreshAccessToken(context)
        val request = Fuel.get(url).apply {
          authorize(context, oAuth)
        }
        val (_, _, result) = request.awaitObjectResponseResult(GenericDeserializer<R>(type))
        result
      } catch (e: Exception) {
        Result.error(FuelError.wrap(e))
      }
    }
    throw IllegalStateException("Illegal state: context is null")
  }
}
