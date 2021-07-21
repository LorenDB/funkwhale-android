package audio.funkwhale.ffa.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.utils.AppContext
import audio.funkwhale.ffa.utils.Settings

class SplashActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    getSharedPreferences(AppContext.PREFS_CREDENTIALS, Context.MODE_PRIVATE).apply {
      when (Settings.hasAccessToken() || Settings.isAnonymous()) {
        true -> Intent(this@SplashActivity, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NO_ANIMATION

          startActivity(this)
        }

        false -> Intent(this@SplashActivity, LoginActivity::class.java).apply {
          FFA.get().deleteAllData()

          flags = Intent.FLAG_ACTIVITY_NO_ANIMATION

          startActivity(this)
        }
      }
    }
  }
}
