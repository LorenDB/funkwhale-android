package audio.funkwhale.ffa.activities

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.ActivityLoginBinding
import audio.funkwhale.ffa.fragments.LoginDialog
import audio.funkwhale.ffa.utils.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObjectResponseResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.github.kittinunf.result.Result
import com.preference.PowerPreference
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject

data class FwCredentials(val token: String, val non_field_errors: List<String>?)

class LoginActivity : AppCompatActivity() {

  private lateinit var binding: ActivityLoginBinding
  private val oAuth: OAuth by inject(OAuth::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityLoginBinding.inflate(layoutInflater)
    setContentView(binding.root)
    limitContainerWidth()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    data?.let {
      when (requestCode) {
        0 -> {
          oAuth.exchange(this, data,
            {
              PowerPreference
                .getFileByName(AppContext.PREFS_CREDENTIALS)
                .setBoolean("anonymous", false)

              lifecycleScope.launch(Main) {
                Userinfo.get(this@LoginActivity, oAuth)?.let {
                  startActivity(Intent(this@LoginActivity, MainActivity::class.java))

                  return@launch finish()
                }
                throw Exception(getString(R.string.login_error_userinfo))
              }
            },
            { "error".log() }
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    with(binding) {
      login.setOnClickListener {
        var hostname = hostname.text.toString().trim()

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

          val fuelResult = when (anonymous.isChecked) {
            false -> authedLogin(hostname)
            true -> anonymousLogin(hostname)
          }

          hostnameField.error = mapFuelResultToError(fuelResult)
        } catch (e: Exception) {
          val message =
            if (e.message?.isEmpty() == true) getString(R.string.login_error_hostname)
            else e.message

          hostnameField.error = message
        }
      }
    }
  }

  private fun mapFuelResultToError(fuelResult: FuelResult) = when {
    fuelResult.httpStatus == 404 ->
      getString(R.string.login_error_funkwhale_not_found)

    !fuelResult.success ->
      getString(R.string.login_error, fuelResult.message)

    else -> ""
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    limitContainerWidth()
  }

  private fun validateHostname(hostname: String, cleartext: Boolean): String? {
    if (hostname.isEmpty()) {
      return getString(R.string.login_error_hostname)
    }
    if (!cleartext && hostname.startsWith("http")) {
      return getString(R.string.login_error_hostname_https)
    }
    return null
  }

  private fun authedLogin(hostname: String): FuelResult {
    oAuth.init(hostname)
    return oAuth.register {
      PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).setString("hostname", hostname)
      oAuth.authorize(this)
    }
  }

  private fun anonymousLogin(hostname: String): FuelResult {
    val dialog = LoginDialog().apply {
      show(supportFragmentManager, "LoginDialog")
    }

    val uri = "$hostname/api/v1/tracks/"
    val (_, _, result) = runBlocking {
      Fuel.get(uri).awaitObjectResponseResult(gsonDeserializerOf(FwCredentials::class.java))
    }

    when (result) {
      is Result.Success -> {
        PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).apply {
          setString("hostname", hostname)
          setBoolean("anonymous", true)
        }

        dialog.dismiss()
        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        finish()
        return FuelResult.ok()
      }

      is Result.Failure -> {
        dialog.dismiss()
        return FuelResult.from(result)
      }
    }
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
