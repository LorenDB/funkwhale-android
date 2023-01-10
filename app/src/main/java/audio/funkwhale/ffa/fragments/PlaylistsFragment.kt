package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.adapters.PlaylistsAdapter
import audio.funkwhale.ffa.databinding.FragmentPlaylistsBinding
import audio.funkwhale.ffa.model.Playlist
import audio.funkwhale.ffa.repositories.PlaylistsRepository

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
      findNavController().navigate(BrowseFragmentDirections.browseToPlaylistTracks(playlist))
    }
  }
}
