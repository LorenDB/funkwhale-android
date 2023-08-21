package audio.funkwhale.ffa.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowSearchHeaderBinding
import audio.funkwhale.ffa.databinding.RowTrackBinding
import audio.funkwhale.ffa.model.Album
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.onApi
import audio.funkwhale.ffa.utils.toast
import audio.funkwhale.ffa.viewmodel.SearchViewModel
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation

class SearchAdapter(
  viewModel: SearchViewModel,
  private val fragment: Fragment,
  private val listener: OnSearchResultClickListener,
  private val favoriteListener: FavoriteListener
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

  interface OnSearchResultClickListener {
    fun onArtistClick(holder: View?, artist: Artist)
    fun onAlbumClick(holder: View?, album: Album)
  }

  enum class ResultType {
    Header,
    Artist,
    Album,
    Track
  }

  private lateinit var searchHeaderBinding: RowSearchHeaderBinding
  private lateinit var rowTrackBinding: RowTrackBinding

  val sectionCount = 3

  var artists = listOf<Artist>()
  var albums = listOf<Album>()
  var tracks = listOf<Track>()

  var currentTrack: Track? = null

  init {
    viewModel.artistResults.observe(fragment.viewLifecycleOwner) {
      artists = it
      this.notifyDataSetChanged()
    }
    viewModel.albumResults.observe(fragment.viewLifecycleOwner) {
      albums = it
      this.notifyDataSetChanged()
    }
    viewModel.trackResults.observe(fragment.viewLifecycleOwner) {
      tracks = it
      this.notifyDataSetChanged()
    }
  }

  override fun getItemCount() = sectionCount + artists.size + albums.size + tracks.size

  override fun getItemId(position: Int): Long {
    return when (getItemViewType(position)) {
      ResultType.Header.ordinal -> {
        if (position == 0) return -1
        if (position == (artists.size + 1)) return -2
        return -3
      }

      ResultType.Artist.ordinal -> artists[position].id.toLong()
      ResultType.Album.ordinal -> albums[position - artists.size - 2].id.toLong()
      ResultType.Track.ordinal ->
        tracks[position - artists.size - albums.size - sectionCount].id.toLong()
      else -> 0
    }
  }

  override fun getItemViewType(position: Int): Int = when {
    position == 0 ||
      position == (artists.size + 1) ||
      position == (artists.size + albums.size + 2) -> ResultType.Header.ordinal
    position <= artists.size -> ResultType.Artist.ordinal
    position <= artists.size + albums.size + 2 -> ResultType.Album.ordinal
    else -> ResultType.Track.ordinal
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    return when (viewType) {
      ResultType.Header.ordinal -> {
        searchHeaderBinding = RowSearchHeaderBinding.inflate(fragment.layoutInflater, parent, false)
        SearchHeaderViewHolder(searchHeaderBinding, fragment.requireContext())
      }
      else -> {
        rowTrackBinding = RowTrackBinding.inflate(fragment.layoutInflater, parent, false)
        RowTrackViewHolder(rowTrackBinding, fragment.requireContext()).also {
          rowTrackBinding.root.setOnClickListener(it)
        }
      }
    }
  }

  @SuppressLint("NewApi")
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val resultType = getItemViewType(position)
    val searchHeaderViewHolder = holder as? SearchHeaderViewHolder
    val rowTrackViewHolder = holder as? RowTrackViewHolder

    if (resultType == ResultType.Header.ordinal) {
      if (position == 0) {
        searchHeaderViewHolder?.title?.text = fragment.requireContext().getString(R.string.artists)
        holder.itemView.visibility = View.VISIBLE
        holder.itemView.layoutParams = RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (artists.isEmpty()) {
          holder.itemView.visibility = View.GONE
          holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
        }
      }

      if (position == (artists.size + 1)) {
        searchHeaderViewHolder?.title?.text = fragment.requireContext().getString(R.string.albums)
        holder.itemView.visibility = View.VISIBLE
        holder.itemView.layoutParams = RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (albums.isEmpty()) {
          holder.itemView.visibility = View.GONE
          holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
        }
      }

      if (position == (artists.size + albums.size + 2)) {
        searchHeaderViewHolder?.title?.text = fragment.requireContext().getString(R.string.tracks)
        holder.itemView.visibility = View.VISIBLE
        holder.itemView.layoutParams = RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (tracks.isEmpty()) {
          holder.itemView.visibility = View.GONE
          holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
        }
      }

      return
    }

    val item = when (resultType) {
      ResultType.Artist.ordinal -> {
        rowTrackViewHolder?.actions?.visibility = View.GONE
        rowTrackViewHolder?.favorite?.visibility = View.GONE

        artists[position - 1]
      }

      ResultType.Album.ordinal -> {
        rowTrackViewHolder?.actions?.visibility = View.GONE
        rowTrackViewHolder?.favorite?.visibility = View.GONE

        albums[position - artists.size - 2]
      }

      ResultType.Track.ordinal -> {
        tracks[position - artists.size - albums.size - sectionCount]
      }

      else -> tracks[position]
    }

    CoverArt.requestCreator(maybeNormalizeUrl(item.cover()))
      .fit()
      .transform(RoundedCornersTransformation(16, 0))
      .into(rowTrackViewHolder?.cover)

    rowTrackViewHolder?.title?.text = item.title()
    rowTrackViewHolder?.artist?.text = item.subtitle()

    Build.VERSION_CODES.P.onApi(
      {
        searchHeaderViewHolder?.title?.setTypeface(
          searchHeaderViewHolder.title.typeface,
          Typeface.DEFAULT.weight
        )
        rowTrackViewHolder?.artist?.setTypeface(
          rowTrackViewHolder.artist.typeface,
          Typeface.DEFAULT.weight
        )
      },
      {
        searchHeaderViewHolder?.title?.typeface =
          Typeface.create(searchHeaderViewHolder?.title?.typeface, Typeface.NORMAL)
        rowTrackViewHolder?.artist?.typeface =
          Typeface.create(rowTrackViewHolder?.artist?.typeface, Typeface.NORMAL)
      }
    )

    searchHeaderViewHolder?.title?.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)

    when (resultType) {
      ResultType.Artist.ordinal -> {
        rowTrackViewHolder?.title?.setCompoundDrawablesWithIntrinsicBounds(
          0, 0, 0, 0
        )
      }
      ResultType.Album.ordinal -> {
        rowTrackViewHolder?.title?.setCompoundDrawablesWithIntrinsicBounds(
          0, 0, 0, 0
        )
      }
      ResultType.Track.ordinal -> {
        (item as? Track)?.let { track ->
          if (track == currentTrack || track.current) {
            searchHeaderViewHolder?.title?.setTypeface(
              searchHeaderViewHolder.title.typeface,
              Typeface.BOLD
            )
            rowTrackViewHolder?.artist?.setTypeface(
              rowTrackViewHolder.artist.typeface,
              Typeface.BOLD
            )
          }

          when (track.favorite) {
            true -> rowTrackViewHolder?.favorite?.setColorFilter(
              fragment.requireContext().getColor(R.color.colorFavorite)
            )
            false -> rowTrackViewHolder?.favorite?.setColorFilter(
              fragment.requireContext().getColor(R.color.colorSelected)
            )
          }

          rowTrackViewHolder?.favorite?.setOnClickListener {
            favoriteListener.let {
              favoriteListener.onToggleFavorite(track.id, !track.favorite)

              tracks[position - artists.size - albums.size - sectionCount].favorite =
                !track.favorite

              notifyItemChanged(position)
            }
          }

          when (track.cached || track.downloaded) {
            true -> rowTrackViewHolder?.title?.setCompoundDrawablesWithIntrinsicBounds(
              R.drawable.downloaded, 0, 0, 0
            )
            false -> rowTrackViewHolder?.title?.setCompoundDrawablesWithIntrinsicBounds(
              0, 0, 0, 0
            )
          }

          if (track.cached && !track.downloaded) {
            rowTrackViewHolder?.title?.compoundDrawables?.forEach {
              it?.colorFilter =
                PorterDuffColorFilter(
                  fragment.requireContext().getColor(R.color.cached),
                  PorterDuff.Mode.SRC_IN
                )
            }
          }

          if (track.downloaded) {
            rowTrackViewHolder?.title?.compoundDrawables?.forEach {
              it?.colorFilter =
                PorterDuffColorFilter(
                  fragment.requireContext().getColor(R.color.downloaded),
                  PorterDuff.Mode.SRC_IN
                )
            }
          }

          rowTrackViewHolder?.actions?.setOnClickListener {
            PopupMenu(
              fragment.requireContext(),
              rowTrackViewHolder.actions,
              Gravity.START,
              R.attr.actionOverflowMenuStyle,
              0
            ).apply {
              inflate(R.menu.row_track)

              setOnMenuItemClickListener {
                when (it.itemId) {
                  R.id.track_add_to_queue -> CommandBus.send(Command.AddToQueue(listOf(track)))
                  R.id.track_play_next -> CommandBus.send(Command.PlayNext(track))
                  R.id.track_pin -> CommandBus.send(Command.PinTrack(track))
                  R.id.track_add_to_playlist -> CommandBus.send(
                    Command.AddToPlaylist(listOf(track))
                  )
                  R.id.queue_remove -> CommandBus.send(Command.RemoveFromQueue(track))
                }

                true
              }

              show()
            }
          }
        }
      }
    }
  }

  fun getPositionOf(type: ResultType, position: Int): Int {
    return when (type) {
      ResultType.Artist -> position + 1
      ResultType.Album -> position + artists.size + 2
      ResultType.Track -> artists.size + albums.size + sectionCount + position
      else -> 0
    }
  }

  inner class SearchHeaderViewHolder(val binding: RowSearchHeaderBinding, context: Context) :
    ViewHolder(binding.root, context) {
    val title = binding.title
  }

  inner class RowTrackViewHolder(val binding: RowTrackBinding, context: Context) :
    ViewHolder(binding.root, context), View.OnClickListener {
    val title = binding.title
    val cover = binding.cover
    val artist = binding.artist

    val favorite = binding.favorite
    val actions = binding.actions

    override fun onClick(view: View?) {
      when (getItemViewType(layoutPosition)) {
        ResultType.Artist.ordinal -> {
          val position = layoutPosition - 1

          listener.onArtistClick(view, artists[position])
        }

        ResultType.Album.ordinal -> {
          val position = layoutPosition - artists.size - 2

          listener.onAlbumClick(view, albums[position])
        }

        ResultType.Track.ordinal -> {
          val position = layoutPosition - artists.size - albums.size - sectionCount

          tracks.subList(position, tracks.size).plus(tracks.subList(0, position)).apply {
            CommandBus.send(Command.ReplaceQueue(this))

            context.toast("All tracks were added to your queue")
          }
        }

        else -> {
          // empty
        }
      }
    }
  }

  abstract inner class ViewHolder(view: View, val context: Context?) : RecyclerView.ViewHolder(view)
}
