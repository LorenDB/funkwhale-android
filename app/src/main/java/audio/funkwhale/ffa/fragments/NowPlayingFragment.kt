package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.customview.widget.Openable
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import audio.funkwhale.ffa.MainNavDirections
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.FragmentNowPlayingBinding
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.FavoritedRepository
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.utils.*
import audio.funkwhale.ffa.viewmodel.NowPlayingViewModel
import audio.funkwhale.ffa.views.NowPlayingBottomSheet
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NowPlayingFragment : Fragment(R.layout.fragment_now_playing) {
  private val binding by lazy { FragmentNowPlayingBinding.bind(requireView()) }
  private val viewModel by viewModels<NowPlayingViewModel>()
  private val favoriteRepository by lazy { FavoritesRepository(requireContext()) }
  private val favoritedRepository by lazy { FavoritedRepository(requireContext()) }

  private val bottomSheet: BottomSheetIneractable? by lazy {
    var view = this.view?.parent
    while (view != null) {
      if(view is BottomSheetIneractable) return@lazy view
      view = view.parent
    }
    null
  }

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

    with(binding.header) {
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
    }

    binding.nowPlayingDetailsInfo.setOnClickListener { openInfoMenu() }

    lifecycleScope.launch(Dispatchers.Main) {
      CommandBus.get().collect { onCommand(it) }
    }

    lifecycleScope.launch(Dispatchers.Main) {
      EventBus.get().collect { onEvent(it) }
    }

    lifecycleScope.launch(Dispatchers.Main) {
      ProgressBus.get().collect { onProgress(it) }
    }
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

  private fun onEvent(event: Event): Unit = when (event) {
    is Event.Buffering -> viewModel.isBuffering.postValue(event.value)
    is Event.StateChanged -> viewModel.isPlaying.postValue(event.playing)
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
      binding.nowPlayingDetailCover.setImageResource(R.drawable.cover)
      return
    }

    CoverArt.withContext(requireContext(), maybeNormalizeUrl(track.album?.cover()))
      .fit()
      .centerCrop()
      .into(binding.nowPlayingDetailCover)

    CoverArt.withContext(requireContext(), maybeNormalizeUrl(track.album?.cover()))
      .fit()
      .centerCrop()
      .transform(RoundedCornersTransformation(16, 0))
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
        bottomSheet?.close()

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