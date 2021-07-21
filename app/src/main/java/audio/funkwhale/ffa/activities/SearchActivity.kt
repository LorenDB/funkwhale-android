package audio.funkwhale.ffa.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import audio.funkwhale.ffa.adapters.SearchAdapter
import audio.funkwhale.ffa.databinding.ActivitySearchBinding
import audio.funkwhale.ffa.fragments.AddToPlaylistDialog
import audio.funkwhale.ffa.fragments.AlbumsFragment
import audio.funkwhale.ffa.fragments.ArtistsFragment
import audio.funkwhale.ffa.repositories.*
import audio.funkwhale.ffa.utils.*
import com.google.android.exoplayer2.offline.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.*

class SearchActivity : AppCompatActivity() {
  private lateinit var adapter: SearchAdapter

  private lateinit var artistsRepository: ArtistsSearchRepository
  private lateinit var albumsRepository: AlbumsSearchRepository
  private lateinit var tracksRepository: TracksSearchRepository
  private lateinit var favoritesRepository: FavoritesRepository
  private lateinit var binding: ActivitySearchBinding

  var done = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivitySearchBinding.inflate(layoutInflater)

    setContentView(binding.root)

    adapter =
      SearchAdapter(layoutInflater, this, SearchResultClickListener(), FavoriteListener()).also {
        binding.results.layoutManager = LinearLayoutManager(this)
        binding.results.adapter = it
      }

    binding.search.requestFocus()
  }

  override fun onResume() {
    super.onResume()

    lifecycleScope.launch(Dispatchers.Main) {
      CommandBus.get().collect { command ->
        when (command) {
          is Command.AddToPlaylist -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            AddToPlaylistDialog.show(
              layoutInflater,
              this@SearchActivity,
              lifecycleScope,
              command.tracks
            )
          }
        }
      }
    }

    lifecycleScope.launch(Dispatchers.IO) {
      EventBus.get().collect { message ->
        when (message) {
          is Event.DownloadChanged -> refreshDownloadedTrack(message.download)
        }
      }
    }

    artistsRepository = ArtistsSearchRepository(this@SearchActivity, "")
    albumsRepository = AlbumsSearchRepository(this@SearchActivity, "")
    tracksRepository = TracksSearchRepository(this@SearchActivity, "")
    favoritesRepository = FavoritesRepository(this@SearchActivity)

    binding.search.setOnQueryTextListener(object :
      androidx.appcompat.widget.SearchView.OnQueryTextListener {
      override fun onQueryTextSubmit(rawQuery: String?): Boolean {
        binding.search.clearFocus()

        rawQuery?.let {
          done = 0

          val query = URLEncoder.encode(it, "UTF-8")

          artistsRepository.query = query.lowercase(Locale.ROOT)
          albumsRepository.query = query.lowercase(Locale.ROOT)
          tracksRepository.query = query.lowercase(Locale.ROOT)

          binding.searchSpinner.visibility = View.VISIBLE
          binding.searchEmpty.visibility = View.GONE
          binding.searchNoResults.visibility = View.GONE

          adapter.artists.clear()
          adapter.albums.clear()
          adapter.tracks.clear()
          adapter.notifyDataSetChanged()

          artistsRepository.fetch(Repository.Origin.Network.origin)
            .untilNetwork(lifecycleScope) { artists, _, _, _ ->
              done++

              adapter.artists.addAll(artists)
              refresh()
            }

          albumsRepository.fetch(Repository.Origin.Network.origin)
            .untilNetwork(lifecycleScope) { albums, _, _, _ ->
              done++

              adapter.albums.addAll(albums)
              refresh()
            }

          tracksRepository.fetch(Repository.Origin.Network.origin)
            .untilNetwork(lifecycleScope) { tracks, _, _, _ ->
              done++

              adapter.tracks.addAll(tracks)
              refresh()
            }
        }

        return true
      }

      override fun onQueryTextChange(newText: String?) = true
    })
  }

  private fun refresh() {
    adapter.notifyDataSetChanged()

    if (adapter.artists.size + adapter.albums.size + adapter.tracks.size == 0) {
      binding.searchNoResults.visibility = View.VISIBLE
    } else {
      binding.searchNoResults.visibility = View.GONE
    }

    if (done == 3) {
      binding.searchSpinner.visibility = View.INVISIBLE
    }
  }

  private suspend fun refreshDownloadedTrack(download: Download) {
    if (download.state == Download.STATE_COMPLETED) {
      download.getMetadata()?.let { info ->
        adapter.tracks.withIndex().associate { it.value to it.index }
          .filter { it.key.id == info.id }.toList().getOrNull(0)?.let { match ->
            withContext(Dispatchers.Main) {
              adapter.tracks[match.second].downloaded = true
              adapter.notifyItemChanged(
                adapter.getPositionOf(
                  SearchAdapter.ResultType.Track,
                  match.second
                )
              )
            }
          }
      }
    }
  }

  inner class SearchResultClickListener : SearchAdapter.OnSearchResultClickListener {
    override fun onArtistClick(holder: View?, artist: Artist) {
      ArtistsFragment.openAlbums(this@SearchActivity, artist)
    }

    override fun onAlbumClick(holder: View?, album: Album) {
      AlbumsFragment.openTracks(this@SearchActivity, album)
    }
  }

  inner class FavoriteListener : SearchAdapter.OnFavoriteListener {
    override fun onToggleFavorite(id: Int, state: Boolean) {
      when (state) {
        true -> favoritesRepository.addFavorite(id)
        false -> favoritesRepository.deleteFavorite(id)
      }
    }
  }
}
