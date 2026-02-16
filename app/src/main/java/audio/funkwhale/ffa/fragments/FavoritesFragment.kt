package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.adapters.FavoriteListener
import audio.funkwhale.ffa.adapters.FavoritesAdapter
import audio.funkwhale.ffa.databinding.FragmentFavoritesBinding
import audio.funkwhale.ffa.model.Favorite
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.TracksRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import audio.funkwhale.ffa.utils.getMetadata
import audio.funkwhale.ffa.utils.wait
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class FavoritesFragment : FFAFragment<Favorite, FavoritesAdapter>() {

  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)

  private var _binding: FragmentFavoritesBinding? = null
  private val binding get() = _binding!!

  override val recycler: RecyclerView get() = binding.favorites
  override val alwaysRefresh = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    repository = FavoritesRepository(context)
    adapter = FavoritesAdapter(layoutInflater, context, FavoriteListener(repository()))
    watchEventBus()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentFavoritesBinding.inflate(inflater)
    swiper = binding.swiper
    binding.filterTracks.addTextChangedListener(object : TextWatcher {

      override fun afterTextChanged(s: Editable) {

        adapter.applyFilter()
        adapter.notifyDataSetChanged()
      }

      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        adapter.filter = s.toString()
      }
    })

    binding.favoritesMenu.setOnClickListener { view ->
      PopupMenu(requireContext(), view).apply {
        inflate(R.menu.favorites)
        setOnMenuItemClickListener { item ->
          when (item.itemId) {
            R.id.download_all -> {
              CommandBus.send(Command.PinTracks(adapter.data.map { it.track }))
              true
            }
            else -> false
          }
        }
        show()
      }
    }

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

      refreshFavoritedTracks()
      refreshDownloadedTracks()
    }

    binding.play.setOnClickListener {
      CommandBus.send(Command.ReplaceQueue(adapter.data.map { it.track }.shuffled()))
    }
  }

  private fun watchEventBus() {
    lifecycleScope.launch(Main) {
      EventBus.get().collect { event ->
        if (event is Event.DownloadChanged) refreshDownloadedTrack(event.download)
      }
    }

    lifecycleScope.launch(Main) {
      CommandBus.get().collect { command ->
        if (command is Command.RefreshTrack) refreshCurrentTrack(command.track)
      }
    }
  }

  private fun refreshFavoritedTracks() {
    lifecycleScope.launch(Main) {
      update()
    }
  }

  private suspend fun refreshDownloadedTracks() {
    val downloaded = TracksRepository.getDownloadedIds(exoDownloadManager) ?: listOf()

    withContext(Main) {
      val data = adapter.data.map {
        it.track.downloaded = downloaded.contains(it.id)
        it
      }.toMutableList()

      adapter.setUnfilteredData(data)

      adapter.notifyDataSetChanged()
    }
  }

  private suspend fun refreshDownloadedTrack(download: Download) {
    if (download.state == Download.STATE_COMPLETED) {
      download.getMetadata()?.let { info ->
        adapter.data.withIndex().associate { it.value to it.index }.filter { it.key.id == info.id }
          .toList().getOrNull(0)?.let { match ->
            withContext(Main) {
              adapter.data[match.second].track.downloaded = true
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
}
