package audio.funkwhale.ffa.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.databinding.ActivityLicencesBinding
import audio.funkwhale.ffa.databinding.RowLicenceBinding
import audio.funkwhale.ffa.utils.enableEdgeToEdge

class LicencesActivity : AppCompatActivity() {

  private lateinit var binding: ActivityLicencesBinding

  data class Licence(val name: String, val licence: String, val url: String)

  interface OnLicenceClickListener {
    fun onClick(url: String)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    binding = ActivityLicencesBinding.inflate(layoutInflater)
    setContentView(binding.root)

    LicencesAdapter(OnLicenceClick()).also {
      binding.licences.layoutManager = LinearLayoutManager(this)
      binding.licences.adapter = it
    }
  }

  private inner class LicencesAdapter(val listener: OnLicenceClickListener) :
    RecyclerView.Adapter<LicencesAdapter.ViewHolder>() {

    val licences = listOf(
      Licence(
        "ExoPlayer",
        "Apache License 2.0",
        "https://github.com/google/ExoPlayer/blob/release-v2/LICENSE"
      ),
      Licence(
        "ExoPlayer-Extensions",
        "Apache License 2.0",
        "https://github.com/PaulWoitaschek/ExoPlayer-Extensions/blob/master/LICENSE"
      ),
      Licence(
        "Fuel",
        "MIT License",
        "https://github.com/kittinunf/fuel/blob/master/LICENSE.md"
      ),
      Licence(
        "Gson",
        "Apache License 2.0",
        "https://github.com/google/gson/blob/master/LICENSE"
      ),
      Licence(
        "Picasso",
        "Apache License 2.0",
        "https://github.com/square/picasso/blob/master/LICENSE.txt"
      ),
      Licence(
        "Picasso Transformations",
        "Apache License 2.0",
        "https://github.com/wasabeef/picasso-transformations/blob/master/LICENSE"
      ),
      Licence(
        "PowerPreference",
        "Apache License 2.0",
        "https://github.com/AliAsadi/PowerPreference/blob/master/LICENSE"
      )
    )

    override fun getItemCount() = licences.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val binding = RowLicenceBinding.inflate(layoutInflater)
      return ViewHolder(binding).also {
        binding.root.setOnClickListener(it)
      }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val item = licences[position]

      holder.name.text = item.name
      holder.licence.text = item.licence
    }

    inner class ViewHolder(binding: RowLicenceBinding) :
      RecyclerView.ViewHolder(binding.root),
      View.OnClickListener {
      val name = binding.name
      val licence = binding.licence

      override fun onClick(view: View?) {
        listener.onClick(licences[layoutPosition].url)
      }
    }
  }

  inner class OnLicenceClick : OnLicenceClickListener {
    override fun onClick(url: String) {
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        startActivity(this)
      }
    }
  }
}
