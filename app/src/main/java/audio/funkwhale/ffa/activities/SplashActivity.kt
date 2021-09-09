package audio.funkwhale.ffa.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.utils.AppContext
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.Settings
import org.koin.java.KoinJavaComponent.inject

class SplashActivity : AppCompatActivity() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    getSharedPreferences(AppContext.PREFS_CREDENTIALS, Context.MODE_PRIVATE)
      .apply {
        when (Settings.isAnonymous() || oAuth.isAuthorized(this@SplashActivity)) {
          true -> Intent(this@SplashActivity, MainActivity::class.java)
            .apply {
              Log.i("SplashActivity", "Authorized, redirecting to MainActivity")
              flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
              startActivity(this)
            }

          false -> Intent(this@SplashActivity, LoginActivity::class.java)
            .apply {
              Log.i("SplashActivity", "Not authorized, redirecting to LoginActivity")
              FFA.get().deleteAllData(this@SplashActivity)
              flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
              startActivity(this)
            }
        }
      }
  }
}
