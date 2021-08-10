package audio.funkwhale.ffa.utils

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.fragment.app.Fragment
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.fragments.BrowseFragment
import audio.funkwhale.ffa.repositories.Repository
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.google.android.exoplayer2.offline.Download
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.openid.appauth.ClientSecretPost
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

fun Fragment.onViewPager(block: Fragment.() -> Unit) {
  for (f in activity?.supportFragmentManager?.fragments ?: listOf()) {
    if (f is BrowseFragment) {
      f.block()
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

fun Picasso.maybeLoad(url: String?): RequestCreator {
  return if (url == null) load(R.drawable.cover)
  else load(url)
}

fun Request.authorize(context: Context, oAuth: OAuth): Request {
  return runBlocking {
    this@authorize.apply {
      if (!Settings.isAnonymous()) {
        oAuth.state().let { state ->
          val now = SystemClock.currentThreadTimeMillis()
          state.accessTokenExpirationTime?.let {
            Log.i("Request.authorize()", "Accesstoken expiration: ${it - now}")
          }
          val old = state.accessToken
          val auth = ClientSecretPost(oAuth.state().clientSecret)
          val done = CompletableDeferred<Boolean>()

          state.performActionWithFreshTokens(oAuth.service(context), auth) { token, _, _ ->
            if (token == old) {
              Log.i("Request.authorize()", "Accesstoken not renewed")
            }
            if (token != old && token != null) {
              state.save()
            }
            header("Authorization", "Bearer ${oAuth.state().accessToken}")
            done.complete(true)
          }
          done.await()
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
