package audio.funkwhale.ffa.fragments

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import audio.funkwhale.ffa.adapters.TracksAdapter
import audio.funkwhale.ffa.databinding.FragmentTracksBinding
import audio.funkwhale.ffa.repositories.FavoritedRepository
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.TracksRepository
import audio.funkwhale.ffa.utils.*
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.preference.PowerPreference
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class TracksFragment : FFAFragment<Track, TracksAdapter>() {

  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)

  override val recycler: RecyclerView get() = binding.tracks

  private var _binding: FragmentTracksBinding? = null
  private val binding get() = _binding!!

  private lateinit var favoritesRepository: FavoritesRepository
  private lateinit var favoritedRepository: FavoritedRepository

  private var albumId = 0
  private var albumArtist = ""
  private var albumTitle = ""
  private var albumCover = ""

  companion object {
    fun new(album: Album): TracksFragment {
      return TracksFragment().apply {
        arguments = bundleOf(
          "albumId" to album.id,
          "albumArtist" to album.artist.name,
          "albumTitle" to album.title,
          "albumCover" to album.cover()
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

    adapter = TracksAdapter(layoutInflater, context, FavoriteListener())
    repository = TracksRepository(context, albumId)
    favoritesRepository = FavoritesRepository(context)
    favoritedRepository = FavoritedRepository(context)

    watchEventBus()
  }

  override fun onDataFetched(data: List<Track>) {

    when {
      data.all { it.downloaded } -> {
        binding.title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.downloaded, 0, 0, 0)
        binding.title.compoundDrawables.forEach {
          it?.colorFilter =
            PorterDuffColorFilter(
              requireContext().getColor(R.color.downloaded),
              PorterDuff.Mode.SRC_IN
            )
        }
      }
      data.all { it.cached } -> {
        binding.title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.downloaded, 0, 0, 0)
        binding.title.compoundDrawables.forEach {
          it?.colorFilter =
            PorterDuffColorFilter(
              requireContext().getColor(R.color.cached),
              PorterDuff.Mode.SRC_IN
            )
        }
      }
      else -> {
        binding.title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentTracksBinding.inflate(inflater)
    swiper = binding.swiper
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    Picasso.get()
      .maybeLoad(maybeNormalizeUrl(albumCover))
      .noFade()
      .fit()
      .centerCrop()
      .transform(RoundedCornersTransformation(16, 0))
      .into(binding.cover)

    binding.artist.text = albumArtist
    binding.title.text = albumTitle
  }

  override fun onResume() {
    super.onResume()

    lifecycleScope.launch(Main) {
      RequestBus.send(Request.GetCurrentTrack).wait<Response.CurrentTrack>()?.let { response ->
        adapter.currentTrack = response.track
        adapter.notifyDataSetChanged()
      }

      refreshDownloadedTracks()
    }

    var coverHeight: Float? = null

    binding.scroller.setOnScrollChangeListener { _: View?, _: Int, scrollY: Int, _: Int, _: Int ->
      if (coverHeight == null) {
        coverHeight = binding.cover.measuredHeight.toFloat()
      }

      binding.cover.translationY = (scrollY / 2).toFloat()

      coverHeight?.let { height ->
        binding.cover.alpha = (height - scrollY.toFloat()) / height
      }
    }

    when (PowerPreference.getDefaultFile().getString("play_order")) {
      "in_order" -> binding.play.text = getString(R.string.playback_play)
      else -> binding.play.text = getString(R.string.playback_shuffle)
    }

    binding.play.setOnClickListener {
      when (PowerPreference.getDefaultFile().getString("play_order")) {
        "in_order" -> CommandBus.send(Command.ReplaceQueue(adapter.data))
        else -> CommandBus.send(Command.ReplaceQueue(adapter.data.shuffled()))
      }

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

          menu.findItem(R.id.play_secondary)?.let { item ->
            when (PowerPreference.getDefaultFile().getString("play_order")) {
              "in_order" -> item.title = getString(R.string.playback_shuffle)
              else -> item.title = getString(R.string.playback_play)
            }
          }

          setOnMenuItemClickListener {
            when (it.itemId) {
              R.id.play_secondary -> when (PowerPreference.getDefaultFile()
                .getString("play_order")) {
                "in_order" -> CommandBus.send(Command.ReplaceQueue(adapter.data.shuffled()))
                else -> CommandBus.send(Command.ReplaceQueue(adapter.data))
              }

              R.id.add_to_queue -> {
                when (PowerPreference.getDefaultFile().getString("play_order")) {
                  "in_order" -> CommandBus.send(Command.AddToQueue(adapter.data))
                  else -> CommandBus.send(Command.AddToQueue(adapter.data.shuffled()))
                }

                context.toast("All tracks were added to your queue")
              }

              R.id.download -> CommandBus.send(Command.PinTracks(adapter.data))
            }

            true
          }

          show()
        }
      }
    }
  }

  private fun watchEventBus() {
    lifecycleScope.launch(IO) {
      EventBus.get().collect { message ->
        when (message) {
          is Event.DownloadChanged -> refreshDownloadedTrack(message.download)
        }
      }
    }

    lifecycleScope.launch(Main) {
      CommandBus.get().collect { command ->
        when (command) {
          is Command.RefreshTrack -> refreshCurrentTrack(command.track)
        }
      }
    }
  }

  private suspend fun refreshDownloadedTracks() {
    val downloaded = TracksRepository.getDownloadedIds(exoDownloadManager) ?: listOf()

    withContext(Main) {
      adapter.data = adapter.data.map {
        it.downloaded = downloaded.contains(it.id)
        it
      }.toMutableList()

      adapter.notifyDataSetChanged()
    }
  }

  private suspend fun refreshDownloadedTrack(download: Download) {
    if (download.state == Download.STATE_COMPLETED) {
      download.getMetadata()?.let { info ->
        adapter.data.withIndex().associate { it.value to it.index }.filter { it.key.id == info.id }
          .toList().getOrNull(0)?.let { match ->
            withContext(Main) {
              adapter.data[match.second].downloaded = true
              adapter.notifyItemChanged(match.second)
            }
          }
      }
    }
  }

  private fun refreshCurrentTrack(track: Track?) {
    track?.let {
      adapter.currentTrack?.current = false
      adapter.currentTrack = track.apply {
        current = true
      }

      adapter.notifyDataSetChanged()
    }
  }

  inner class FavoriteListener : TracksAdapter.OnFavoriteListener {
    override fun onToggleFavorite(id: Int, state: Boolean) {
      when (state) {
        true -> favoritesRepository.addFavorite(id)
        false -> favoritesRepository.deleteFavorite(id)
      }
    }
  }
}
