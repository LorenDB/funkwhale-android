package audio.funkwhale.ffa.playback

import android.content.Context
import android.net.Uri
import audio.funkwhale.ffa.model.QueueCache
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.FFACache
import audio.funkwhale.ffa.utils.log
import audio.funkwhale.ffa.utils.mustNormalizeUrl
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.gson.Gson
import org.koin.java.KoinJavaComponent.inject

class QueueManager(val context: Context) {

  private val cacheDataSourceFactoryProvider: CacheDataSourceFactoryProvider by inject(
    CacheDataSourceFactoryProvider::class.java
  )

  var metadata: MutableList<Track> = mutableListOf()
  val dataSources = ConcatenatingMediaSource()
  var current = -1

  init {
    FFACache.getLine(context, "queue")?.let { json ->
      gsonDeserializerOf(QueueCache::class.java).deserialize(json.reader())?.let { cache ->
        metadata = cache.data.toMutableList()

        val factory = cacheDataSourceFactoryProvider.create(context)

        dataSources.addMediaSources(
          metadata.map { track ->
            val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")

            val mediaItem = MediaItem.fromUri(Uri.parse(url)).buildUpon().setTag(track.title).build()
            ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
          }
        )
      }
    }

    FFACache.getLine(context, "current")?.let {
      current = it.toInt()
    }
  }

  private fun persist() {
    FFACache.set(
      context,
      "queue",
      Gson().toJson(QueueCache(metadata)).toString()
    )
  }

  fun replace(tracks: List<Track>) {
    tracks.map { it.formatted }.log("Replacing queue with ${tracks.size} tracks")
    val factory = cacheDataSourceFactoryProvider.create(context)
    val sources = tracks.map { track ->
      val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")
      val mediaItem = MediaItem.fromUri(Uri.parse(url)).buildUpon().setTag(track.title).build()
      ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
    }

    metadata = tracks.toMutableList()
    dataSources.clear()
    dataSources.addMediaSources(sources)

    persist()

    EventBus.send(Event.QueueChanged)
  }

  fun append(tracks: List<Track>) {
    tracks.map { it.formatted }.log("Appending ${tracks.size} tracks")
    val factory = cacheDataSourceFactoryProvider.create(context)
    val missingTracks = tracks.filter { metadata.indexOf(it) == -1 }

    val sources = missingTracks.map { track ->
      val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")

      val mediaItem = MediaItem.fromUri(Uri.parse(url)).buildUpon().setTag(track.title).build()
      ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
    }

    metadata.addAll(tracks)
    dataSources.addMediaSources(sources)

    persist()

    EventBus.send(Event.QueueChanged)
  }

  fun insertNext(track: Track) {
    track.formatted.log("Next track")
    val factory = cacheDataSourceFactoryProvider.create(context)
    val url = mustNormalizeUrl(track.bestUpload()?.listen_url ?: "")

    if (metadata.indexOf(track) == -1) {
      val mediaItem = MediaItem.fromUri(Uri.parse(url)).buildUpon().setTag(track.title).build()
      ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem).let {
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
    track.formatted.log("Removing track")
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

  fun currentIndex(): Int = (if (current == -1) 0 else current)

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
