package audio.funkwhale.ffa.playback

import android.content.Context
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.Settings
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.util.Util

class CacheDataSourceFactoryProvider(
  private val oAuth: OAuth,
  private val exoCache: Cache,
  private val exoDownloadCache: Cache
) {

  fun create(context: Context): CacheDataSource.Factory {

    val playbackCache = CacheDataSource.Factory().apply {
      setCache(exoCache)
      setUpstreamDataSourceFactory(createDatasourceFactory(context, oAuth))
    }

    return CacheDataSource.Factory().apply {
      setCache(exoDownloadCache)
      setUpstreamDataSourceFactory(playbackCache)
      setCacheReadDataSourceFactory(FileDataSource.Factory())
      setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
  }

  private fun createDatasourceFactory(context: Context, oAuth: OAuth): DataSource.Factory {
    val http = DefaultHttpDataSource.Factory().apply {
      setUserAgent(Util.getUserAgent(context, context.getString(R.string.app_name)))
    }
    return if (!Settings.isAnonymous()) {
      OAuth2DatasourceFactory(context, http, oAuth)
    } else {
      http
    }
  }
}
