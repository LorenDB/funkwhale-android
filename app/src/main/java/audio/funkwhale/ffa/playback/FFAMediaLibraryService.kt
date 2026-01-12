package audio.funkwhale.ffa.playback

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.model.Album
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.model.Playlist
import audio.funkwhale.ffa.model.Radio
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.AlbumsRepository
import audio.funkwhale.ffa.repositories.ArtistsRepository
import audio.funkwhale.ffa.repositories.ArtistTracksRepository
import audio.funkwhale.ffa.repositories.PlaylistTracksRepository
import audio.funkwhale.ffa.repositories.PlaylistsRepository
import audio.funkwhale.ffa.repositories.RadiosRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.repositories.TracksRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class FFAMediaLibraryService : MediaBrowserServiceCompat() {

  private val mediaSession: MediaSession by inject()
  private val scope = CoroutineScope(Job() + Main)

  companion object {
    const val ROOT_ID = "root"
    const val ARTISTS_ID = "artists"
    const val ALBUMS_ID = "albums"
    const val PLAYLISTS_ID = "playlists"
    const val RADIOS_ID = "radios"
    const val QUEUE_ID = "queue"

    const val ARTIST_PREFIX = "artist_"
    const val ALBUM_PREFIX = "album_"
    const val PLAYLIST_PREFIX = "playlist_"
  }

  override fun onCreate() {
    super.onCreate()
    sessionToken = mediaSession.session.sessionToken

    mediaSession.ensureServiceStarted()
  }

  override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
    return BrowserRoot(ROOT_ID, null)
  }

  override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
    result.detach()
    scope.launch {
      val items = when {
        parentId == ROOT_ID -> getRootItems()
        parentId == ARTISTS_ID -> getArtists()
        parentId == ALBUMS_ID -> getAlbums()
        parentId == PLAYLISTS_ID -> getPlaylists()
        parentId == RADIOS_ID -> getRadios()
        parentId == QUEUE_ID -> getQueue()
        parentId.startsWith(ARTIST_PREFIX) -> getArtistTracks(parentId.removePrefix(ARTIST_PREFIX).toInt())
        parentId.startsWith(ALBUM_PREFIX) -> getAlbumTracks(parentId.removePrefix(ALBUM_PREFIX).toInt())
        parentId.startsWith(PLAYLIST_PREFIX) -> getPlaylistTracks(parentId.removePrefix(PLAYLIST_PREFIX).toInt())
        else -> mutableListOf()
      }
      result.sendResult(items)
    }
  }

  private fun getRootItems(): MutableList<MediaItem> {
    return mutableListOf(
      createBrowsableItem(ARTISTS_ID, getString(R.string.artists)),
      createBrowsableItem(ALBUMS_ID, getString(R.string.albums)),
      createBrowsableItem(PLAYLISTS_ID, getString(R.string.playlists)),
      createBrowsableItem(RADIOS_ID, getString(R.string.radios)),
      createBrowsableItem(QUEUE_ID, getString(R.string.playback_queue))
    )
  }

  private suspend fun <D : Any> fetchAll(repository: Repository<D, *>): List<D> {
    val all = mutableListOf<D>()
    repository.fetch(Repository.Origin.Cache.origin).collect { all.addAll(it.data) }
    if (all.isNotEmpty()) return all

    var size = 0
    while (true) {
      var hasMore = false
      var received = 0
      repository.fetch(Repository.Origin.Network.origin, size).collect { response ->
        all.addAll(response.data)
        hasMore = response.hasMore
        received += response.data.size
      }
      if (!hasMore || received == 0) break
      size = all.size
    }
    return all
  }

  private suspend fun getArtists(): MutableList<MediaItem> {
    val repository = ArtistsRepository(this@FFAMediaLibraryService)
    return fetchAll(repository).map { artist ->
      createBrowsableItem(ARTIST_PREFIX + artist.id, artist.name, null, artist.cover())
    }.toMutableList()
  }

  private suspend fun getAlbums(): MutableList<MediaItem> {
    val repository = AlbumsRepository(this@FFAMediaLibraryService)
    return fetchAll(repository).map { album ->
      createBrowsableItem(ALBUM_PREFIX + album.id, album.title, album.artist.name, album.cover())
    }.toMutableList()
  }

  private suspend fun getPlaylists(): MutableList<MediaItem> {
    val repository = PlaylistsRepository(this@FFAMediaLibraryService)
    return fetchAll(repository).map { playlist ->
      createBrowsableItem(PLAYLIST_PREFIX + playlist.id, playlist.name, null, playlist.album_covers.firstOrNull())
    }.toMutableList()
  }

  private suspend fun getRadios(): MutableList<MediaItem> {
    val repository = RadiosRepository(this@FFAMediaLibraryService)
    return fetchAll(repository).map { radio ->
      createPlayableItem("radio_${radio.id}", radio.name)
    }.toMutableList()
  }

  private suspend fun getQueue(): MutableList<MediaItem> {
    val channel = RequestBus.send(Request.GetQueue)
    val response = channel.receive()
    val items = if (response is Response.Queue) {
      response.queue.map { track ->
        createPlayableItem("queue_0_track_${track.id}", track.title, track.artist.name, track.cover())
      }.toMutableList()
    } else {
      mutableListOf()
    }
    channel.close()
    return items
  }

  private suspend fun getArtistTracks(artistId: Int): MutableList<MediaItem> {
    val repository = ArtistTracksRepository(this@FFAMediaLibraryService, artistId)
    return fetchAll(repository).map { track ->
      createPlayableItem("artist_${artistId}_track_${track.id}", track.title, track.artist.name, track.cover())
    }.toMutableList()
  }

  private suspend fun getAlbumTracks(albumId: Int): MutableList<MediaItem> {
    val repository = TracksRepository(this@FFAMediaLibraryService, albumId)
    return fetchAll(repository).map { track ->
      createPlayableItem("album_${albumId}_track_${track.id}", track.title, track.artist.name, track.cover())
    }.toMutableList()
  }

  private suspend fun getPlaylistTracks(playlistId: Int): MutableList<MediaItem> {
    val repository = PlaylistTracksRepository(this@FFAMediaLibraryService, playlistId)
    return fetchAll(repository).map { playlistTrack ->
      val track = playlistTrack.track
      createPlayableItem("playlist_${playlistId}_track_${track.id}", track.title, track.artist.name, track.cover())
    }.toMutableList()
  }

  private fun createBrowsableItem(id: String, title: String, subtitle: String? = null, coverUrl: String? = null): MediaItem {
    val builder = MediaDescriptionCompat.Builder()
      .setMediaId(id)
      .setTitle(title)
      .setSubtitle(subtitle)

    maybeNormalizeUrl(coverUrl)?.let { url ->
      builder.setIconUri(Uri.parse(url))
      // Some Android Auto versions prefer local Bitmaps over Uris
      try {
        runBlocking(IO) {
          builder.setIconBitmap(CoverArt.requestCreator(url).get())
        }
      } catch (_: Exception) {}
    }

    return MediaItem(builder.build(), MediaItem.FLAG_BROWSABLE)
  }

  private fun createPlayableItem(id: String, title: String, subtitle: String? = null, coverUrl: String? = null): MediaItem {
    val builder = MediaDescriptionCompat.Builder()
      .setMediaId(id)
      .setTitle(title)
      .setSubtitle(subtitle)

    maybeNormalizeUrl(coverUrl)?.let { url ->
      builder.setIconUri(Uri.parse(url))
      try {
        runBlocking(IO) {
          builder.setIconBitmap(CoverArt.requestCreator(url).get())
        }
      } catch (_: Exception) {}
    }

    return MediaItem(builder.build(), MediaItem.FLAG_PLAYABLE)
  }

  override fun onSearch(query: String, extras: Bundle?, result: Result<MutableList<MediaItem>>) {
    result.sendResult(mutableListOf())
  }
}
