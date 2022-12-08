package audio.funkwhale.ffa.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.ActivityMainBinding
import audio.funkwhale.ffa.fragments.AddToPlaylistDialog
import audio.funkwhale.ffa.fragments.AlbumsFragment
import audio.funkwhale.ffa.fragments.ArtistsFragment
import audio.funkwhale.ffa.fragments.BrowseFragment
import audio.funkwhale.ffa.fragments.LandscapeQueueFragment
import audio.funkwhale.ffa.fragments.QueueFragment
import audio.funkwhale.ffa.fragments.TrackInfoDetailsFragment
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.playback.MediaControlsManager
import audio.funkwhale.ffa.playback.PinService
import audio.funkwhale.ffa.playback.PlayerService
import audio.funkwhale.ffa.repositories.FavoritedRepository
import audio.funkwhale.ffa.repositories.FavoritesRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.utils.AppContext
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.Event
import audio.funkwhale.ffa.utils.EventBus
import audio.funkwhale.ffa.utils.FFACache
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.ProgressBus
import audio.funkwhale.ffa.utils.Settings
import audio.funkwhale.ffa.utils.Userinfo
import audio.funkwhale.ffa.utils.authorize
import audio.funkwhale.ffa.utils.log
import audio.funkwhale.ffa.utils.logError
import audio.funkwhale.ffa.utils.maybeLoad
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import audio.funkwhale.ffa.utils.mustNormalizeUrl
import audio.funkwhale.ffa.utils.onApi
import audio.funkwhale.ffa.utils.toast
import audio.funkwhale.ffa.utils.untilNetwork
import audio.funkwhale.ffa.views.DisableableFrameLayout
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitStringResponse
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.offline.DownloadService
import com.google.gson.Gson
import com.preference.PowerPreference
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

class MainActivity : AppCompatActivity() {
  enum class ResultCode(val code: Int) {
    LOGOUT(1001)
  }

  private val favoriteRepository = FavoritesRepository(this)
  private val favoritedRepository = FavoritedRepository(this)
  private var menu: Menu? = null

  private lateinit var binding: ActivityMainBinding
  private val oAuth: OAuth by inject(OAuth::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    AppContext.init(this)

    binding = ActivityMainBinding.inflate(layoutInflater)

    setContentView(binding.root)

    setSupportActionBar(binding.appbar)

    when (intent.action) {
      MediaControlsManager.NOTIFICATION_ACTION_OPEN_QUEUE.toString() -> launchDialog(QueueFragment())
    }

    supportFragmentManager
      .beginTransaction()
      .replace(R.id.container, BrowseFragment())
      .commit()

    watchEventBus()
  }

  override fun onResume() {
    super.onResume()

    (binding.container as? DisableableFrameLayout)?.setShouldRegisterTouch { _ ->
      if (binding.nowPlaying.isOpened()) {
        binding.nowPlaying.close()

        return@setShouldRegisterTouch false
      }

      true
    }

    favoritedRepository.update(this, lifecycleScope)

    startService(Intent(this, PlayerService::class.java))
    DownloadService.start(this, PinService::class.java)

    CommandBus.send(Command.RefreshService)

    lifecycleScope.launch(IO) {
      Userinfo.get(this@MainActivity, oAuth)
    }

    with(binding) {

      nowPlayingContainer?.nowPlayingToggle?.setOnClickListener {
        CommandBus.send(Command.ToggleState)
      }

      nowPlayingContainer?.nowPlayingNext?.setOnClickListener {
        CommandBus.send(Command.NextTrack)
      }

      nowPlayingContainer?.nowPlayingDetailsPrevious?.setOnClickListener {
        CommandBus.send(Command.PreviousTrack)
      }

      nowPlayingContainer?.nowPlayingDetailsNext?.setOnClickListener {
        CommandBus.send(Command.NextTrack)
      }

      nowPlayingContainer?.nowPlayingDetailsToggle?.setOnClickListener {
        CommandBus.send(Command.ToggleState)
      }

      binding.nowPlayingContainer?.nowPlayingDetailsProgress?.setOnSeekBarChangeListener(
        object : SeekBar.OnSeekBarChangeListener {
          override fun onStopTrackingTouch(view: SeekBar?) {}

          override fun onStartTrackingTouch(view: SeekBar?) {}

          override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) {
              CommandBus.send(Command.Seek(progress))
            }
          }
        })

