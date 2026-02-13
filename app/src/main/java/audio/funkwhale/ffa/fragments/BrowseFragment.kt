package audio.funkwhale.ffa.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import audio.funkwhale.ffa.adapters.BrowseTabsAdapter
import audio.funkwhale.ffa.databinding.FragmentBrowseBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.preference.PowerPreference

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
      val adapter = BrowseTabsAdapter(this@BrowseFragment)
      binding.pager.adapter = adapter
      binding.pager.offscreenPageLimit = 3
      TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
        tab.text = adapter.tabText(position)
      }.attach()

      // Restore the last selected tab position
      val lastTabPosition = PowerPreference.getDefaultFile().getInt("last_tab_position", 0)
      if (lastTabPosition in 0 until binding.tabs.tabCount) {
        binding.tabs.getTabAt(lastTabPosition)?.select()
      }

      // Save tab position when user changes tabs
      binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
          tab?.position?.let { position ->
            PowerPreference.getDefaultFile().setInt("last_tab_position", position)
          }
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {}
        override fun onTabReselected(tab: TabLayout.Tab?) {}
      })
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
