package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import audio.funkwhale.ffa.adapters.FavoriteListener
import audio.funkwhale.ffa.adapters.SearchAdapter
import audio.funkwhale.ffa.databinding.FragmentSearchBinding
import audio.funkwhale.ffa.model.Album
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.getMetadata
import audio.funkwhale.ffa.viewmodel.SearchViewModel
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {
  private lateinit var adapter: SearchAdapter
  private lateinit var binding: FragmentSearchBinding
  private val viewModel by activityViewModels<SearchViewModel>()
  private val noSearchYet = MutableLiveData(true)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentSearchBinding.inflate(layoutInflater, container, false)
    binding.lifecycleOwner = this
    binding.isLoadingData = viewModel.isLoadingData
    binding.hasResults = viewModel.hasResults
    binding.noSearchYet = noSearchYet
    return binding.root
  }

  override fun onResume() {
    super.onResume()
    binding.search.requestFocus()

    lifecycleScope.launch(Dispatchers.Main) {
      CommandBus.get().collect { command ->
        if (command is Command.AddToPlaylist) {

          if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            AddToPlaylistDialog.show(
              layoutInflater,
              requireActivity(),
              lifecycleScope,
              command.tracks
            )
          }
        }
      }
    }

    lifecycleScope.launch(Dispatchers.IO) {
      EventBus.get().collect { event ->
        if (event is Event.DownloadChanged) refreshDownloadedTrack(event.download)
      }
    }

    adapter =
      SearchAdapter(
        viewModel,
        this,
        SearchResultClickListener(),
        FavoriteListener(FavoritesRepository(requireContext()))
      ).also {
        binding.results.layoutManager = LinearLayoutManager(requireContext())
        binding.results.adapter = it
      }

    binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

      override fun onQueryTextSubmit(query: String): Boolean {
        binding.search.clearFocus()
        noSearchYet.value = false
        viewModel.query.postValue(query)

        return true
      }

      override fun onQueryTextChange(newText: String) = true
    })
  }

  override fun onDestroy() {
    super.onDestroy()
    // Empty the research to prevent result recall the next time
    viewModel.query.value = ""
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
      findNavController().navigate(SearchFragmentDirections.searchToAlbums(artist))
    }

    override fun onAlbumClick(holder: View?, album: Album) {
      findNavController().navigate(SearchFragmentDirections.searchToTracks(album))
    }
  }
}
