package audio.funkwhale.ffa.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowTrackBinding
import audio.funkwhale.ffa.fragments.FFAAdapter
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.maybeLoad
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.toast
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import java.util.Collections

class FavoritesAdapter(
  private val layoutInflater: LayoutInflater,
  private val context: Context?,
  private val favoriteListener: FavoriteListener,
  val fromQueue: Boolean = false,
) : FFAAdapter<Track, FavoritesAdapter.ViewHolder>() {

  init {
    this.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
  }

  private lateinit var binding: RowTrackBinding

  var currentTrack: Track? = null
  var filter = ""

  override fun getItemCount() = data.size

  override fun getItemId(position: Int): Long {
    return data[position].id.toLong()
  }

  override fun applyFilter() {
    data.clear()
    getUnfilteredData().map {
      if (it.matchesFilter(filter)) {
        data.add(it)
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

    binding = RowTrackBinding.inflate(layoutInflater, parent, false)

    return ViewHolder(binding, context).also {
      binding.root.setOnClickListener(it)
    }
  }

  @SuppressLint("NewApi")
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val favorite = data[position]

    Picasso.get()
      .maybeLoad(maybeNormalizeUrl(favorite.album?.cover()))
      .fit()
      .placeholder(R.drawable.cover)
      .transform(RoundedCornersTransformation(16, 0))
      .into(holder.cover)

    holder.title.text = favorite.title
    holder.artist.text = favorite.artist.name

    context?.let {
      holder.itemView.background = context.getDrawable(R.drawable.ripple)
    }

    if (favorite.id == currentTrack?.id) {
      context?.let {
        holder.itemView.background = context.getDrawable(R.drawable.current)
      }
    }

    context?.let {
      when (favorite.favorite) {
        true -> holder.favorite.setColorFilter(context.getColor(R.color.colorFavorite))
        false -> holder.favorite.setColorFilter(context.getColor(R.color.colorSelected))
      }

      when (favorite.cached || favorite.downloaded) {
        true -> holder.title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.downloaded, 0, 0, 0)
        false -> holder.title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
      }

      if (favorite.cached && !favorite.downloaded) {
        holder.title.compoundDrawables.forEach {
          it?.colorFilter =
            PorterDuffColorFilter(context.getColor(R.color.cached), PorterDuff.Mode.SRC_IN)
        }
      }

      if (favorite.downloaded) {
        holder.title.compoundDrawables.forEach {
          it?.colorFilter =
            PorterDuffColorFilter(context.getColor(R.color.downloaded), PorterDuff.Mode.SRC_IN)
        }
      }

      holder.favorite.setOnClickListener {
        favoriteListener.onToggleFavorite(favorite.id, !favorite.favorite)

        data.remove(favorite)
        notifyItemRemoved(holder.bindingAdapterPosition)
      }
    }

    holder.actions.setOnClickListener {
      context?.let { context ->
        PopupMenu(context, holder.actions, Gravity.START, R.attr.actionOverflowMenuStyle, 0).apply {
          inflate(if (fromQueue) R.menu.row_queue else R.menu.row_track)

          setOnMenuItemClickListener {
            when (it.itemId) {
              R.id.track_add_to_queue -> CommandBus.send(Command.AddToQueue(listOf(favorite)))
              R.id.track_play_next -> CommandBus.send(Command.PlayNext(favorite))
              R.id.track_pin -> CommandBus.send(Command.PinTrack(favorite))
              R.id.queue_remove -> CommandBus.send(Command.RemoveFromQueue(favorite))
            }

            true
          }

          show()
        }
      }
    }
  }

  fun onItemMove(oldPosition: Int, newPosition: Int) {
    if (oldPosition < newPosition) {
      for (i in oldPosition.until(newPosition)) {
        Collections.swap(data, i, i + 1)
      }
    } else {
      for (i in newPosition.downTo(oldPosition)) {
        Collections.swap(data, i, i - 1)
      }
    }

    notifyItemMoved(oldPosition, newPosition)
    CommandBus.send(Command.MoveFromQueue(oldPosition, newPosition))
  }

  inner class ViewHolder(binding: RowTrackBinding, val context: Context?) :
    RecyclerView.ViewHolder(binding.root),
    View.OnClickListener {
    val cover = binding.cover
    val title = binding.title
    val artist = binding.artist

    val favorite = binding.favorite
    val actions = binding.actions

    override fun onClick(view: View?) {
      when (fromQueue) {
        true -> CommandBus.send(Command.PlayTrack(layoutPosition))
        false -> {
          data.subList(layoutPosition, data.size).plus(data.subList(0, layoutPosition)).apply {
            CommandBus.send(Command.ReplaceQueue(this))

            context.toast("All tracks were added to your queue")
          }
        }
      }
    }
  }
}
