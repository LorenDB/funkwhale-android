package audio.funkwhale.ffa.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.adapters.TracksAdapter
import audio.funkwhale.ffa.databinding.FragmentQueueBinding
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import audio.funkwhale.ffa.utils.wait
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class QueueFragment : BottomSheetDialogFragment() {

  private var _binding: FragmentQueueBinding? = null
  private val binding get() = _binding!!

  private var adapter: TracksAdapter? = null

  lateinit var favoritesRepository: FavoritesRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    favoritesRepository = FavoritesRepository(context)

    setStyle(DialogFragment.STYLE_NORMAL, R.style.AppTheme_FloatingBottomSheet)

    watchEventBus()
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    return super.onCreateDialog(savedInstanceState).apply {
      setOnShowListener {
        findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
          BottomSheetBehavior.from(it).skipCollapsed = true
        }
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentQueueBinding.inflate(inflater)
    return binding.root.apply {
      adapter = TracksAdapter(layoutInflater, context, FavoriteListener(), fromQueue = true).also {
        binding.included.queue.layoutManager = LinearLayoutManager(context)
        binding.included.queue.adapter = it
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onResume() {
    super.onResume()

    binding.included.queue.visibility = View.GONE
    binding.included.placeholder.visibility = View.VISIBLE

    binding.included.queueShuffle.setOnClickListener {
      CommandBus.send(Command.ShuffleQueue)
    }

    binding.included.queueSave.setOnClickListener {
      adapter?.data?.let {
        CommandBus.send(Command.AddToPlaylist(it))
      }
    }

    binding.included.queueClear.setOnClickListener {
      CommandBus.send(Command.ClearQueue)
    }

    refresh()
  }

  private fun refresh() {
    lifecycleScope.launch(Main) {
      RequestBus.send(Request.GetQueue).wait<Response.Queue>()?.let { response ->
        binding.included.let { included ->
          adapter?.let {
            it.data = response.queue.toMutableList()
            it.notifyDataSetChanged()

            if (it.data.isEmpty()) {
              included.queue.visibility = View.GONE
              binding.included.placeholder.visibility = View.VISIBLE
            } else {
              included.queue.visibility = View.VISIBLE
              binding.included.placeholder.visibility = View.GONE
            }
          }
        }
      }
    }
  }

  private fun watchEventBus() {
    lifecycleScope.launch(Main) {
      EventBus.get().collect { message ->
        when (message) {
          is Event.QueueChanged -> refresh()
        }
      }
    }

    lifecycleScope.launch(Main) {
      CommandBus.get().collect { command ->
        when (command) {
          is Command.RefreshTrack -> refresh()
        }
      }
    }
  }

  inner class FavoriteListener : TracksAdapter.OnFavoriteListener {
    override fun onToggleFavorite(id: Int, state: Boolean) {
      when (state) {
        true -> favoritesRepository.addFavorite(id)
        false -> favoritesRepository.deleteFavorite(id)
      }
    }
  }
}
