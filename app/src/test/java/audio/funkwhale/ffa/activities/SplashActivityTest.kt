package audio.funkwhale.ffa.activities

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.KoinTestApp
import audio.funkwhale.ffa.utils.OAuth
import com.preference.PowerPreference
import com.preference.Preference
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@RunWith(RobolectricTestRunner::class)
@Config(application = KoinTestApp::class, sdk = [30])
class SplashActivityTest {

  private val app: KoinTestApp = ApplicationProvider.getApplicationContext()

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun `unauthorized and nonAnonymous request should redirect to LoginActivity`() {

    mockkStatic(PowerPreference::class)
    val preference = mockk<Preference> {
      every { getBoolean("anonymous", false) } returns false
      every { clear() } returns true
    }
    every { PowerPreference.getFileByName("credentials") } returns preference

    val modules = module {
      single<OAuth> {
        mockk { every { isAuthorized(any()) } returns false }
      }
    }

    app.loadModules(modules) {
      val scenario = ActivityScenario.launch(SplashActivity::class.java)
      scenario.onActivity { activity ->
        val expectedIntent = Intent(activity, LoginActivity::class.java)
        val appContext = Shadows.shadowOf(ApplicationProvider.getApplicationContext<FFA>())
        expectThat(appContext.nextStartedActivity.component).isEqualTo(expectedIntent.component)
        verify { preference.clear() }
      }
    }
  }

  @Test
  fun `authorized request should redirect to MainActivity`() {
    val modules = module {
      single<OAuth> {
        mockk { every { isAuthorized(any()) } returns true }
      }
    }
    app.loadModules(modules) {
      val scenario = ActivityScenario.launch(SplashActivity::class.java)
      scenario.onActivity { activity ->
        val expectedIntent = Intent(activity, MainActivity::class.java)
        val appContext = Shadows.shadowOf(ApplicationProvider.getApplicationContext<FFA>())
        expectThat(appContext.nextStartedActivity.component).isEqualTo(expectedIntent.component)
      }
    }
  }

  @Test
  fun `anonymous requests should redirect to MainActivity`() {

    mockkStatic(PowerPreference::class)
    val preference = mockk<Preference>() {
      every { getBoolean("anonymous", false) } returns true
    }
    every { PowerPreference.getFileByName("credentials") } returns preference

    val modules = module {
      single<OAuth> {
        mockk { every { isAuthorized(any()) } returns false }
      }
    }
    app.loadModules(modules) {
      val scenario = ActivityScenario.launch(SplashActivity::class.java)
      scenario.onActivity { activity ->
        val expectedIntent = Intent(activity, MainActivity::class.java)
        val appContext = Shadows.shadowOf(ApplicationProvider.getApplicationContext<FFA>())
        expectThat(appContext.nextStartedActivity.component).isEqualTo(expectedIntent.component)
      }
    }
  }
}
