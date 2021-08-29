package audio.funkwhale.ffa.koin

import android.content.Context
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.playback.CacheDataSourceFactoryProvider
import audio.funkwhale.ffa.playback.MediaSession
import audio.funkwhale.ffa.utils.AuthorizationServiceFactory
import audio.funkwhale.ffa.utils.OAuth
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.preference.PowerPreference
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun exoplayerModule(context: Context) = module {

  single<DatabaseProvider>(named("exoDatabase")) {
    ExoDatabaseProvider(context)
  }

  single {
    val cacheDataSourceFactoryProvider = get<CacheDataSourceFactoryProvider>()

    val exoDownloadCache = get<Cache>(named("exoDownloadCache"))
    val exoDatabase = get<DatabaseProvider>(named("exoDatabase"))
    val cacheDataSourceFactory = cacheDataSourceFactoryProvider.create(context)

    DownloadManager(context, exoDatabase, exoDownloadCache, cacheDataSourceFactory, Runnable::run)
  }

  single {
    CacheDataSourceFactoryProvider(
      get(),
      get(named("exoCache")),
      get(named("exoDownloadCache"))
    )
  }

  single<Cache>(named("exoDownloadCache")) {
    SimpleCache(
      context.cacheDir.resolve("downloads"),
      NoOpCacheEvictor(),
      get<DatabaseProvider>(named("exoDatabase"))
    )
  }

  single<Cache>(named("exoCache")) {
    val cacheSize = PowerPreference.getDefaultFile().getInt("media_cache_size", 1).toLong()
      .let { if (it == 0L) 0 else it * 1024 * 1024 * 1024 }
    SimpleCache(
      context.cacheDir.resolve("media"),
      LeastRecentlyUsedCacheEvictor(cacheSize),
      get<DatabaseProvider>(named("exoDatabase"))
    )
  }

  single { MediaSession(context) }
}

fun authModule() = module {
  single { OAuth(get()) }
  single { AuthorizationServiceFactory() }
}