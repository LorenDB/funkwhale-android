package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import androidx.transition.Slide
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.activities.MainActivity
import audio.funkwhale.ffa.adapters.AlbumsGridAdapter
import audio.funkwhale.ffa.databinding.FragmentAlbumsGridBinding
import audio.funkwhale.ffa.repositories.AlbumsRepository
import audio.funkwhale.ffa.model.Album
import audio.funkwhale.ffa.utils.AppContext

class AlbumsGridFragment : FFAFragment<Album, AlbumsGridAdapter>() {

  private var _binding: FragmentAlbumsGridBinding? = null
  private val binding get() = _binding!!

  override val recycler: RecyclerView get() = binding.albums
  override val layoutManager get() = GridLayoutManager(context, 3)
  override val alwaysRefresh = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    repository = AlbumsRepository(context)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    parent: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentAlbumsGridBinding.inflate(inflater)
    adapter = AlbumsGridAdapter(inflater, OnAlbumClickListener())
    swiper = binding.swiper
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  inner class OnAlbumClickListener : AlbumsGridAdapter.OnAlbumClickListener {
    override fun onClick(view: View?, album: Album) {
      (context as? MainActivity)?.let { activity ->
        exitTransition = Fade().apply {
          duration = AppContext.TRANSITION_DURATION
          interpolator = AccelerateDecelerateInterpolator()

          view?.let {
            addTarget(it)
          }
        }

        val fragment = TracksFragment.new(album).apply {
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
