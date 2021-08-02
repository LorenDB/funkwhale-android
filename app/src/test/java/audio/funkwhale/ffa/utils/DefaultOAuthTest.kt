package audio.funkwhale.ffa.utils

import android.content.Context
import com.github.kittinunf.fuel.core.Client
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Request
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.preference.PowerPreference
import com.preference.Preference
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import org.junit.Before
import org.junit.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue


class DefaultOAuthTest {

  @InjectMockKs
  private lateinit var oAuth: DefaultOAuth

  @MockK
  private lateinit var authServiceFactory: AuthorizationServiceFactory

  @MockK
  private lateinit var authService: AuthorizationService

  @MockK
  private lateinit var mockPreference: Preference

  @MockK
  private lateinit var context: Context

  @Before
  fun setup() {
    MockKAnnotations.init(this, relaxUnitFun = true)
  }

  @Test
  fun `tryState() should return null if saved state is missing`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns null
    expectThat(oAuth.tryState()).isNull()
  }

  @Test
  fun `tryState() should return null if saved state is empty`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns ""
    expectThat(oAuth.tryState()).isNull()
  }

  @Test
  fun `tryState() should return deserialized object if saved state is present`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns "{}"
    expectThat(oAuth.tryState()).isNotNull()
  }

  @Test
  fun `state() should return deserialized object if saved state is present`() {

    mockkStatic(PowerPreference::class)
    mockkStatic(AuthState::class)

    val authState = AuthState()
    every { AuthState.jsonDeserialize(any<String>()) } returns authState

    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns "{}"

    val result = oAuth.state()
    expectThat(result).isEqualTo(authState)
  }

  @Test
  fun `state() should throw error if saved state is missing`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns null

    expectThrows<IllegalStateException> { oAuth.state() }
  }

  @Test
  fun `isAuthorized() should return false if no state exists`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns null

    expectThat(oAuth.isAuthorized(context)).isFalse()
  }

  @Test
  fun `isAuthorized() should return false if existing state is not authorized and token is not refreshed`() {
    mockkStatic(PowerPreference::class)
    mockkStatic(AuthState::class)

    val authState = mockk<AuthState>()
    every { AuthState.jsonDeserialize(any<String>()) } returns authState
    every { authState.isAuthorized } returns false
    every { authState.needsTokenRefresh } returns false

    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns "{}"

    expectThat(oAuth.isAuthorized(context)).isFalse()
  }

  @Test
  fun `isAuthorized() should return true if existing state is authorized`() {
    mockkStatic(PowerPreference::class)
    mockkStatic(AuthState::class)

    val authState = mockk<AuthState>()
    every { AuthState.jsonDeserialize(any<String>()) } returns authState
    every { authState.isAuthorized } returns true

    val mockPref = mockk<Preference>()
    every { PowerPreference.getFileByName(any()) } returns mockPref
    every { mockPref.getString(any()) } returns "{}"

    expectThat(oAuth.isAuthorized(context)).isTrue()
  }

  @Test
  fun `tryRefreshAccessToken() should perform token refresh request if accessToken needs refresh and refreshToken exists`() {
    mockkStatic(PowerPreference::class)
    mockkStatic(AuthState::class)

    val authState = mockk<AuthState>()
    every { AuthState.jsonDeserialize(any<String>()) } returns authState
    every { authState.isAuthorized } returns false
    every { authState.needsTokenRefresh } returns true
    every { authState.refreshToken } returns "refreshToken"
    every { authState.createTokenRefreshRequest() } returns mockk()
    every { authState.clientSecret } returns "clientSecret"
    every { authServiceFactory.create(any()) } returns authService
    every { authService.performTokenRequest(any(), any<ClientSecretPost>(), any()) } returns mockk()

    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns "{}"

    oAuth.tryRefreshAccessToken(context)

    verify { authService.performTokenRequest(any(), any(), any()) }
  }

  @Test
  fun `tryRefreshAccessToken() should not perform token refresh request if accessToken doesn't need refresh`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns "{}"

    mockkStatic(AuthState::class)

    val authState = mockk<AuthState>()
    every { AuthState.jsonDeserialize(any<String>()) } returns authState
    every { authState.isAuthorized } returns false
    every { authState.needsTokenRefresh } returns false

    oAuth.tryRefreshAccessToken(context)

    verify(exactly = 0) { authService.performTokenRequest(any(), any(), any()) }
  }

  @Test
  fun `init() should setup correct endpoints`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.setString(any(), any()) } returns true

    val result = oAuth.init("hostname")

    expectThat(result.authorizationServiceConfiguration?.authorizationEndpoint.toString())
      .isEqualTo("hostname/authorize")
    expectThat(result.authorizationServiceConfiguration?.tokenEndpoint.toString())
      .isEqualTo("hostname/api/v1/oauth/token/")
    expectThat(result.authorizationServiceConfiguration?.registrationEndpoint.toString())
      .isEqualTo("hostname/api/v1/oauth/apps/")
  }

  @Test
  fun `register() should not initiate http request if configuration is missing`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns "{}"

    mockkStatic(AuthState::class)
    val authState = mockk<AuthState>()
    every { AuthState.jsonDeserialize(any<String>()) } returns authState
    every { authState.authorizationServiceConfiguration } returns null

    val mockkClient = mockk<Client>()
    FuelManager.instance.client = mockkClient

    oAuth.register {}

    verify(exactly = 0) { mockkClient.executeRequest(any()) }
  }

  @Test
  fun `register() should initiate correct HTTP request to registration endpoint`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName(any()) } returns mockPreference
    every { mockPreference.getString(any()) } returns "{}"
    every { mockPreference.setString(any(), any()) } returns true

    mockkStatic(AuthState::class)
    val authState = mockk<AuthState>()
    every { AuthState.jsonDeserialize(any<String>()) } returns authState
    val mockConfig = mockk<AuthorizationServiceConfiguration>()
    every { authState.authorizationServiceConfiguration } returns mockConfig

    val mockkClient = mockk<Client>()

    FuelManager.instance.client = mockkClient

    val state = oAuth.init("https://example.com")
    oAuth.register(state) { }

    val requestSlot = slot<com.github.kittinunf.fuel.core.Request>()

    coVerify { mockkClient.awaitRequest(capture(requestSlot)) }

    val capturedRequest = requestSlot.captured
    expectThat(capturedRequest.url.toString())
      .isEqualTo("https://example.com/api/v1/oauth/apps/")

    expectThat(deserializeJson<Map<String, String>>(capturedRequest)).isEqualTo(
      mapOf(
        "name" to "Funkwhale for Android (null)",
        "redirect_uris" to "urn:/audio.funkwhale.funkwhale-android/oauth/callback",
        "scopes" to "read write"
      )
    )
  }

  private fun <T> deserializeJson(
    capturedRequest: Request
  ): T {
    return Gson().fromJson(
      capturedRequest.body.asString("application/json"),
      object : TypeToken<T>() {}.type
    )
  }

}

