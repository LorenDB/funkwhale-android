package audio.funkwhale.ffa.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.fragments.AlbumsGridFragment
import audio.funkwhale.ffa.fragments.ArtistsFragment
import audio.funkwhale.ffa.fragments.FavoritesFragment
import audio.funkwhale.ffa.fragments.PlaylistsFragment
import audio.funkwhale.ffa.fragments.RadiosFragment

class BrowseTabsAdapter(val context: Fragment) : FragmentStateAdapter(context) {
  override fun getItemCount() = 5

  override fun createFragment(position: Int): Fragment = when (position) {
    0 -> ArtistsFragment()
    1 -> AlbumsGridFragment()
    2 -> PlaylistsFragment()
    3 -> RadiosFragment()
    4 -> FavoritesFragment()
    else -> ArtistsFragment()
  }

  fun tabText(position: Int): String {
    return when (position) {
      0 -> context.getString(R.string.artists)
      1 -> context.getString(R.string.albums)
      2 -> context.getString(R.string.playlists)
      3 -> context.getString(R.string.radios)
      4 -> context.getString(R.string.favorites)
      else -> ""
    }
  }
}
