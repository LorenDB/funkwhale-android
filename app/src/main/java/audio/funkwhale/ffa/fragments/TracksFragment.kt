package audio.funkwhale.ffa.fragments

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.adapters.FavoriteListener
import audio.funkwhale.ffa.adapters.TracksAdapter
import audio.funkwhale.ffa.databinding.FragmentTracksBinding
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.FavoritedRepository
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.TracksRepository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.Request
import audio.funkwhale.ffa.utils.RequestBus
import audio.funkwhale.ffa.utils.Response
import audio.funkwhale.ffa.utils.getMetadata
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.toast
import audio.funkwhale.ffa.utils.wait
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.preference.PowerPreference
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class TracksFragment : FFAFragment<Track, TracksAdapter>() {
  private val args by navArgs<TracksFragmentArgs>()
  private val exoDownloadManager: DownloadManager by inject(DownloadManager::class.java)

  override val recycler: RecyclerView get() = binding.tracks

  private var _binding: FragmentTracksBinding? = null
  private val binding get() = _binding!!

  private lateinit var favoritesRepository: FavoritesRepository
  private lateinit var favoritedRepository: FavoritedRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    favoritesRepository = FavoritesRepository(context)
    favoritedRepository = FavoritedRepository(context)
    repository = TracksRepository(context, args.album.id)

    adapter = TracksAdapter(layoutInflater, context, FavoriteListener(favoritesRepository))

    watchEventBus()
  }

  override fun onDataFetched(data: List<Track>) {

    when {
      data.isNotEmpty() && data.all { it.downloaded } -> {
        binding.title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.downloaded, 0, 0, 0)
        binding.title.compoundDrawables.forEach {
          it?.colorFilter =
            PorterDuffColorFilter(
              requireContext().getColor(R.color.downloaded),
              PorterDuff.Mode.SRC_IN
            )
        }
      }
      data.isNotEmpty() && data.all { it.cached } -> {
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

    when (PowerPreference.getDefaultFile().getString("play_order")) {
      "in_order" -> binding.play.text = getString(R.string.playback_play)
      else -> binding.play.text = getString(R.string.playback_shuffle)
    }

    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    CoverArt.requestCreator(maybeNormalizeUrl(args.album.cover()))
      .noFade()
      .fit()
      .centerCrop()
      .transform(RoundedCornersTransformation(16, 0))
      .into(binding.cover)

    binding.artist.text = args.album.artist.name
    binding.title.text = args.album.title
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
              R.id.play_secondary -> when (
                PowerPreference.getDefaultFile()
                  .getString("play_order")
              ) {
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
        if (message is Event.DownloadChanged) {
          refreshDownloadedTrack(message.download)
        }
      }
    }

    lifecycleScope.launch(Main) {
      CommandBus.get().collect { command ->
        if (command is Command.RefreshTrack) {
          refreshCurrentTrack(command.track)
        }
      }
    }
  }

  private suspend fun refreshDownloadedTracks() {
    val downloaded = TracksRepository.getDownloadedIds(exoDownloadManager) ?: listOf()

    withContext(Main) {
      adapter.setUnfilteredData(
        adapter.data.map {
          it.downloaded = downloaded.contains(it.id)
          it
        }.toMutableList()
      )

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
}
