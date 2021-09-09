package audio.funkwhale.ffa.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
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
import audio.funkwhale.ffa.model.PlaylistTrack
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.maybeLoad
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.toast
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import java.util.Collections

class PlaylistTracksAdapter(
  private val layoutInflater: LayoutInflater,
  private val context: Context?,
  private val favoriteListener: OnFavoriteListener? = null,
  private val playlistListener: OnPlaylistListener? = null
) : FFAAdapter<PlaylistTrack, PlaylistTracksAdapter.ViewHolder>() {

  interface OnFavoriteListener {
    fun onToggleFavorite(id: Int, state: Boolean)
  }

  private lateinit var binding: RowTrackBinding

  interface OnPlaylistListener {
    fun onMoveTrack(from: Int, to: Int)
    fun onRemoveTrackFromPlaylist(track: Track, index: Int)
  }

  private lateinit var touchHelper: ItemTouchHelper

  var currentTrack: Track? = null

  override fun getItemCount() = data.size

  override fun getItemId(position: Int): Long {
    return data[position].track.id.toLong()
  }

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)

    touchHelper = ItemTouchHelper(TouchHelperCallback()).also {
      it.attachToRecyclerView(recyclerView)
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

    Picasso.get()
      .maybeLoad(maybeNormalizeUrl(track.track.album?.cover()))
      .fit()
      .placeholder(R.drawable.cover)
      .transform(RoundedCornersTransformation(16, 0))
      .into(holder.cover)

    holder.title.text = track.track.title
    holder.artist.text = track.track.artist.name

    context?.let {
      holder.itemView.background = ContextCompat.getDrawable(context, R.drawable.ripple)
    }

    if (track.track == currentTrack || track.track.current) {
      context?.let {
        holder.itemView.background = ContextCompat.getDrawable(context, R.drawable.current)
      }
    }

    context?.let {
      when (track.track.favorite) {
        true -> holder.favorite.setColorFilter(context.getColor(R.color.colorFavorite))
        false -> holder.favorite.setColorFilter(context.getColor(R.color.colorSelected))
      }

      holder.favorite.setOnClickListener {
        favoriteListener?.let {
          favoriteListener.onToggleFavorite(track.track.id, !track.track.favorite)

          track.track.favorite = !track.track.favorite
          notifyItemChanged(position)
        }
      }
    }

    holder.actions.setOnClickListener {
      context?.let { context ->
        PopupMenu(context, holder.actions, Gravity.START, R.attr.actionOverflowMenuStyle, 0).apply {
          inflate(R.menu.row_track)

          menu.findItem(R.id.track_remove_from_playlist).isVisible = true

          setOnMenuItemClickListener {
            when (it.itemId) {
              R.id.track_add_to_queue -> CommandBus.send(Command.AddToQueue(listOf(track.track)))
              R.id.track_play_next -> CommandBus.send(Command.PlayNext(track.track))
              R.id.queue_remove -> CommandBus.send(Command.RemoveFromQueue(track.track))
              R.id.track_remove_from_playlist -> playlistListener?.onRemoveTrackFromPlaylist(
                track.track,
                position
              )
            }

            true
          }

          show()
        }
      }
    }

    holder.handle.visibility = View.VISIBLE

    holder.handle.setOnTouchListener { _, event ->
      if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        touchHelper.startDrag(holder)
      }

      true
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
      data.subList(layoutPosition, data.size).plus(data.subList(0, layoutPosition)).apply {
        CommandBus.send(Command.ReplaceQueue(this.map { it.track }))

        context.toast("All tracks were added to your queue")
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
      if (from == -1) from = viewHolder.bindingAdapterPosition
      to = target.bindingAdapterPosition

      onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

      return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
      if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
        context?.let {
          viewHolder?.itemView?.background = ColorDrawable(context.getColor(R.color.colorSelected))
        }
      }

      super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
      viewHolder.itemView.background = ColorDrawable(Color.TRANSPARENT)

      if (from != -1 && to != -1 && from != to) {
        playlistListener?.onMoveTrack(from, to)

        from = -1
        to = -1
      }

      super.clearView(recyclerView, viewHolder)
    }
  }
}
