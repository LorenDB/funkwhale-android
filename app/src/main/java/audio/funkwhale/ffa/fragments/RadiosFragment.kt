package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.adapters.RadiosAdapter
import audio.funkwhale.ffa.databinding.FragmentRadiosBinding
import audio.funkwhale.ffa.repositories.RadiosRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.model.Radio
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RadiosFragment : FFAFragment<Radio, RadiosAdapter>() {

  override val recycler: RecyclerView get() = binding.radios
  override val alwaysRefresh = false

  private var _binding: FragmentRadiosBinding? = null
  private val binding get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    adapter = RadiosAdapter(layoutInflater, context, lifecycleScope, RadioClickListener())
    repository = RadiosRepository(context)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentRadiosBinding.inflate(inflater)
    swiper = binding.swiper
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  inner class RadioClickListener : RadiosAdapter.OnRadioClickListener {

    override fun onClick(holder: RadiosAdapter.RowRadioViewHolder, radio: Radio) {
      holder.spin()
      recycler.forEach {
        it.isEnabled = false
        it.isClickable = false
      }

      CommandBus.send(Command.PlayRadio(radio))

      lifecycleScope.launch(Main) {
        EventBus.get().collect { message ->
          when (message) {
            is Event.RadioStarted ->
              recycler.forEach {
                it.isEnabled = true
                it.isClickable = true
              }
          }
        }
      }
    }
  }
}
