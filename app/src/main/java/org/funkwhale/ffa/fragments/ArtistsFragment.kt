package org.funkwhale.ffa.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import androidx.transition.Slide
import org.funkwhale.ffa.R
import org.funkwhale.ffa.activities.MainActivity
import org.funkwhale.ffa.adapters.ArtistsAdapter
import org.funkwhale.ffa.repositories.ArtistsRepository
import org.funkwhale.ffa.utils.AppContext
import org.funkwhale.ffa.utils.Artist
import org.funkwhale.ffa.utils.onViewPager
import kotlinx.android.synthetic.main.fragment_artists.*

class ArtistsFragment : OtterFragment<Artist, ArtistsAdapter>() {
  override val viewRes = R.layout.fragment_artists
  override val recycler: RecyclerView get() = artists
  override val alwaysRefresh = false

  companion object {
    fun openAlbums(context: Context?, artist: Artist, fragment: Fragment? = null, art: String? = null) {
      (context as? MainActivity)?.let {
        fragment?.let { fragment ->
          fragment.onViewPager {
            exitTransition = Fade().apply {
              duration = AppContext.TRANSITION_DURATION
              interpolator = AccelerateDecelerateInterpolator()

              view?.let {
                addTarget(it)
              }
            }
          }
        }
      }

      (context as? AppCompatActivity)?.let { activity ->
        val nextFragment = AlbumsFragment.new(artist, art).apply {
          enterTransition = Slide().apply {
            duration = AppContext.TRANSITION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
          }
        }

        activity.supportFragmentManager
          .beginTransaction()
          .replace(R.id.container, nextFragment)
          .addToBackStack(null)
          .commit()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    adapter = ArtistsAdapter(context, OnArtistClickListener())
    repository = ArtistsRepository(context)
  }

  inner class OnArtistClickListener : ArtistsAdapter.OnArtistClickListener {
    override fun onClick(holder: View?, artist: Artist) {
      openAlbums(context, artist, fragment = this@ArtistsFragment)
    }
  }
}
