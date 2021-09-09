package audio.funkwhale.ffa.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowRadioBinding
import audio.funkwhale.ffa.databinding.RowRadioHeaderBinding
import audio.funkwhale.ffa.fragments.FFAAdapter
import audio.funkwhale.ffa.model.Radio
import audio.funkwhale.ffa.utils.AppContext
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.views.LoadingImageView
import com.preference.PowerPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RadiosAdapter(
  private val layoutInflater: LayoutInflater,
  private val context: Context?,
  private val scope: CoroutineScope,
  private val listener: OnRadioClickListener
) : FFAAdapter<Radio, RadiosAdapter.ViewHolder>() {

  interface OnRadioClickListener {
    fun onClick(holder: RowRadioViewHolder, radio: Radio)
  }

  private lateinit var rowRadioBinding: RowRadioBinding
  private lateinit var rowRadioHeaderBinding: RowRadioHeaderBinding

  enum class RowType {
    Header,
    InstanceRadio,
    UserRadio
  }

  private val instanceRadios: List<Radio> by lazy {
    context?.let {
      return@lazy when (
        val username =
          PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).getString("actor_username")
      ) {
        "" -> listOf(
          Radio(
            0,
            "random",
            context.getString(R.string.radio_random_title),
            context.getString(R.string.radio_random_description)
          )
        )

        else -> listOf(
          Radio(
            0,
            "actor-content",
            context.getString(R.string.radio_your_content_title),
            context.getString(R.string.radio_your_content_description),
            username
          ),
          Radio(
            0,
            "random",
            context.getString(R.string.radio_random_title),
            context.getString(R.string.radio_random_description)
          ),
          Radio(
            0,
            "favorites",
            context.getString(R.string.favorites),
            context.getString(R.string.radio_favorites_description)
          ),
          Radio(
            0,
            "less-listened",
            context.getString(R.string.radio_less_listened_title),
            context.getString(R.string.radio_less_listened_description)
          )
        )
      }
    }

    listOf<Radio>()
  }

  private fun getRadioAt(position: Int): Radio {
    return when (getItemViewType(position)) {
      RowType.InstanceRadio.ordinal -> instanceRadios[position - 1]
      else -> data[position - instanceRadios.size - 2]
    }
  }

  override fun getItemId(position: Int) = when (getItemViewType(position)) {
    RowType.InstanceRadio.ordinal -> (-position - 1).toLong()
    RowType.Header.ordinal -> Long.MIN_VALUE
    else -> getRadioAt(position).id.toLong()
  }

  override fun getItemCount() = instanceRadios.size + data.size + 2

  override fun getItemViewType(position: Int): Int {
    return when {
      position == 0 || position == instanceRadios.size + 1 -> RowType.Header.ordinal
      position <= instanceRadios.size -> RowType.InstanceRadio.ordinal
      else -> RowType.UserRadio.ordinal
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RadiosAdapter.ViewHolder {
    return when (viewType) {
      RowType.InstanceRadio.ordinal, RowType.UserRadio.ordinal -> {
        rowRadioBinding = RowRadioBinding.inflate(layoutInflater, parent, false)

        RowRadioViewHolder(rowRadioBinding, listener).also {
          rowRadioBinding.root.setOnClickListener(it)
        }
      }

      else -> {
        rowRadioHeaderBinding = RowRadioHeaderBinding.inflate(layoutInflater, parent, false)
        RowRadioHeaderViewHolder(rowRadioHeaderBinding)
      }
    }
  }

  override fun onBindViewHolder(holder: RadiosAdapter.ViewHolder, position: Int) {
    when (getItemViewType(position)) {
      RowType.Header.ordinal -> {
        holder as RowRadioHeaderViewHolder
        context?.let {
          when (position) {
            0 -> holder.label.text = context.getString(R.string.radio_instance_radios)
            instanceRadios.size + 1 ->
              holder.label.text =
                context.getString(R.string.radio_user_radios)
          }
        }
      }

      RowType.InstanceRadio.ordinal, RowType.UserRadio.ordinal -> {
        val radio = getRadioAt(position)
        holder as RowRadioViewHolder
        holder.art.visibility = View.VISIBLE
        holder.name.text = radio.name
        holder.description.text = radio.description

        context?.let { context ->
          val icon = when (radio.radio_type) {
            "actor_content" -> R.drawable.library
            "favorites" -> R.drawable.favorite
            "random" -> R.drawable.shuffle
            "less-listened" -> R.drawable.sad
            else -> null
          }

          icon?.let {
            holder.native = true

            holder.art.setImageDrawable(context.getDrawable(icon))
            holder.art.alpha = 0.7f
            holder.art.setColorFilter(context.getColor(R.color.controlForeground))
          }
        }
      }
    }
  }

  inner class RowRadioViewHolder(binding: RowRadioBinding, val listener: OnRadioClickListener) :
    ViewHolder(binding.root),
    View.OnClickListener {
    val art = binding.art
    val name = binding.name
    val description = binding.description

    fun spin() {
      context?.let {
        val originalDrawable = art.drawable
        val originalColorFilter = art.colorFilter
        val imageAnimator = LoadingImageView.start(context, art)

        art.setColorFilter(context.getColor(R.color.controlForeground))

        scope.launch(Main) {
          EventBus.get().collect { message ->
            when (message) {
              is Event.RadioStarted -> {
                art.colorFilter = originalColorFilter
                LoadingImageView.stop(context, originalDrawable, art, imageAnimator)
              }
            }
          }
        }
      }
    }

    override fun onClick(view: View?) {
      listener.onClick(this, getRadioAt(layoutPosition))
    }
  }

  inner class RowRadioHeaderViewHolder(
    binding: RowRadioHeaderBinding
  ) : ViewHolder(
    binding.root
  ) {
    val label = binding.label
  }

  abstract inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    var native = false
  }
}
