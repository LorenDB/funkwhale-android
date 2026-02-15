package audio.funkwhale.ffa.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.ActivityLoginBinding
import audio.funkwhale.ffa.utils.AppContext
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.Userinfo
import audio.funkwhale.ffa.utils.enableEdgeToEdge
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObjectResponseResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.github.kittinunf.result.Result
import com.preference.PowerPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

data class FwCredentials(val token: String, val non_field_errors: List<String>?)

class LoginActivity : AppCompatActivity() {

  private lateinit var binding: ActivityLoginBinding
  private val oAuth: OAuth by inject(OAuth::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    binding = ActivityLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)
    limitContainerWidth()
  }

  private var resultLauncher =
    registerForActivityResult(StartActivityForResult()) { result ->
      result.data?.let { data ->
        lifecycleScope.launch {
          try {
            showProgress(getString(R.string.login_status_exchanging_token))

            oAuth.exchange(this@LoginActivity, data)

            PowerPreference
              .getFileByName(AppContext.PREFS_CREDENTIALS)
              .setBoolean("anonymous", false)

            showProgress(getString(R.string.login_status_fetching_user))

            val user = Userinfo.get(this@LoginActivity, oAuth)
            if (user != null) {
              startActivity(Intent(this@LoginActivity, MainActivity::class.java))
              finish()
            } else {
              showError(getString(R.string.login_error_userinfo))
            }
          } catch (e: Exception) {
            showError(
              e.message ?: getString(R.string.login_error_hostname)
            )
          }
        }
      } ?: run {
        showError(getString(R.string.login_error_authorization_cancelled))
      }
    }

  override fun onResume() {
    super.onResume()
    with(binding) {
      val preferences = getPreferences(Context.MODE_PRIVATE)
      val hn = preferences?.getString("hostname", "")
      if (hn != null && hn.isNotEmpty()) {
        hostname.text = Editable.Factory.getInstance().newEditable(hn)
      }
      cleartext.isChecked = preferences?.getBoolean("cleartext", false) ?: false
      anonymous.isChecked = preferences?.getBoolean("anonymous", false) ?: false
      login.setOnClickListener {
        var hostname = hostname.text.toString().trim().trim('/')

        try {
          validateHostname(hostname, cleartext.isChecked)?.let {
            hostnameField.error = it
            return@setOnClickListener
          }

          val uri = Uri.parse(hostname)
          if (uri.scheme == null) {
            hostname = when (cleartext.isChecked) {
              true -> "http://$hostname"
              false -> "https://$hostname"
            }
          }

          hostnameField.error = ""

          when (anonymous.isChecked) {
            false -> authedLogin(hostname)
            true -> anonymousLogin(hostname)
          }
        } catch (e: Exception) {
          val message =
            if (e.message?.isEmpty() == true) getString(R.string.login_error_hostname)
            else e.message

          hostnameField.error = message
        }

        if (hostnameField.error.isNullOrEmpty()) {
          val prefs = getPreferences(Context.MODE_PRIVATE)
          prefs?.edit()?.putString("hostname", hostname)?.commit()
          prefs?.edit()?.putBoolean("cleartext", cleartext.isChecked)?.commit()
          prefs?.edit()?.putBoolean("anonymous", anonymous.isChecked)?.commit()
        }
      }
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    limitContainerWidth()
  }

  private fun showProgress(status: String) {
    binding.loginProgress.visibility = View.VISIBLE
    binding.loginStatusText.visibility = View.VISIBLE
    binding.loginStatusText.text = status
    binding.login.isEnabled = false
    binding.hostname.isEnabled = false
    binding.cleartext.isEnabled = false
    binding.anonymous.isEnabled = false
    binding.hostnameField.error = ""
  }

  private fun hideProgress() {
    binding.loginProgress.visibility = View.GONE
    binding.loginStatusText.visibility = View.GONE
    binding.login.isEnabled = true
    binding.hostname.isEnabled = true
    binding.cleartext.isEnabled = true
    binding.anonymous.isEnabled = true
  }

  private fun showError(message: String) {
    hideProgress()
    binding.hostnameField.error = message
  }

  private fun validateHostname(hostname: String, cleartext: Boolean): String? {
    if (hostname.isEmpty()) {
      return getString(R.string.login_error_hostname)
    }
    if (!cleartext && hostname.startsWith("http://")) {
      return getString(R.string.login_error_hostname_https)
    }
    return null
  }

  private fun authedLogin(hostname: String) {
    lifecycleScope.launch {
      try {
        showProgress(getString(R.string.login_status_registering))

        oAuth.init(hostname)

        val result = withContext(Dispatchers.IO) {
          oAuth.register()
        }

        if (!result.success) {
          showError(mapResultToError(result))
          return@launch
        }

        PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS)
          .setString("hostname", hostname)

        showProgress(getString(R.string.login_status_waiting_for_browser))

        resultLauncher.launch(oAuth.authorizeIntent(this@LoginActivity))
      } catch (e: Exception) {
        showError(
          e.message ?: getString(R.string.login_error_hostname)
        )
      }
    }
  }

  private fun anonymousLogin(hostname: String) {
    lifecycleScope.launch {
      try {
        showProgress(getString(R.string.login_status_connecting))

        val uri = "$hostname/api/v1/tracks/"
        val (_, _, result) = withContext(Dispatchers.IO) {
          Fuel.get(uri)
            .awaitObjectResponseResult(gsonDeserializerOf(FwCredentials::class.java))
        }

        when (result) {
          is Result.Success -> {
            PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).apply {
              setString("hostname", hostname)
              setBoolean("anonymous", true)
            }

            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
          }

          is Result.Failure -> {
            val status = result.error.response.statusCode
            val message = result.error.response.responseMessage
            showError(
              when {
                status == 404 -> getString(R.string.login_error_funkwhale_not_found)
                else -> getString(R.string.login_error, message)
              }
            )
          }
        }
      } catch (e: Exception) {
        showError(
          e.message ?: getString(R.string.login_error_hostname)
        )
      }
    }
  }

  private fun mapResultToError(result: audio.funkwhale.ffa.utils.FuelResult) = when {
    result.httpStatus == 404 ->
      getString(R.string.login_error_funkwhale_not_found)

    !result.success ->
      getString(R.string.login_error, result.message)

    else -> ""
  }

  private fun limitContainerWidth() {
    binding.container.doOnLayout {
      if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && binding.container.width >= 1440) {
        binding.container.layoutParams.width = 1440
      } else {
        binding.container.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
      }

      binding.container.requestLayout()
    }
  }
}
