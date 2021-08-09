package audio.funkwhale.ffa.repositories

import android.content.Context
import android.net.Uri
import audio.funkwhale.ffa.utils.*
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

class HttpUpstream<D : Any, R : OtterResponse<D>>(
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

      val url =
        Uri.parse(url)
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
              if (response.next != null) fetch(size + data.size).collect { emit(it) }
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
  }.flowOn(IO)

  private fun networkResponse(data: List<D>, page: Int, hasMore: Boolean) = Repository.Response(
    Repository.Origin.Network,
    data,
    page,
    hasMore
  )

  class GenericDeserializer<T : OtterResponse<*>>(val type: Type) : ResponseDeserializable<T> {
    override fun deserialize(reader: Reader): T? {
      return Gson().fromJson(reader, type)
    }
  }

  suspend fun get(context: Context, url: String): Result<R, FuelError> {
    return try {
      val request = Fuel.get(mustNormalizeUrl(url)).apply {
        authorize(context, oAuth)
      }
      val (_, _, result) = request.awaitObjectResponseResult(GenericDeserializer<R>(type))
      result
    } catch (e: Exception) {
      Result.error(FuelError.wrap(e))
    }
  }
}
