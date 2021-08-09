package audio.funkwhale.ffa.playback

import android.content.Context
import android.net.Uri
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.utils.*
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import org.koin.java.KoinJavaComponent.inject

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

class QueueManager(val context: Context) {

  private val cacheDataSourceFactoryProvider: CacheDataSourceFactoryProvider by inject(
    CacheDataSourceFactoryProvider::class.java
  )

  var metadata: MutableList<Track> = mutableListOf()
  val dataSources = ConcatenatingMediaSource()
  var current = -1

  init {
    FFACache.get(context, "queue")?.let { json ->
      gsonDeserializerOf(QueueCache::class.java).deserialize(json)?.let { cache ->
        metadata = cache.data.toMutableList()

        val factory = cacheDataSourceFactoryProvider.create(context)

        dataSources.addMediaSources(metadata.map { track ->
          val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")

          ProgressiveMediaSource.Factory(factory).setTag(track.title)
            .createMediaSource(Uri.parse(url))
        })
      }
    }

    FFACache.get(context, "current")?.let { string ->
      current = string.readLine().toInt()
    }
  }

  private fun persist() {
    FFACache.set(
      context,
      "queue",
      Gson().toJson(QueueCache(metadata)).toByteArray()
    )
  }

  fun replace(tracks: List<Track>) {
    val factory = cacheDataSourceFactoryProvider.create(context)
    val sources = tracks.map { track ->
      val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")

      ProgressiveMediaSource.Factory(factory).setTag(track.title).createMediaSource(Uri.parse(url))
    }

    metadata = tracks.toMutableList()
    dataSources.clear()
    dataSources.addMediaSources(sources)

    persist()

    EventBus.send(Event.QueueChanged)
  }

  fun append(tracks: List<Track>) {
    val factory = cacheDataSourceFactoryProvider.create(context)
    val missingTracks = tracks.filter { metadata.indexOf(it) == -1 }

    val sources = missingTracks.map { track ->
      val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")

      ProgressiveMediaSource.Factory(factory).createMediaSource(Uri.parse(url))
    }

    metadata.addAll(tracks)
    dataSources.addMediaSources(sources)

    persist()

    EventBus.send(Event.QueueChanged)
  }

  fun insertNext(track: Track) {
    val factory = cacheDataSourceFactoryProvider.create(context)
    val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")

    if (metadata.indexOf(track) == -1) {
      ProgressiveMediaSource.Factory(factory).createMediaSource(Uri.parse(url)).let {
        dataSources.addMediaSource(current + 1, it)
        metadata.add(current + 1, track)
      }
    } else {
      move(metadata.indexOf(track), current + 1)
    }

    persist()

    EventBus.send(Event.QueueChanged)
  }

  fun remove(track: Track) {
    metadata.indexOf(track).let {
      if (it < 0) {
        return
      }

      dataSources.removeMediaSource(it)
      metadata.removeAt(it)

      if (it == current) {
        CommandBus.send(Command.NextTrack)
      }

      if (it < current) {
        current--
      }
    }

    if (metadata.isEmpty()) {
      current = -1
    }

    persist()

    EventBus.send(Event.QueueChanged)
  }

  fun move(oldPosition: Int, newPosition: Int) {
    dataSources.moveMediaSource(oldPosition, newPosition)
    metadata.add(newPosition, metadata.removeAt(oldPosition))

    persist()
  }

  fun get() = metadata.mapIndexed { index, track ->
    track.current = index == current
    track
  }

  fun get(index: Int): Track = metadata[index]

  fun current(): Track? {
    if (current == -1) {
      return metadata.getOrNull(0)
    }

    return metadata.getOrNull(current)
  }

  fun clear() {
    metadata = mutableListOf()
    dataSources.clear()
    current = -1

    persist()
  }

  fun shuffle() {
    if (metadata.size < 2) return

    if (current == -1) {
      replace(metadata.shuffled())
    } else {
      move(current, 0)
      current = 0

      val shuffled =
        metadata
          .drop(1)
          .shuffled()

      while (metadata.size > 1) {
        dataSources.removeMediaSource(metadata.size - 1)
        metadata.removeAt(metadata.size - 1)
      }

      append(shuffled)
    }

    persist()

    EventBus.send(Event.QueueChanged)
  }
}
