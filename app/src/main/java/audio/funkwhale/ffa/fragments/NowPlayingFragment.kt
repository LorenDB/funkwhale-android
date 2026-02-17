package audio.funkwhale.ffa.fragments

import android.graphics.Color
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
import androidx.palette.graphics.Palette
import android.graphics.drawable.GradientDrawable
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
import audio.funkwhale.ffa.views.SwipeableConstraintLayout
import audio.funkwhale.ffa.views.SwipeableSquareImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlin.math.max

class NowPlayingFragment: Fragment(R.layout.fragment_now_playing) {
  private val binding by lazy { FragmentNowPlayingBinding.bind(requireView()) }
  private val viewModel by viewModels<NowPlayingViewModel>()
  private val favoriteRepository by lazy { FavoritesRepository(requireContext()) }
  private val favoritedRepository by lazy { FavoritedRepository(requireContext()) }

  private var onDetailsMenuItemClickedCb: () -> Unit = {}
  private var maxGradientRadius = 0f
  private var isExpanded = false
  private var isTransitioning = false
  private var currentAlbumArtUrl: String? = null

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

      // Set up swipe gestures on the album cover.
      // The cover translates itself; the header bar (headerControls)
      // co-moves so the background, track info, and cover all slide
      // as one visual unit.  The playback buttons are counter-translated
      // so they appear to stay in place.
      nowPlayingCover.setAdditionalSwipeTargets(listOf(headerControls))
      nowPlayingCover.setStaticViews(listOf(nowPlayingToggle, nowPlayingNext))
      nowPlayingCover.setOnSwipeListener(object : SwipeableSquareImageView.OnSwipeListener {
        override fun onSwipeLeft() {
          CommandBus.send(Command.NextTrack)
        }

        override fun onSwipeRight() {
          CommandBus.send(Command.PreviousTrack)
        }
      })

      // Set up swipe gestures on the header controls (playback bar).
      // The layout translates itself (carrying its background and track
      // info with it); the album cover co-moves.  The playback buttons
      // are counter-translated so they stay visually static.
      headerControls.setCoMovingViews(listOf(nowPlayingCover))
      headerControls.setStaticViews(listOf(nowPlayingToggle, nowPlayingNext))
      headerControls.setOnSwipeListener(object : SwipeableConstraintLayout.OnSwipeListener {
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
    
    // Track transition state and load high-res when starting to expand
    val wasTransitioning = isTransitioning
    val wasExpanded = isExpanded
    isTransitioning = value > 0f && value < 1f
    isExpanded = value >= 0.9f
    
    // Load high-res album art when starting to expand or when fully expanded
    if ((isTransitioning && !wasTransitioning && value > 0f) || (isExpanded && !wasExpanded)) {
      loadAlbumArtHighRes()
    }
    // Load low-res when fully collapsed and not transitioning
    else if (value == 0f && wasTransitioning) {
      loadAlbumArtLowRes()
    }
    
    // Update gradient alpha based on progress to fade it out
    binding.nowPlayingRoot.background?.let { bg ->
      if (bg is GradientDrawable) {
        bg.alpha = (binding.nowPlayingRoot.progress * 255).toInt()
        
        // Get the album cover's current position to anchor the gradient
        val cover = binding.header.nowPlayingCover
        val rootView = binding.nowPlayingRoot
        
        // Calculate the center of the cover relative to the root view
        val coverCenterX = (cover.left + cover.right) / 2f
        val coverCenterY = (cover.top + cover.bottom) / 2f
        
        // Normalize to 0-1 range for gradient center
        val normalizedX = if (rootView.width > 0) coverCenterX / rootView.width else 0.5f
        val normalizedY = if (rootView.height > 0) coverCenterY / rootView.height else 0.35f
        
        bg.setGradientCenter(normalizedX, normalizedY)
      }
    }
    
    // Make status bar transparent when bottom sheet is fully expanded
    requireActivity().window.statusBarColor = if (value >= 0.9f) {
      Color.TRANSPARENT
    } else {
      resources.getColor(R.color.surface, requireContext().theme)
    }
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
      binding.nowPlayingRoot.background = null
      currentAlbumArtUrl = null
      return
    }

    val url = maybeNormalizeUrl(track.album?.cover())
    currentAlbumArtUrl = url
    
    // Load appropriate resolution based on current state
    if (isExpanded || isTransitioning) {
      loadAlbumArtHighRes()
    } else {
      loadAlbumArtLowRes()
    }

    // Extract palette colors and apply a radial gradient to the root layout
    lifecycleScope.launch(Dispatchers.IO) {
      try {
        val bitmap = CoverArt.requestCreator(url).get()
        Palette.from(bitmap).generate { palette ->
          lifecycleScope.launch(Dispatchers.Main) {
            palette?.let { pal ->
              val surfaceColor = resources.getColor(R.color.surface, requireContext().theme)
              val dominantColor = pal.getDominantColor(surfaceColor)
              val vibrantColor = pal.getVibrantColor(dominantColor)
              val mutedColor = pal.getMutedColor(dominantColor)
              
              // Blend the palette colors with the surface color to reduce intensity
              fun blendColors(color: Int, backgroundColor: Int, ratio: Float): Int {
                val r = (android.graphics.Color.red(color) * ratio + android.graphics.Color.red(backgroundColor) * (1 - ratio)).toInt()
                val g = (android.graphics.Color.green(color) * ratio + android.graphics.Color.green(backgroundColor) * (1 - ratio)).toInt()
                val b = (android.graphics.Color.blue(color) * ratio + android.graphics.Color.blue(backgroundColor) * (1 - ratio)).toInt()
                return android.graphics.Color.rgb(r, g, b)
              }
              
              val blendedVibrant = blendColors(vibrantColor, surfaceColor, 0.3f)
              val blendedMuted = blendColors(mutedColor, surfaceColor, 0.2f)
              
              val gradient = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                // Use the view height to scale the radius relative to the screen
                gradientRadius = (binding.nowPlayingRoot.height * 0.7f)
                colors = intArrayOf(
                  blendedVibrant,
                  blendedMuted,
                  surfaceColor
                )
                setGradientCenter(0.5f, 0.35f)
                alpha = (binding.nowPlayingRoot.progress * 255).toInt()
              }
              binding.nowPlayingRoot.background = gradient
              
              // Set max radius (no longer needed for dynamic scaling)
              maxGradientRadius = binding.nowPlayingRoot.height * 0.7f
            }
          }
        }
      } catch (e: Exception) {
        lifecycleScope.launch(Dispatchers.Main) {
          binding.nowPlayingRoot.background = null
        }
      }
    }
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

  private fun loadAlbumArtHighRes() {
    val url = currentAlbumArtUrl ?: return
    CoverArt.requestCreatorNoPlaceholder(url)
      .resize(1200, 1200)
      .centerCrop()
      .onlyScaleDown()
      .transform(RoundedCornersTransformation(32, 0))
      .into(binding.header.nowPlayingCover)
  }

  private fun loadAlbumArtLowRes() {
    val url = currentAlbumArtUrl ?: return
    CoverArt.requestCreatorNoPlaceholder(url)
      .resize(300, 300)
      .centerCrop()
      .onlyScaleDown()
      .transform(RoundedCornersTransformation(32, 0))
      .into(binding.header.nowPlayingCover)
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
