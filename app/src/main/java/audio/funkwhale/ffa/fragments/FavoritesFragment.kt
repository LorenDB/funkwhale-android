package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.adapters.FavoritesAdapter
import audio.funkwhale.ffa.databinding.FragmentFavoritesBinding
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.TracksRepository
import audio.funkwhale.ffa.utils.*
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class FavoritesFragment : FFAFragment<Track, FavoritesAdapter>() {

  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)

  private var _binding: FragmentFavoritesBinding? = null
  private val binding get() = _binding!!

  override val recycler: RecyclerView get() = binding.favorites
  override val alwaysRefresh = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    adapter = FavoritesAdapter(layoutInflater, context, FavoriteListener())
    repository = FavoritesRepository(context)
    watchEventBus()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentFavoritesBinding.inflate(inflater)
    swiper = binding.swiper
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onResume() {
    super.onResume()

    lifecycleScope.launch(IO) {
      RequestBus.send(Request.GetCurrentTrack).wait<Response.CurrentTrack>()?.let { response ->
        withContext(Main) {
          adapter.currentTrack = response.track
          adapter.notifyDataSetChanged()
        }
      }

      refreshDownloadedTracks()
    }

    binding.play.setOnClickListener {
      CommandBus.send(Command.ReplaceQueue(adapter.data.shuffled()))
    }
  }

  private fun watchEventBus() {
    lifecycleScope.launch(Main) {
      EventBus.get().collect { message ->
        when (message) {
          is Event.DownloadChanged -> refreshDownloadedTrack(message.download)
        }
      }
    }

    lifecycleScope.launch(Main) {
      CommandBus.get().collect { command ->
        when (command) {
          is Command.RefreshTrack -> refreshCurrentTrack(command.track)
        }
      }
    }
  }

  private suspend fun refreshDownloadedTracks() {
    val downloaded = TracksRepository.getDownloadedIds(exoDownloadManager) ?: listOf()

    withContext(Main) {
      adapter.data = adapter.data.map {
        it.downloaded = downloaded.contains(it.id)
        it
      }.toMutableList()

      adapter.notifyDataSetChanged()
    }
  }

  private suspend fun refreshDownloadedTrack(download: Download) {
    if (download.state == Download.STATE_COMPLETED) {
      download.getMetadata()?.let { info ->
        adapter.data.withIndex().associate { it.value to it.index }.filter { it.key.id == info.id }
          .toList().getOrNull(0)?.let { match ->
            withContext(Main) {
              adapter.data[match.second].downloaded = true
              adapter.notifyItemChanged(match.second)
            }
          }
      }
    }
  }

  private fun refreshCurrentTrack(track: Track?) {
    track?.let {
      adapter.currentTrack?.current = false
      adapter.currentTrack = track.apply {
        current = true
      }
      adapter.notifyDataSetChanged()
    }
  }

  inner class FavoriteListener : FavoritesAdapter.OnFavoriteListener {
    override fun onToggleFavorite(id: Int, state: Boolean) {
      (repository as? FavoritesRepository)?.let { repository ->
        when (state) {
          true -> repository.addFavorite(id)
          false -> repository.deleteFavorite(id)
        }
      }
    }
  }
}
