package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import audio.funkwhale.ffa.adapters.BrowseTabsAdapter
import audio.funkwhale.ffa.databinding.FragmentBrowseBinding
import com.google.android.material.tabs.TabLayoutMediator

class BrowseFragment : Fragment() {

  private var _binding: FragmentBrowseBinding? = null
  private val binding get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentBrowseBinding.inflate(inflater)
    return binding.root.apply {
      binding.tabs.getTabAt(0)?.select()

      val adapter = BrowseTabsAdapter(this@BrowseFragment)
      binding.pager.adapter = adapter
      binding.pager.offscreenPageLimit = 3
      TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
        tab.text = adapter.tabText(position)
      }.attach()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
