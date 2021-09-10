package audio.funkwhale.ffa.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowPlaylistBinding
import audio.funkwhale.ffa.fragments.FFAAdapter
import audio.funkwhale.ffa.model.Playlist
import audio.funkwhale.ffa.utils.toDurationString
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation

class PlaylistsAdapter(
  private val layoutInflater: LayoutInflater,
  private val context: Context?,
  private val listener: OnPlaylistClickListener
) : FFAAdapter<Playlist, PlaylistsAdapter.ViewHolder>() {

  init {
    this.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
  }

  interface OnPlaylistClickListener {
    fun onClick(holder: View?, playlist: Playlist)
  }

  private lateinit var binding: RowPlaylistBinding

  override fun getItemCount() = data.size

  override fun getItemId(position: Int) = data[position].id.toLong()

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    binding = RowPlaylistBinding.inflate(layoutInflater, parent, false)

    return ViewHolder(binding, listener).also {
      binding.root.setOnClickListener(it)
    }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val playlist = data[position]

    holder.name.text = playlist.name
    holder.summary.text = context?.resources?.getQuantityString(
      R.plurals.playlist_description,
      playlist.tracks_count,
      playlist.tracks_count,
      toDurationString(playlist.duration.toLong())
    ) ?: ""

    context?.let {
      ContextCompat.getDrawable(context, R.drawable.cover).let {
        holder.coverTopLeft.setImageDrawable(it)
        holder.covertTopRight.setImageDrawable(it)
        holder.coverBottomLeft.setImageDrawable(it)
        holder.coverBottomRight.setImageDrawable(it)
      }
    }

    playlist.album_covers.shuffled().take(4).forEachIndexed { index, url ->
      val imageView = when (index) {
        0 -> holder.coverTopLeft
        1 -> holder.covertTopRight
        2 -> holder.coverBottomLeft
        3 -> holder.coverBottomRight
        else -> holder.coverTopLeft
      }

      val corner = when (index) {
        0 -> RoundedCornersTransformation.CornerType.TOP_LEFT
        1 -> RoundedCornersTransformation.CornerType.TOP_RIGHT
        2 -> RoundedCornersTransformation.CornerType.BOTTOM_LEFT
        3 -> RoundedCornersTransformation.CornerType.BOTTOM_RIGHT
        else -> RoundedCornersTransformation.CornerType.TOP_LEFT
      }

      Picasso.get()
        .load(url)
        .transform(RoundedCornersTransformation(32, 0, corner))
        .into(imageView)
    }
  }

  inner class ViewHolder(
    binding: RowPlaylistBinding,
    private val listener: OnPlaylistClickListener
  ) :
    RecyclerView.ViewHolder(binding.root), View.OnClickListener {
    val name = binding.name
    val summary = binding.summary

    val coverTopLeft = binding.coverTopLeft
    val covertTopRight = binding.coverTopRight
    val coverBottomLeft = binding.coverBottomLeft
    val coverBottomRight = binding.coverBottomRight

    override fun onClick(view: View?) {
      listener.onClick(view, data[layoutPosition])
    }
  }
}