      landscapeQueue?.let {
        supportFragmentManager.beginTransaction()
          .replace(R.id.landscape_queue, LandscapeQueueFragment()).commit()
      }
    }
  }

  override fun onBackPressed() {
    if (binding.nowPlaying.isOpened()) {
      binding.nowPlaying.close()
      return
    }

    super.onBackPressed()
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    this.menu = menu

    return super.onPrepareOptionsMenu(menu)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.toolbar, menu)

    menu.findItem(R.id.nav_all_music)?.let {
      it.isChecked = Settings.getScopes().contains("all")
      it.isEnabled = !it.isChecked
    }

    menu.findItem(R.id.nav_my_music)?.isChecked = Settings.getScopes().contains("me")
    menu.findItem(R.id.nav_followed)?.isChecked = Settings.getScopes().contains("subscribed")

    return true
  }

  var resultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
    if (result.resultCode == ResultCode.LOGOUT.code) {
      Intent(this, LoginActivity::class.java).apply {
        FFA.get().deleteAllData(this@MainActivity)

        flags =
          Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        stopService(Intent(this@MainActivity, PlayerService::class.java))
        startActivity(this)
        finish()
      }
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        binding.nowPlaying.close()

        (supportFragmentManager.fragments.last() as? BrowseFragment)?.let {
          it.selectTabAt(0)

          return true
        }

        launchFragment(BrowseFragment())
      }

      R.id.nav_queue -> launchDialog(QueueFragment())
      R.id.nav_search -> startActivity(Intent(this, SearchActivity::class.java))
      R.id.nav_all_music, R.id.nav_my_music, R.id.nav_followed -> {
        menu?.let { menu ->
          item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
          item.actionView = View(this)
          item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = false
            override fun onMenuItemActionCollapse(item: MenuItem) = false
          })

          item.isChecked = !item.isChecked

          val scopes = Settings.getScopes().toMutableSet()

          val new = when (item.itemId) {
            R.id.nav_my_music -> "me"
            R.id.nav_followed -> "subscribed"

            else -> {
              menu.findItem(R.id.nav_all_music).isEnabled = false
              menu.findItem(R.id.nav_my_music).isChecked = false
              menu.findItem(R.id.nav_followed).isChecked = false
              PowerPreference.getDefaultFile().setString("scope", "all")
              EventBus.send(Event.ListingsChanged)

              return false
            }
          }

          menu.findItem(R.id.nav_all_music).let {
            it.isChecked = false
            it.isEnabled = true
          }

          scopes.remove("all")

          when (item.isChecked) {
            true -> scopes.add(new)
            false -> scopes.remove(new)
          }

          if (scopes.isEmpty()) {
            menu.findItem(R.id.nav_all_music).let {
              it.isChecked = true
              it.isEnabled = false
            }

            scopes.add("all")
          }

          PowerPreference.getDefaultFile().setString("scope", scopes.joinToString(","))
          EventBus.send(Event.ListingsChanged)

          return false
        }
      }
      R.id.nav_downloads -> startActivity(Intent(this, DownloadsActivity::class.java))
      R.id.settings -> resultLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    return true
  }

  private fun launchFragment(fragment: Fragment) {
    supportFragmentManager.fragments.lastOrNull()?.also { oldFragment ->
      oldFragment.enterTransition = null
      oldFragment.exitTransition = null

      supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    supportFragmentManager
      .beginTransaction()
      .setCustomAnimations(0, 0, 0, 0)
      .replace(R.id.container, fragment)
      .commit()
  }

  private fun launchDialog(fragment: DialogFragment) {
    supportFragmentManager.beginTransaction().let {
      fragment.show(it, "")
    }
  }

  @SuppressLint("NewApi")
  private fun watchEventBus() {
    lifecycleScope.launch(Main) {
      EventBus.get().collect { event ->
        if (event is Event.LogOut) {
          FFA.get().deleteAllData(this@MainActivity)
          startActivity(
            Intent(this@MainActivity, LoginActivity::class.java).apply {
              flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            }
          )

          finish()
        } else if (event is Event.PlaybackError) {
          toast(event.message)
        } else if (event is Event.Buffering) {
          when (event.value) {
            true -> binding.nowPlayingContainer?.nowPlayingBuffering?.visibility = View.VISIBLE
            false -> binding.nowPlayingContainer?.nowPlayingBuffering?.visibility = View.GONE
          }
        } else if (event is Event.PlaybackStopped) {
          if (binding.nowPlaying.visibility == View.VISIBLE) {
            (binding.container.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
              it.bottomMargin = it.bottomMargin / 2
            }

            binding.landscapeQueue?.let { landscape_queue ->
              (landscape_queue.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.bottomMargin = it.bottomMargin / 2
              }
            }

            binding.nowPlaying.animate()
              .alpha(0.0f)
              .setDuration(400)
              .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                  binding.nowPlaying.visibility = View.GONE
                }
              })
              .start()
          }
        } else if (event is Event.TrackFinished) {
          incrementListenCount(event.track)
        } else if (event is Event.StateChanged) {
          when (event.playing) {
            true -> {
              binding.nowPlayingContainer?.nowPlayingToggle?.icon = getDrawable(R.drawable.pause)
              binding.nowPlayingContainer?.nowPlayingDetailsToggle?.icon =
                getDrawable(R.drawable.pause)
            }

            false -> {
              binding.nowPlayingContainer?.nowPlayingToggle?.icon = getDrawable(R.drawable.play)
              binding.nowPlayingContainer?.nowPlayingDetailsToggle?.icon =
                getDrawable(R.drawable.play)
            }
          }
        } else if (event is Event.QueueChanged) {
          findViewById<View>(R.id.nav_queue)?.let { view ->
            ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).let {
              it.duration = 500
              it.interpolator = AccelerateDecelerateInterpolator()
              it.start()
            }
          }
        }
      }
    }

    lifecycleScope.launch(Main) {
      CommandBus.get().collect { command ->
        if (command is Command.StartService) {
          Build.VERSION_CODES.O.onApi(
            {
              startForegroundService(
                Intent(
                  this@MainActivity,
                  PlayerService::class.java
                ).apply {
                  putExtra(PlayerService.INITIAL_COMMAND_KEY, command.command.toString())
                }
              )
            },
            {
              startService(
                Intent(this@MainActivity, PlayerService::class.java).apply {
                  putExtra(PlayerService.INITIAL_COMMAND_KEY, command.command.toString())
                }
              )
            }
          )
        } else if (command is Command.RefreshTrack) {
          refreshCurrentTrack(command.track)
        } else if (command is Command.AddToPlaylist) {
          if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            AddToPlaylistDialog.show(
              layoutInflater,
              this@MainActivity,
              lifecycleScope,
              command.tracks
            )
          }
        }
      }
    }

    lifecycleScope.launch(Main) {
      ProgressBus.get().collect { (current, duration, percent) ->
        binding.nowPlayingContainer?.nowPlayingProgress?.progress = percent
        binding.nowPlayingContainer?.nowPlayingDetailsProgress?.progress = percent

        val currentMins = (current / 1000) / 60
        val currentSecs = (current / 1000) % 60

        val durationMins = duration / 60
        val durationSecs = duration % 60

        binding.nowPlayingContainer?.nowPlayingDetailsProgressCurrent?.text =
          "%02d:%02d".format(currentMins, currentSecs)
        binding.nowPlayingContainer?.nowPlayingDetailsProgressDuration?.text =
          "%02d:%02d".format(durationMins, durationSecs)
      }
    }
  }

  private fun refreshCurrentTrack(track: Track?) {
    track?.let {
      if (binding.nowPlaying.visibility == View.GONE) {
        binding.nowPlaying.visibility = View.VISIBLE
        binding.nowPlaying.alpha = 0f

        binding.nowPlaying.animate()
          .alpha(1.0f)
          .setDuration(400)
          .setListener(null)
          .start()

        (binding.container.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
          it.bottomMargin = it.bottomMargin * 2
        }

        binding.landscapeQueue?.let { landscape_queue ->
          (landscape_queue.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.bottomMargin = it.bottomMargin * 2
          }
        }
      }

      binding.nowPlayingContainer?.nowPlayingTitle?.text = track.title
      binding.nowPlayingContainer?.nowPlayingAlbum?.text = track.artist.name

      binding.nowPlayingContainer?.nowPlayingDetailsTitle?.text = track.title
      binding.nowPlayingContainer?.nowPlayingDetailsArtist?.text = track.artist.name

      Picasso.get()
        .maybeLoad(maybeNormalizeUrl(track.album?.cover?.urls?.original))
        .fit()
        .centerCrop()
        .into(binding.nowPlayingContainer?.nowPlayingCover)

      binding.nowPlayingContainer?.nowPlayingDetailsCover?.let { nowPlayingDetailsCover ->
        Picasso.get()
          .maybeLoad(maybeNormalizeUrl(track.album?.cover()))
          .fit()
          .centerCrop()
          .transform(RoundedCornersTransformation(16, 0))
          .into(nowPlayingDetailsCover)
      }

      if (binding.nowPlayingContainer?.nowPlayingCover == null) {
        lifecycleScope.launch(Default) {
          val width = DisplayMetrics().apply {
            windowManager.defaultDisplay.getMetrics(this)
          }.widthPixels

          val backgroundCover = Picasso.get()
            .maybeLoad(maybeNormalizeUrl(track.album?.cover()))
            .get()
            .run { Bitmap.createScaledBitmap(this, width, width, false).toDrawable(resources) }
            .apply {
              alpha = 20
              gravity = Gravity.CENTER
            }

          withContext(Main) {
            binding.nowPlayingContainer?.nowPlayingDetails?.background = backgroundCover
          }
        }
      }

      binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.let { now_playing_details_repeat ->
        changeRepeatMode(FFACache.getLine(this@MainActivity, "repeat")?.toInt() ?: 0)

        now_playing_details_repeat.setOnClickListener {
          val current = FFACache.getLine(this@MainActivity, "repeat")?.toInt() ?: 0

          changeRepeatMode((current + 1) % 3)
        }
      }

      binding.nowPlayingContainer?.nowPlayingDetailsInfo?.let { nowPlayingDetailsInfo ->
        nowPlayingDetailsInfo.setOnClickListener {
          PopupMenu(
            this@MainActivity,
            nowPlayingDetailsInfo,
            Gravity.START,
            R.attr.actionOverflowMenuStyle,
            0
          ).apply {
            inflate(R.menu.track_info)

            setOnMenuItemClickListener {
              when (it.itemId) {
                R.id.track_info_artist -> ArtistsFragment.openAlbums(
                  this@MainActivity,
                  track.artist,
                  art = track.album?.cover()
                )
                R.id.track_info_album -> AlbumsFragment.openTracks(this@MainActivity, track.album)
                R.id.track_info_details -> TrackInfoDetailsFragment.new(track)
                  .show(supportFragmentManager, "dialog")
              }

              binding.nowPlaying.close()

              true
            }

            show()
          }
        }
      }

      binding.nowPlayingContainer?.nowPlayingDetailsFavorite?.let { now_playing_details_favorite ->
        favoritedRepository.fetch().untilNetwork(lifecycleScope, IO) { favorites, _, _, _ ->
          lifecycleScope.launch(Main) {
            track.favorite = favorites.contains(track.id)

            when (track.favorite) {
              true -> now_playing_details_favorite.setColorFilter(getColor(R.color.colorFavorite))
              false -> now_playing_details_favorite.setColorFilter(getColor(R.color.controlForeground))
            }
          }
        }

        now_playing_details_favorite.setOnClickListener {
          when (track.favorite) {
            true -> {
              favoriteRepository.deleteFavorite(track.id)
              now_playing_details_favorite.setColorFilter(getColor(R.color.controlForeground))
            }

            false -> {
              favoriteRepository.addFavorite(track.id)
              now_playing_details_favorite.setColorFilter(getColor(R.color.colorFavorite))
            }
          }

          track.favorite = !track.favorite

          favoriteRepository.fetch(Repository.Origin.Network.origin)
        }

        binding.nowPlayingContainer?.nowPlayingDetailsAddToPlaylist?.setOnClickListener {
          CommandBus.send(Command.AddToPlaylist(listOf(track)))
        }
      }
    }
  }

  private fun changeRepeatMode(index: Int) {
    when (index) {
      // From no repeat to repeat all
      0 -> {
        FFACache.set(this@MainActivity, "repeat", "0")

        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.setImageResource(R.drawable.repeat)
        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.setColorFilter(
          ContextCompat.getColor(
            this,
            R.color.controlForeground
          )
        )
        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.alpha = 0.2f

        CommandBus.send(Command.SetRepeatMode(Player.REPEAT_MODE_OFF))
      }

      // From repeat all to repeat one
      1 -> {
        FFACache.set(this@MainActivity, "repeat", "1")

        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.setImageResource(R.drawable.repeat)
        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.setColorFilter(
          ContextCompat.getColor(
            this,
            R.color.controlForeground
          )
        )
        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.alpha = 1.0f

        CommandBus.send(Command.SetRepeatMode(Player.REPEAT_MODE_ALL))
      }

      // From repeat one to no repeat
      2 -> {
        FFACache.set(this@MainActivity, "repeat", "2")
        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.setImageResource(R.drawable.repeat_one)
        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.setColorFilter(
          ContextCompat.getColor(
            this,
            R.color.controlForeground
          )
        )
        binding.nowPlayingContainer?.nowPlayingDetailsRepeat?.alpha = 1.0f

        CommandBus.send(Command.SetRepeatMode(Player.REPEAT_MODE_ONE))
      }
    }
  }

  private fun incrementListenCount(track: Track?) {
    track?.let {
      it.log("Incrementing listen count for track ${track.id}")
      lifecycleScope.launch(IO) {
        try {
          Fuel
            .post(mustNormalizeUrl("/api/v1/history/listenings/"))
            .authorize(this@MainActivity, oAuth)
            .header("Content-Type", "application/json")
            .body(Gson().toJson(mapOf("track" to track.id)))
            .awaitStringResponse()
        } catch (e: Exception) {
          e.logError("incrementListenCount()")
        }
      }
    }
  }
}
