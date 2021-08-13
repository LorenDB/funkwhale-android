package audio.funkwhale.ffa.playback

import android.content.Context
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.Settings
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.util.Util

class CacheDataSourceFactoryProvider(
    private val oAuth: OAuth,
    private val exoCache: Cache,
    private val exoDownloadCache: Cache
) {

  fun create(context: Context): CacheDataSourceFactory {

    val playbackCache =
        CacheDataSourceFactory(exoCache, createDatasourceFactory(context, oAuth))

    return CacheDataSourceFactory(
        exoDownloadCache,
        playbackCache,
        FileDataSource.Factory(),
        null,
        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
        null
    )
  }

  private fun createDatasourceFactory(context: Context, oAuth: OAuth): DataSource.Factory {
    val http = DefaultHttpDataSourceFactory(
        Util.getUserAgent(context, context.getString(R.string.app_name))
    )
    return if (!Settings.isAnonymous()) {
        OAuth2DatasourceFactory(context, http, oAuth)
    } else {
      http
    }
  }
}