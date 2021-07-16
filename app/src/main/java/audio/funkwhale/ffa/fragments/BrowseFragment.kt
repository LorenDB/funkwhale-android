package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import audio.funkwhale.ffa.adapters.BrowseTabsAdapter
import audio.funkwhale.ffa.databinding.FragmentBrowseBinding

class BrowseFragment : Fragment() {

  private var _binding: FragmentBrowseBinding? = null
  private val binding get() = _binding!!

  private var adapter: BrowseTabsAdapter? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    adapter = BrowseTabsAdapter(this, childFragmentManager)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentBrowseBinding.inflate(inflater)
    return binding.root.apply {
      binding.tabs.setupWithViewPager(binding.pager)
      binding.tabs.getTabAt(0)?.select()

      binding.pager.adapter = adapter
      binding.pager.offscreenPageLimit = 3
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  fun selectTabAt(position: Int) {
    binding.tabs.getTabAt(position)?.select()
  }
}
