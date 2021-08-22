package audio.funkwhale.ffa.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import androidx.transition.Slide
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.activities.MainActivity
import audio.funkwhale.ffa.adapters.ArtistsAdapter
import audio.funkwhale.ffa.databinding.FragmentArtistsBinding
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.repositories.ArtistsRepository
import audio.funkwhale.ffa.utils.AppContext
import audio.funkwhale.ffa.utils.onViewPager

class ArtistsFragment : FFAFragment<Artist, ArtistsAdapter>() {

  private var _binding: FragmentArtistsBinding? = null
  private val binding get() = _binding!!

  override val recycler: RecyclerView get() = binding.artists
  override val alwaysRefresh = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    repository = ArtistsRepository(context)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentArtistsBinding.inflate(inflater)
    swiper = binding.swiper
    adapter = ArtistsAdapter(inflater, context, OnArtistClickListener())
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {

    fun openAlbums(
      context: Context?,
      artist: Artist,
      fragment: Fragment? = null,
      art: String? = null
    ) {
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

  inner class OnArtistClickListener : ArtistsAdapter.OnArtistClickListener {
    override fun onClick(holder: View?, artist: Artist) {
      openAlbums(context, artist, fragment = this@ArtistsFragment)
    }
  }
}
