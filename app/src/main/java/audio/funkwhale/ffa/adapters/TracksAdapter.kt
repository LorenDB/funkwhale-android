package audio.funkwhale.ffa.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowTrackBinding
import audio.funkwhale.ffa.fragments.FFAAdapter
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.toast
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import java.util.Collections

class TracksAdapter(
  private val layoutInflater: LayoutInflater,
  private val context: Context?,
  private val favoriteListener: FavoriteListener,
  val fromQueue: Boolean = false
) : FFAAdapter<Track, TracksAdapter.ViewHolder>() {

  init {
    this.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
  }

  private lateinit var binding: RowTrackBinding
  private lateinit var touchHelper: ItemTouchHelper

  var currentTrack: Track? = null

  override fun getItemId(position: Int): Long = data[position].id.toLong()

  override fun getItemCount() = data.size

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)

    if (fromQueue) {
      touchHelper = ItemTouchHelper(TouchHelperCallback()).also {
        it.attachToRecyclerView(recyclerView)
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
    val track = data[position]

    CoverArt.requestCreator(maybeNormalizeUrl(track.cover()))
      .fit()
      .transform(RoundedCornersTransformation(8, 0))
      .into(holder.cover)

    holder.title.text = track.title
    holder.artist.text = track.artist.name

    context?.let {
      holder.itemView.background = ContextCompat.getDrawable(context, R.drawable.ripple)
    }

    if (track == currentTrack || track.current) {
      context?.let {
        holder.itemView.background = ContextCompat.getDrawable(context, R.drawable.current)
      }
    }

    context?.let {
      when (track.favorite) {
        true -> holder.favorite.setColorFilter(context.getColor(R.color.colorFavorite))
        false -> holder.favorite.setColorFilter(context.getColor(R.color.colorSelected))
      }

      holder.favorite.setOnClickListener {
        favoriteListener.let {
          favoriteListener.onToggleFavorite(track.id, !track.favorite)

          data[position].favorite = !track.favorite

          notifyItemChanged(position)
        }
      }

      when (track.cached || track.downloaded) {
        true -> holder.title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.downloaded, 0, 0, 0)
        false -> holder.title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
      }

      if (track.cached && !track.downloaded) {
        holder.title.compoundDrawables.forEach {
          it?.colorFilter =
            PorterDuffColorFilter(context.getColor(R.color.cached), PorterDuff.Mode.SRC_IN)
        }
      }

      if (track.downloaded) {
        holder.title.compoundDrawables.forEach {
          it?.colorFilter =
            PorterDuffColorFilter(context.getColor(R.color.downloaded), PorterDuff.Mode.SRC_IN)
        }
      }
    }

    holder.actions.setOnClickListener {
      context?.let { context ->
        PopupMenu(context, holder.actions, Gravity.START, R.attr.actionOverflowMenuStyle, 0).apply {
          inflate(if (fromQueue) R.menu.row_queue else R.menu.row_track)

          setOnMenuItemClickListener {
            when (it.itemId) {
              R.id.track_add_to_queue -> CommandBus.send(Command.AddToQueue(listOf(track)))
              R.id.track_play_next -> CommandBus.send(Command.PlayNext(track))
              R.id.track_pin -> CommandBus.send(Command.PinTrack(track))
              R.id.track_add_to_playlist -> CommandBus.send(Command.AddToPlaylist(listOf(track)))
              R.id.queue_remove -> CommandBus.send(Command.RemoveFromQueue(track))
            }

            true
          }

          show()
        }
      }
    }

    if (fromQueue) {
      holder.handle.visibility = View.VISIBLE

      holder.handle.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
          touchHelper.startDrag(holder)
        }

        true
      }
    }
  }

  fun onItemMove(oldPosition: Int, newPosition: Int) {
    if (oldPosition < newPosition) {
      for (i in oldPosition.until(newPosition)) {
        Collections.swap(data, i, i + 1)
      }
    } else {
      for (i in oldPosition.downTo(newPosition + 1)) {
        Collections.swap(data, i, i - 1)
      }
    }

    notifyItemMoved(oldPosition, newPosition)
  }

  inner class ViewHolder(binding: RowTrackBinding, val context: Context?) :
    RecyclerView.ViewHolder(binding.root),
    View.OnClickListener {

    val handle = binding.handle
    val cover = binding.cover
    val title = binding.title
    val artist = binding.artist

    val favorite = binding.favorite
    val actions = binding.actions

    override fun onClick(view: View?) {
      when (fromQueue) {
        true -> CommandBus.send(Command.PlayTrack(layoutPosition))
        false -> {
          CommandBus.send(Command.ReplaceQueue(data, startIndex = layoutPosition))
          context.toast("All tracks were added to your queue")
        }
      }
    }
  }

  inner class TouchHelperCallback : ItemTouchHelper.Callback() {
    var from = -1
    var to = -1

    override fun isLongPressDragEnabled() = false

    override fun isItemViewSwipeEnabled() = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
      makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

    override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      target: RecyclerView.ViewHolder
    ): Boolean {
      to = target.absoluteAdapterPosition

      onItemMove(viewHolder.absoluteAdapterPosition, to)

      return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
      if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
        context?.let {
          viewHolder?.let {
            from = viewHolder.bindingAdapterPosition
            viewHolder.itemView.background = ColorDrawable(context.getColor(R.color.colorSelected))
          }
        }
      }

      super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
      if (from != -1 && to != -1) {
        CommandBus.send(Command.MoveFromQueue(from, to))

        from = -1
        to = -1
      }

      viewHolder.itemView.background = ColorDrawable(Color.TRANSPARENT)

      super.clearView(recyclerView, viewHolder)
    }
  }
}
