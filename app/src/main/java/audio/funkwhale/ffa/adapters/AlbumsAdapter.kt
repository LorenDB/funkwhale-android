package audio.funkwhale.ffa.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.databinding.RowAlbumBinding
import audio.funkwhale.ffa.fragments.FFAAdapter
import audio.funkwhale.ffa.model.Album
import audio.funkwhale.ffa.utils.CoverArt
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation

class AlbumsAdapter(
  val layoutInflater: LayoutInflater,
  val context: Context?,
  private val listener: OnAlbumClickListener
) : FFAAdapter<Album, AlbumsAdapter.ViewHolder>() {

  init {
    this.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
  }

  interface OnAlbumClickListener {
    fun onClick(view: View?, album: Album)
  }

  private lateinit var binding: RowAlbumBinding

  override fun getItemId(position: Int): Long = data[position].id.toLong()

  override fun getItemCount() = data.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

    binding = RowAlbumBinding.inflate(layoutInflater, parent, false)

    return ViewHolder(binding, listener).also {
      binding.root.setOnClickListener(it)
    }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val album = data[position]

    CoverArt.withContext(layoutInflater.context, album.cover())
      .fit()
      .transform(RoundedCornersTransformation(8, 0))
      .into(holder.art)

    holder.title.text = album.title
    holder.artist.text = album.artist.name
    holder.releaseDate.visibility = View.GONE

    album.release_date?.split('-')?.getOrNull(0)?.let { year ->
      if (year.isNotEmpty()) {
        holder.releaseDate.visibility = View.VISIBLE
        holder.releaseDate.text = year
      }
    }
  }

  inner class ViewHolder(binding: RowAlbumBinding, private val listener: OnAlbumClickListener) :
    RecyclerView.ViewHolder(binding.root), View.OnClickListener {
    val art = binding.art
    val title = binding.title
    val artist = binding.artist
    val releaseDate = binding.releaseDate

    override fun onClick(view: View?) {
      listener.onClick(view, data[layoutPosition])
    }
  }
}
