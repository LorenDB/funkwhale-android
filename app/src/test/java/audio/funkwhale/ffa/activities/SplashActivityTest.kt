package audio.funkwhale.ffa.activities

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import audio.funkwhale.ffa.FFA
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@RunWith(RobolectricTestRunner::class)
class SplashActivityTest {

  @Test
  fun `unauthorized and nonAnonymous request should redirect to LoginActivity`() {
    val scenario = ActivityScenario.launch(SplashActivity::class.java)
    scenario.onActivity { activity ->
      val expectedIntent = Intent(activity, LoginActivity::class.java)
      val appContext = Shadows.shadowOf(ApplicationProvider.getApplicationContext<FFA>())
      expectThat(appContext.nextStartedActivity.component).isEqualTo(expectedIntent.component)
    }

  }
}
