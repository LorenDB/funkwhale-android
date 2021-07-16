package audio.funkwhale.ffa.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.transition.Fade
import androidx.transition.Slide
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.activities.MainActivity
import audio.funkwhale.ffa.adapters.AlbumsAdapter
import audio.funkwhale.ffa.databinding.FragmentAlbumsBinding
import audio.funkwhale.ffa.repositories.AlbumsRepository
import audio.funkwhale.ffa.repositories.ArtistTracksRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.utils.*
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumsFragment : FFAFragment<Album, AlbumsAdapter>() {

  override val recycler: RecyclerView get() = binding.albums
  override val alwaysRefresh = false

  private var _binding: FragmentAlbumsBinding? = null
  private val binding get() = _binding!!

  private lateinit var artistTracksRepository: ArtistTracksRepository

  private var artistId = 0
  private var artistName = ""
  private var artistArt = ""

  companion object {
    fun new(artist: Artist, _art: String? = null): AlbumsFragment {
      val art = _art ?: if (artist.albums?.isNotEmpty() == true) artist.cover() else ""

      return AlbumsFragment().apply {
        arguments = bundleOf(
          "artistId" to artist.id,
          "artistName" to artist.name,
          "artistArt" to art
        )
      }
    }

    fun openTracks(context: Context?, album: Album?, fragment: Fragment? = null) {
      if (album == null) {
        return
      }

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
        val nextFragment = TracksFragment.new(album).apply {
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

    arguments?.apply {
      artistId = getInt("artistId")
      artistName = getString("artistName") ?: ""
      artistArt = getString("artistArt") ?: ""
    }

    adapter = AlbumsAdapter(layoutInflater, context, OnAlbumClickListener())
    repository = AlbumsRepository(context, artistId)
    artistTracksRepository = ArtistTracksRepository(context, artistId)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentAlbumsBinding.inflate(inflater)
    swiper = binding.swiper
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.cover.let { cover ->
      Picasso.get()
        .maybeLoad(maybeNormalizeUrl(artistArt))
        .noFade()
        .fit()
        .centerCrop()
        .transform(RoundedCornersTransformation(16, 0))
        .into(cover)
    }

    binding.artist.text = artistName

    binding.play.setOnClickListener {
      val loader = CircularProgressDrawable(requireContext()).apply {
        setColorSchemeColors(ContextCompat.getColor(requireContext(), android.R.color.white))
        strokeWidth = 4f
      }

      loader.start()

      binding.play.icon = loader
      binding.play.isClickable = false

      lifecycleScope.launch(IO) {
        artistTracksRepository.fetch(Repository.Origin.Network.origin)
          .map { it.data }
          .toList()
          .flatten()
          .shuffled()
          .also {
            CommandBus.send(Command.ReplaceQueue(it))

            withContext(Main) {
              binding.play.icon =
                AppCompatResources.getDrawable(binding.root.context, R.drawable.play)
              binding.play.isClickable = true
            }
          }
      }
    }
  }

  override fun onResume() {
    super.onResume()

    var coverHeight: Float? = null

    binding.scroller.setOnScrollChangeListener { _: View?, _: Int, scrollY: Int, _: Int, _: Int ->
      if (coverHeight == null) {
        coverHeight = binding.cover.measuredHeight.toFloat()
      }

      binding.cover.translationY = (scrollY / 2).toFloat()

      coverHeight?.let { height ->
        binding.cover.alpha = (height - scrollY.toFloat()) / height
      }
    }
  }

  inner class OnAlbumClickListener : AlbumsAdapter.OnAlbumClickListener {
    override fun onClick(view: View?, album: Album) {
      openTracks(context, album, fragment = this@AlbumsFragment)
    }
  }
}
