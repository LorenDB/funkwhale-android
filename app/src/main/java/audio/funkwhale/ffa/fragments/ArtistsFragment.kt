package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.adapters.ArtistsAdapter
import audio.funkwhale.ffa.databinding.FragmentArtistsBinding
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.repositories.ArtistsRepository

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

  inner class OnArtistClickListener : ArtistsAdapter.OnArtistClickListener {
    override fun onClick(holder: View?, artist: Artist) {
      findNavController().navigate(BrowseFragmentDirections.browseToAlbums(artist))
    }
  }
}
