package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.navigation.fragment.findNavController
import audio.funkwhale.ffa.MainNavDirections
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.FragmentNowPlayingBinding
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.FavoritedRepository
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.FFACache
import audio.funkwhale.ffa.utils.ProgressBus
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.toIntOrElse
import audio.funkwhale.ffa.utils.untilNetwork
import audio.funkwhale.ffa.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Float.max

class NowPlayingFragment: Fragment(R.layout.fragment_now_playing) {
  private val binding by lazy { FragmentNowPlayingBinding.bind(requireView()) }
  private val viewModel by viewModels<NowPlayingViewModel>()
  private val favoriteRepository by lazy { FavoritesRepository(requireContext()) }
  private val favoritedRepository by lazy { FavoritedRepository(requireContext()) }

  private var onDetailsMenuItemClickedCb: () -> Unit = {}

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner

    viewModel.currentTrack.distinctUntilChanged().observe(viewLifecycleOwner, ::onTrackChange)

    with(binding.controls) {
      currentTrackTitle = viewModel.currentTrackTitle
      currentTrackArtist = viewModel.currentTrackArtist
      isCurrentTrackFavorite = viewModel.isCurrentTrackFavorite
      repeatModeResource = viewModel.repeatModeResource
      repeatModeAlpha = viewModel.repeatModeAlpha
      currentProgressText = viewModel.currentProgressText
      currentDurationText = viewModel.currentDurationText
      isPlaying = viewModel.isPlaying
      progress = viewModel.progress

      nowPlayingDetailsPrevious.setOnClickListener {
        CommandBus.send(Command.PreviousTrack)
      }

      nowPlayingDetailsNext.setOnClickListener {
        CommandBus.send(Command.NextTrack)
      }

      nowPlayingDetailsToggle.setOnClickListener {
        CommandBus.send(Command.ToggleState)
      }

      nowPlayingDetailsRepeat.setOnClickListener { toggleRepeatMode() }
      nowPlayingDetailsProgress.setOnSeekBarChangeListener(OnSeekBarChanged())
      nowPlayingDetailsFavorite.setOnClickListener { onFavorite() }
      nowPlayingDetailsAddToPlaylist.setOnClickListener { onAddToPlaylist() }
    }

    binding.nowPlayingDetailsInfo.setOnClickListener { openInfoMenu() }

    with(binding.header) {
      lifecycleOwner = viewLifecycleOwner
      isBuffering = viewModel.isBuffering
      isPlaying = viewModel.isPlaying
      progress = viewModel.progress
      currentTrackTitle = viewModel.currentTrackTitle
      currentTrackArtist = viewModel.currentTrackArtist


      nowPlayingNext.setOnClickListener {
        CommandBus.send(Command.NextTrack)
      }

      nowPlayingToggle.setOnClickListener {
        CommandBus.send(Command.ToggleState)
      }

      // Set up swipe gestures on the album cover
      nowPlayingCover.setOnSwipeListener(object : audio.funkwhale.ffa.views.SwipeableSquareImageView.OnSwipeListener {
        override fun onSwipeLeft() {
          CommandBus.send(Command.NextTrack)
        }

        override fun onSwipeRight() {
          CommandBus.send(Command.PreviousTrack)
        }
      })

      // Set up swipe gestures on the header controls (playback bar)
      headerControls.setOnSwipeListener(object : audio.funkwhale.ffa.views.SwipeableConstraintLayout.OnSwipeListener {
        override fun onSwipeLeft() {
          CommandBus.send(Command.NextTrack)
        }

        override fun onSwipeRight() {
          CommandBus.send(Command.PreviousTrack)
        }
      })
    }

    lifecycleScope.launch(Dispatchers.Main) {
      CommandBus.get().collect { onCommand(it) }
    }

