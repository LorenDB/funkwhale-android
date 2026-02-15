package audio.funkwhale.ffa.koin

import android.content.Context
import audio.funkwhale.ffa.playback.CacheDataSourceFactoryProvider
import audio.funkwhale.ffa.playback.MediaSession
import audio.funkwhale.ffa.utils.AuthorizationServiceFactory
import audio.funkwhale.ffa.utils.OAuth
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.preference.PowerPreference
import org.koin.core.qualifier.named
import org.koin.dsl.module

@OptIn(UnstableApi::class)
fun exoplayerModule(context: Context) = module {

  single<DatabaseProvider>(named("exoDatabase")) {
    StandaloneDatabaseProvider(context)
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
