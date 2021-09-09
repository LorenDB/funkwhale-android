package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import androidx.transition.Slide
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.activities.MainActivity
import audio.funkwhale.ffa.adapters.PlaylistsAdapter
import audio.funkwhale.ffa.databinding.FragmentPlaylistsBinding
import audio.funkwhale.ffa.model.Playlist
import audio.funkwhale.ffa.repositories.PlaylistsRepository
import audio.funkwhale.ffa.utils.AppContext

class PlaylistsFragment : FFAFragment<Playlist, PlaylistsAdapter>() {

  override val recycler: RecyclerView get() = binding.playlists
  override val alwaysRefresh = false

  private var _binding: FragmentPlaylistsBinding? = null
  private val binding get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    adapter = PlaylistsAdapter(layoutInflater, context, OnPlaylistClickListener())
    repository = PlaylistsRepository(context)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentPlaylistsBinding.inflate(layoutInflater)
    swiper = binding.swiper
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
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
