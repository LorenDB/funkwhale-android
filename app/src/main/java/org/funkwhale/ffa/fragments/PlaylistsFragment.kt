package org.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import androidx.transition.Slide
import org.funkwhale.ffa.R
import org.funkwhale.ffa.activities.MainActivity
import org.funkwhale.ffa.adapters.PlaylistsAdapter
import org.funkwhale.ffa.repositories.PlaylistsRepository
import org.funkwhale.ffa.utils.AppContext
import org.funkwhale.ffa.utils.Playlist
import kotlinx.android.synthetic.main.fragment_playlists.*

class PlaylistsFragment : OtterFragment<Playlist, PlaylistsAdapter>() {
  override val viewRes = R.layout.fragment_playlists
  override val recycler: RecyclerView get() = playlists
  override val alwaysRefresh = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    adapter = PlaylistsAdapter(context, OnPlaylistClickListener())
    repository = PlaylistsRepository(context)
  }

  inner class OnPlaylistClickListener : PlaylistsAdapter.OnPlaylistClickListener {
    override fun onClick(holder: View?, playlist: Playlist) {
      (context as? MainActivity)?.let { activity ->
        exitTransition = Fade().apply {
          duration = AppContext.TRANSITION_DURATION
          interpolator = AccelerateDecelerateInterpolator()

          view?.let {
            addTarget(it)
          }
        }

        val fragment = PlaylistTracksFragment.new(playlist).apply {
          enterTransition = Slide().apply {
            duration = AppContext.TRANSITION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
          }
        }

        activity.supportFragmentManager
          .beginTransaction()
          .replace(R.id.container, fragment)
          .addToBackStack(null)
          .commit()
      }
    }
  }
}
