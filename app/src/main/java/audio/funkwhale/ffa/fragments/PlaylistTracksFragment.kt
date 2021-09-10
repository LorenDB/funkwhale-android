package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.adapters.FavoriteListener
import audio.funkwhale.ffa.adapters.PlaylistTracksAdapter
import audio.funkwhale.ffa.databinding.FragmentTracksBinding
import audio.funkwhale.ffa.model.Playlist
import audio.funkwhale.ffa.model.PlaylistTrack
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.ManagementPlaylistsRepository
import audio.funkwhale.ffa.repositories.PlaylistTracksRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import audio.funkwhale.ffa.utils.maybeLoad
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.toast
import audio.funkwhale.ffa.utils.wait
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PlaylistTracksFragment : FFAFragment<PlaylistTrack, PlaylistTracksAdapter>() {

  override val recycler: RecyclerView get() = binding.tracks

  private var _binding: FragmentTracksBinding? = null
  private val binding get() = _binding!!

  lateinit var favoritesRepository: FavoritesRepository
  lateinit var playlistsRepository: ManagementPlaylistsRepository

  var albumId = 0
  var albumArtist = ""
  var albumTitle = ""
  var albumCover = ""

  companion object {
    fun new(playlist: Playlist): PlaylistTracksFragment {
      return PlaylistTracksFragment().apply {
        arguments = bundleOf(
          "albumId" to playlist.id,
          "albumArtist" to "N/A",
          "albumTitle" to playlist.name,
          "albumCover" to ""
        )
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    arguments?.apply {
      albumId = getInt("albumId")
      albumArtist = getString("albumArtist") ?: ""
      albumTitle = getString("albumTitle") ?: ""
      albumCover = getString("albumCover") ?: ""
    }

    adapter = PlaylistTracksAdapter(layoutInflater, context, FavoriteListener(favoritesRepository), PlaylistListener())
    repository = PlaylistTracksRepository(context, albumId)
    favoritesRepository = FavoritesRepository(context)
    playlistsRepository = ManagementPlaylistsRepository(context)

    watchEventBus()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentTracksBinding.inflate(layoutInflater)
    swiper = binding.swiper
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.cover.visibility = View.INVISIBLE
    binding.covers.visibility = View.VISIBLE

    binding.artist.text = "Playlist"
    binding.title.text = albumTitle
  }

  override fun onResume() {
    super.onResume()

    lifecycleScope.launch(Main) {
      RequestBus.send(Request.GetCurrentTrack).wait<Response.CurrentTrack>()?.let { response ->
        adapter.currentTrack = response.track
        adapter.notifyDataSetChanged()
      }
    }

    var coverHeight: Float? = null

    binding.scroller.setOnScrollChangeListener { _: View?, _: Int, scrollY: Int, _: Int, _: Int ->
      if (coverHeight == null) {
        coverHeight = binding.covers.measuredHeight.toFloat()
      }

      binding.covers.translationY = (scrollY / 2).toFloat()

      coverHeight?.let { height ->
        binding.covers.alpha = (height - scrollY.toFloat()) / height
      }
    }

    binding.play.setOnClickListener {
      CommandBus.send(Command.ReplaceQueue(adapter.data.map { it.track }.shuffled()))

      context.toast("All tracks were added to your queue")
    }

    context?.let { context ->
      binding.actions.setOnClickListener {
        PopupMenu(
          context,
          binding.actions,
          Gravity.START,
          R.attr.actionOverflowMenuStyle,
          0
        ).apply {
          inflate(R.menu.album)

          setOnMenuItemClickListener {
            when (it.itemId) {
              R.id.add_to_queue -> {
                CommandBus.send(Command.AddToQueue(adapter.data.map { it.track }))

                context.toast("All tracks were added to your queue")
              }

              R.id.download -> CommandBus.send(Command.PinTracks(adapter.data.map { it.track }))
            }

            true
          }

          show()
        }
      }
    }
  }

  override fun onDataFetched(data: List<PlaylistTrack>) {
    data.map { it.track.album }.toSet().map { it?.cover() }.take(4).forEachIndexed { index, url ->
      val imageView = when (index) {
        0 -> binding.coverTopLeft
        1 -> binding.coverTopRight
        2 -> binding.coverBottomLeft
        3 -> binding.coverBottomRight
        else -> binding.coverTopLeft
      }

      val corner = when (index) {
        0 -> RoundedCornersTransformation.CornerType.TOP_LEFT
        1 -> RoundedCornersTransformation.CornerType.TOP_RIGHT
        2 -> RoundedCornersTransformation.CornerType.BOTTOM_LEFT
        3 -> RoundedCornersTransformation.CornerType.BOTTOM_RIGHT
        else -> RoundedCornersTransformation.CornerType.TOP_LEFT
      }

      lifecycleScope.launch(Main) {
        Picasso.get()
          .maybeLoad(maybeNormalizeUrl(url))
          .fit()
          .centerCrop()
          .transform(RoundedCornersTransformation(16, 0, corner))
          .into(imageView)
      }
    }
  }

  private fun watchEventBus() {
    lifecycleScope.launch(Main) {
      CommandBus.get().collect { command ->
        when (command) {
          is Command.RefreshTrack -> refreshCurrentTrack(command.track)
        }
      }
    }
  }

  private fun refreshCurrentTrack(track: Track?) {
    track?.let {
      adapter.currentTrack = track
      adapter.notifyDataSetChanged()
    }
  }

  inner class PlaylistListener : PlaylistTracksAdapter.OnPlaylistListener {
    override fun onMoveTrack(from: Int, to: Int) {
      playlistsRepository.move(albumId, from, to)
    }

    override fun onRemoveTrackFromPlaylist(track: Track, index: Int) {
      lifecycleScope.launch(Main) {
        playlistsRepository.remove(albumId, index)
        update()
      }
    }
  }
}