    lifecycleScope.launch(Dispatchers.Main) {
      ProgressBus.get().collect { onProgress(it) }
    }
  }

  fun onBottomSheetDrag(value: Float) {
    binding.nowPlayingRoot.progress = max(value, 0f)
  }

  fun onDetailsMenuItemClicked(cb: () -> Unit) {
    onDetailsMenuItemClickedCb = cb
  }


  private fun toggleRepeatMode() {
    val cachedRepeatMode = FFACache.getLine(requireContext(), "repeat").toIntOrElse(0)
    val iteratedRepeatMode = (cachedRepeatMode + 1) % 3
    FFACache.set(requireContext(), "repeat", "$iteratedRepeatMode")
    CommandBus.send(Command.SetRepeatMode(iteratedRepeatMode))
  }

  private fun onAddToPlaylist() {
    val currentTrack = viewModel.currentTrack.value ?: return
    CommandBus.send(Command.AddToPlaylist(listOf(currentTrack)))
  }

  private fun onCommand(command: Command) = when (command) {
    is Command.RefreshTrack -> refreshCurrentTrack(command.track)
    is Command.SetRepeatMode -> viewModel.repeatMode.postValue(command.mode)
    else -> {}
  }

  private fun onFavorite() {
    val currentTrack = viewModel.currentTrack.value ?: return

    if (currentTrack.favorite) favoriteRepository.deleteFavorite(currentTrack.id)
    else favoriteRepository.addFavorite(currentTrack.id)

    currentTrack.favorite = !currentTrack.favorite
    // Trigger UI refresh
    viewModel.currentTrack.postValue(viewModel.currentTrack.value)

    favoritedRepository.fetch(Repository.Origin.Network.origin)
  }

  private fun onProgress(state: Triple<Int, Int, Int>) {
    val (current, duration, percent) = state

    val currentMins = (current / 1000) / 60
    val currentSecs = (current / 1000) % 60

    val durationMins = duration / 60
    val durationSecs = duration % 60

    viewModel.progress.postValue(percent)
    viewModel.currentProgressText.postValue("%02d:%02d".format(currentMins, currentSecs))
    viewModel.currentDurationText.postValue("%02d:%02d".format(durationMins, durationSecs))
  }

  private fun onTrackChange(track: Track?) {
    if (track == null) {
      binding.header.nowPlayingCover.setImageResource(R.drawable.cover)
      return
    }

    CoverArt.requestCreator(maybeNormalizeUrl(track.album?.cover()))
      .into(binding.header.nowPlayingCover)
  }

  private fun openInfoMenu() {
    val currentTrack = viewModel.currentTrack.value ?: return

    PopupMenu(
      requireContext(),
      binding.nowPlayingDetailsInfo,
      Gravity.START,
      R.attr.actionOverflowMenuStyle,
      0
    ).apply {
      inflate(R.menu.track_info)

      setOnMenuItemClickListener {
        onDetailsMenuItemClickedCb()

        when (it.itemId) {
          R.id.track_info_artist -> findNavController().navigate(
            MainNavDirections.globalBrowseToAlbums(
              currentTrack.artist,
              currentTrack.album?.cover()
            )
          )
          R.id.track_info_album -> currentTrack.album?.let { album ->
            findNavController().navigate(MainNavDirections.globalBrowseTracks(album))
          }
          R.id.track_info_details -> TrackInfoDetailsFragment.new(currentTrack).show(
            requireActivity().supportFragmentManager, "dialog"
          )
        }

        true
      }

      show()
    }
  }

  private fun refreshCurrentTrack(track: Track?) {
    viewModel.currentTrack.postValue(track)

    val cachedRepeatMode = FFACache.getLine(requireContext(), "repeat").toIntOrElse(0)
    viewModel.repeatMode.postValue(cachedRepeatMode % 3)

    // At this point, a non-null track is required

    if (track == null) return

    favoritedRepository.fetch().untilNetwork(lifecycleScope, Dispatchers.IO) { favorites, _, _, _ ->
      lifecycleScope.launch(Dispatchers.Main) {
        track.favorite = favorites.contains(track.id)
        // Trigger UI refresh
        viewModel.currentTrack.postValue(viewModel.currentTrack.value)
      }
    }
  }

  inner class OnSeekBarChanged : OnSeekBarChangeListener {
    override fun onStopTrackingTouch(view: SeekBar?) {}

    override fun onStartTrackingTouch(view: SeekBar?) {}

    override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
      if (fromUser) {
        CommandBus.send(Command.Seek(progress))
      }
    }
  }
}
