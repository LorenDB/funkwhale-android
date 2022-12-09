package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import audio.funkwhale.ffa.adapters.FavoriteListener
import audio.funkwhale.ffa.adapters.TracksAdapter
import audio.funkwhale.ffa.databinding.PartialQueueBinding
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import audio.funkwhale.ffa.utils.wait
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LandscapeQueueFragment : Fragment() {

  private var _binding: PartialQueueBinding? = null
  private val binding get() = _binding!!

  lateinit var favoritesRepository: FavoritesRepository

  private var adapter: TracksAdapter? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    favoritesRepository = FavoritesRepository(context)

    watchEventBus()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = PartialQueueBinding.inflate(inflater)

    return binding.root.apply {
      adapter = TracksAdapter(
        layoutInflater,
        context,
        fromQueue = true,
        favoriteListener = FavoriteListener(favoritesRepository)
      ).also {
        binding.queue.layoutManager = LinearLayoutManager(context)
        binding.queue.adapter = it
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onResume() {
    super.onResume()

    binding.queue.visibility = View.GONE
    binding.placeholder.visibility = View.VISIBLE

    binding.queueShuffle.setOnClickListener {
      CommandBus.send(Command.ShuffleQueue)
    }

    binding.queueSave.setOnClickListener {
      adapter?.data?.let {
        CommandBus.send(Command.AddToPlaylist(it))
      }
    }

    binding.queueClear.setOnClickListener {
      CommandBus.send(Command.ClearQueue)
    }

    refresh()
  }

  private fun refresh() {
    activity?.lifecycleScope?.launch(Main) {
      RequestBus.send(Request.GetQueue).wait<Response.Queue>()?.let { response ->
        adapter?.let {
          it.setUnfilteredData(response.queue.toMutableList())
          it.notifyDataSetChanged()

          if (it.data.isEmpty()) {
            binding.queue.visibility = View.GONE
            binding.placeholder.visibility = View.VISIBLE
          } else {
            binding.queue.visibility = View.VISIBLE
            binding.placeholder.visibility = View.GONE
          }
        }
      }
    }
  }

  private fun watchEventBus() {
    activity?.lifecycleScope?.launch(Main) {
      EventBus.get().collect { message ->
        if (message is Event.QueueChanged) refresh()
      }
    }

    activity?.lifecycleScope?.launch(Main) {
      CommandBus.get().collect { command ->
        if (command is Command.RefreshTrack) refresh()
      }
    }
  }
}
