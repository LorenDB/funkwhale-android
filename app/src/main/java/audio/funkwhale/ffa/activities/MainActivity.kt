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
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.ActivityMainBinding
import audio.funkwhale.ffa.fragments.AddToPlaylistDialog
import audio.funkwhale.ffa.fragments.BrowseFragmentDirections
import audio.funkwhale.ffa.fragments.LandscapeQueueFragment
import audio.funkwhale.ffa.fragments.NowPlayingFragment
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
import audio.funkwhale.ffa.utils.CoverArt
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.gson.Gson
import com.preference.PowerPreference
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

  private val favoritedRepository = FavoritedRepository(this)
  private var menu: Menu? = null

  private lateinit var binding: ActivityMainBinding
  private val oAuth: OAuth by inject(OAuth::class.java)

  private val navigation: NavController by lazy {
    val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    navHost.navController
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppContext.init(this)
    binding = ActivityMainBinding.inflate(layoutInflater)

    setContentView(binding.root)

    setSupportActionBar(binding.appbar)

    onBackPressedDispatcher.addCallback(this) {
      if (binding.nowPlayingBottomSheet.isOpen) {
        binding.nowPlayingBottomSheet.close()
      } else {
        navigation.navigateUp()
      }
    }

    when (intent.action) {
      MediaControlsManager.NOTIFICATION_ACTION_OPEN_QUEUE.toString() -> launchDialog(QueueFragment())
    }

    watchEventBus()
  }

  override fun onResume() {
    super.onResume()

    binding.nowPlaying.getFragment<NowPlayingFragment>().apply {
      favoritedRepository.update(requireContext(), lifecycleScope)

      startService(Intent(requireContext(), PlayerService::class.java))
      DownloadService.start(requireContext(), PinService::class.java)

      CommandBus.send(Command.RefreshService)

      lifecycleScope.launch(IO) {
        Userinfo.get(this@MainActivity, oAuth)
      }
    }
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
        binding.nowPlayingBottomSheet.close()
        navigation.popBackStack(R.id.browseFragment, false)
      }

      R.id.nav_queue -> launchDialog(QueueFragment())
      R.id.nav_search -> navigation.navigate(BrowseFragmentDirections.browseToSearch())
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

  private fun launchDialog(fragment: DialogFragment) =
    fragment.show(supportFragmentManager.beginTransaction(), "")

  @SuppressLint("NewApi")
  private fun watchEventBus() {
    lifecycleScope.launch(Main) {
      EventBus.get().collect { event ->
        when(event) {
          is Event.LogOut -> logout()
          is Event.PlaybackError -> toast(event.message)
          is Event.PlaybackStopped -> binding.nowPlayingBottomSheet.hide()
          is Event.TrackFinished -> incrementListenCount(event.track)
          is Event.QueueChanged -> {
            if(binding.nowPlayingBottomSheet.isHidden) binding.nowPlayingBottomSheet.show()
            findViewById<View>(R.id.nav_queue)?.let { view ->
              ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).let {
                it.duration = 500
                it.interpolator = AccelerateDecelerateInterpolator()
                it.start()
              }
            }
          }
          else -> {}
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

        (binding.navHostFragment.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
          it.bottomMargin = it.bottomMargin * 2
        }

        binding.landscapeQueue?.let { landscape_queue ->
          (landscape_queue.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.bottomMargin = it.bottomMargin * 2
          }
        }
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

  private fun logout() {
    FFA.get().deleteAllData(this@MainActivity)
    startActivity(
      Intent(this@MainActivity, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NO_HISTORY
      }
    )

    finish()
  }
}
