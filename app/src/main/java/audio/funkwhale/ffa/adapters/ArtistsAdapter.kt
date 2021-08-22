package audio.funkwhale.ffa.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowArtistBinding
import audio.funkwhale.ffa.fragments.FFAAdapter
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.utils.maybeLoad
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation

class ArtistsAdapter(
  private val layoutInflater: LayoutInflater,
  private val context: Context?,
  private val listener: OnArtistClickListener
) : FFAAdapter<Artist, ArtistsAdapter.ViewHolder>() {

  private lateinit var binding: RowArtistBinding

  private var active: List<Artist> = mutableListOf()

  interface OnArtistClickListener {
    fun onClick(holder: View?, artist: Artist)
  }

  init {
    registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
      override fun onChanged() {
        active = data.filter { it.albums?.isNotEmpty() ?: false }

        super.onChanged()
      }

      override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        active = data.filter { it.albums?.isNotEmpty() ?: false }

        super.onItemRangeInserted(positionStart, itemCount)
      }
    })
  }

  override fun getItemCount() = active.size

  override fun getItemId(position: Int) = active[position].id.toLong()

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

    binding = RowArtistBinding.inflate(layoutInflater, parent, false)

    return ViewHolder(binding, listener).also {
      binding.root.setOnClickListener(it)
    }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val artist = active[position]

    artist.albums?.let { albums ->
      if (albums.isNotEmpty()) {
        Picasso.get()
          .maybeLoad(maybeNormalizeUrl(albums[0].cover?.urls?.original))
          .fit()
          .transform(RoundedCornersTransformation(8, 0))
          .into(holder.art)
      }
    }

    holder.name.text = artist.name

    artist.albums?.let {
      context?.let {
        holder.albums.text = context.resources.getQuantityString(
          R.plurals.album_count,
          artist.albums.size,
          artist.albums.size
        )
      }
    }
  }

  inner class ViewHolder(binding: RowArtistBinding, private val listener: OnArtistClickListener) :
    RecyclerView.ViewHolder(binding.root),
    View.OnClickListener {

    val art = binding.art
    val name = binding.name
    val albums = binding.albums

    override fun onClick(view: View?) {
      listener.onClick(view, active[layoutPosition])
    }
  }
}
