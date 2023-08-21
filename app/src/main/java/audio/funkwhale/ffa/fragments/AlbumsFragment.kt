package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.adapters.AlbumsAdapter
import audio.funkwhale.ffa.databinding.FragmentAlbumsBinding
import audio.funkwhale.ffa.model.Album
import audio.funkwhale.ffa.repositories.AlbumsRepository
import audio.funkwhale.ffa.repositories.ArtistTracksRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.utils.Command
import audio.funkwhale.ffa.utils.CommandBus
import audio.funkwhale.ffa.utils.CoverArt
import audio.funkwhale.ffa.utils.maybeNormalizeUrl
import com.preference.PowerPreference
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

  private val args by navArgs<AlbumsFragmentArgs>()
  private val artistArt: String get() = when {
    !args.cover.isNullOrBlank() -> args.cover!!
    else -> args.artist.cover() ?: ""
  }

  private var _binding: FragmentAlbumsBinding? = null
  private val binding get() = _binding!!

  private lateinit var artistTracksRepository: ArtistTracksRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    adapter = AlbumsAdapter(layoutInflater, context, OnAlbumClickListener())
    repository = AlbumsRepository(context, args.artist.id)
    artistTracksRepository = ArtistTracksRepository(context, args.artist.id)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentAlbumsBinding.inflate(inflater)
    swiper = binding.swiper

    when (PowerPreference.getDefaultFile().getString("play_order")) {
      "in_order" -> binding.play.text = getString(R.string.playback_play)
      else -> binding.play.text = getString(R.string.playback_shuffle)
    }

    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.cover.let { cover ->
      CoverArt.requestCreator(maybeNormalizeUrl(artistArt))
        .noFade()
        .fit()
        .centerCrop()
        .transform(RoundedCornersTransformation(16, 0))
        .into(cover)
    }

    binding.artist.text = args.artist.name
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

    when (PowerPreference.getDefaultFile().getString("play_order")) {
      "in_order" -> binding.play.text = getString(R.string.playback_play)
      else -> binding.play.text = getString(R.string.playback_shuffle)
    }

    binding.play.setOnClickListener {
      val loader = CircularProgressDrawable(requireContext()).apply {
        setColorSchemeColors(ContextCompat.getColor(requireContext(), android.R.color.white))
        strokeWidth = 4f
      }

      loader.start()

      binding.play.icon = loader
      binding.play.isClickable = false

      lifecycleScope.launch(IO) {
        val tracks = artistTracksRepository.fetch(Repository.Origin.Network.origin)
          .map { it.data }
          .toList()
          .flatten()

        when (PowerPreference.getDefaultFile().getString("play_order")) {
          "in_order" -> CommandBus.send(Command.ReplaceQueue(tracks))
          else -> CommandBus.send(Command.ReplaceQueue(tracks.shuffled()))
        }

        withContext(Main) {
          binding.play.icon =
            AppCompatResources.getDrawable(binding.root.context, R.drawable.play)
          binding.play.isClickable = true
        }
      }
    }
  }

  inner class OnAlbumClickListener : AlbumsAdapter.OnAlbumClickListener {
    override fun onClick(view: View?, album: Album) {
      findNavController().navigate(AlbumsFragmentDirections.albumsToTracks(album))
    }
  }
}
