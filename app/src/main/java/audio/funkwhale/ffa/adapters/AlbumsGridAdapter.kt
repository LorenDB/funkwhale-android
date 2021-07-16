package audio.funkwhale.ffa.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowAlbumGridBinding
import audio.funkwhale.ffa.fragments.FFAAdapter
import audio.funkwhale.ffa.utils.Album
import audio.funkwhale.ffa.utils.maybeLoad
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation

class AlbumsGridAdapter(
  private val layoutInflater: LayoutInflater,
  private val listener: OnAlbumClickListener
) : FFAAdapter<Album, AlbumsGridAdapter.ViewHolder>() {

  private lateinit var binding: RowAlbumGridBinding

  interface OnAlbumClickListener {
    fun onClick(view: View?, album: Album)
  }

  override fun getItemId(position: Int): Long = data[position].id.toLong()

  override fun getItemCount() = data.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

    binding = RowAlbumGridBinding.inflate(layoutInflater, parent, false)

    return ViewHolder(binding, listener).also {
      binding.root.setOnClickListener(it)
    }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val album = data[position]

    Picasso.get()
      .maybeLoad(maybeNormalizeUrl(album.cover()))
      .fit()
      .placeholder(R.drawable.cover)
      .transform(RoundedCornersTransformation(16, 0))
      .into(holder.cover)

    holder.title.text = album.title
  }

  inner class ViewHolder(binding: RowAlbumGridBinding, private val listener: OnAlbumClickListener) :
    RecyclerView.ViewHolder(binding.root), View.OnClickListener {
    val cover = binding.cover
    val title = binding.title

    override fun onClick(view: View?) {
      listener.onClick(view, data[layoutPosition])
    }
  }
}
