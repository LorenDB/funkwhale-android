package audio.funkwhale.ffa.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import audio.funkwhale.ffa.model.DownloadInfo
import audio.funkwhale.ffa.repositories.Repository
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.google.android.exoplayer2.offline.Download
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.openid.appauth.ClientSecretPost
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.coroutines.CoroutineContext

inline fun <D> Flow<Repository.Response<D>>.untilNetwork(
  scope: CoroutineScope,
  context: CoroutineContext = Main,
  crossinline callback: (data: List<D>, isCache: Boolean, page: Int, hasMore: Boolean) -> Unit
) {
  scope.launch(context) {
    collect { data ->
      callback(data.data, data.origin == Repository.Origin.Cache, data.page, data.hasMore)
    }
  }
}

fun <T> Int.onApi(block: () -> T) {
  if (Build.VERSION.SDK_INT >= this) {
    block()
  }
}

fun <T, U> Int.onApi(block: () -> T, elseBlock: (() -> U)) {
  if (Build.VERSION.SDK_INT >= this) {
    block()
  } else {
    elseBlock()
  }
}

fun Request.authorize(context: Context, oAuth: OAuth): Request {
  return runBlocking {
    this@authorize.apply {
      if (!Settings.isAnonymous()) {
        oAuth.state().let { state ->
          val old = state.accessToken
          val auth = ClientSecretPost(oAuth.state().clientSecret)
          val done = CompletableDeferred<Boolean>()
          val tokenService = oAuth.service(context)

          state.performActionWithFreshTokens(tokenService, auth) { token, _, e ->
            if (e != null) {
              Log.e("Request.authorize()", "performActionWithFreshToken failed: $e")
              if (e.type != 2 || e.code != 2002) {
                Log.e("Request.authorize()", Log.getStackTraceString(e))
                EventBus.send(Event.LogOut)
              }
            }
            if (token != old && token != null) {
              state.save()
            }
            header("Authorization", "Bearer ${oAuth.state().accessToken}")
            done.complete(true)
          }
          done.await()
          tokenService.dispose()
          return@runBlocking this
        }
      }
    }
  }
}

fun FuelError.formatResponseMessage(): String {
  return "${response.statusCode}: ${response.url}"
}

fun Download.getMetadata(): DownloadInfo? =
  Gson().fromJson(String(this.request.data), DownloadInfo::class.java)

val ISO_8601_DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

fun Date.format(): String {
  return ISO_8601_DATE_TIME_FORMAT.format(this)
}

fun String?.containsIgnoringCase(candidate: String): Boolean =
  this != null && this.lowercase().contains(candidate.lowercase())

inline fun <T, U, V, R> LiveData<T>.mergeWith(
  u: LiveData<U>,
  v: LiveData<V>,
  crossinline block: (valT: T, valU: U, valV: V) -> R
): LiveData<R> = MediatorLiveData<R>().apply {
  addSource(this@mergeWith) {
    if (u.value != null && v.value != null) {
      postValue(block(it, u.value!!, v.value!!))
    }
  }
  addSource(u) {
    if (this@mergeWith.value != null && u.value != null) {
      postValue(block(this@mergeWith.value!!, it, v.value!!))
    }
  }
  addSource(v) {
    if (this@mergeWith.value != null && u.value != null) {
      postValue(block(this@mergeWith.value!!, u.value!!, it))
    }
  }
}

inline fun <T, U, V, W, R> LiveData<T>.mergeWith(
  u: LiveData<U>,
  v: LiveData<V>,
  w: LiveData<W>,
  crossinline block: (valT: T, valU: U, valV: V, valW: W) -> R
): LiveData<R> = MediatorLiveData<R>().apply {
  addSource(this@mergeWith) {
    if (u.value != null && v.value != null && w.value != null) {
      postValue(block(it, u.value!!, v.value!!, w.value!!))
    }
  }
  addSource(u) {
    if (this@mergeWith.value != null && v.value != null && w.value != null) {
      postValue(block(this@mergeWith.value!!, it, v.value!!, w.value!!))
    }
  }
  addSource(v) {
    if (this@mergeWith.value != null && u.value != null && w.value != null) {
      postValue(block(this@mergeWith.value!!, u.value!!, it, w.value!!))
    }
  }
  addSource(w) {
    if (this@mergeWith.value != null && u.value != null && v.value != null) {
      postValue(block(this@mergeWith.value!!, u.value!!, v.value!!, it))
    }
  }
}

public fun String?.toIntOrElse(default: Int): Int = this?.toIntOrNull(radix = 10) ?: default

fun Activity.enableEdgeToEdge() {
  WindowCompat.setDecorFitsSystemWindows(window, false)
  
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    window.isNavigationBarContrastEnforced = false
  }
}
