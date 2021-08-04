package audio.funkwhale.ffa.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.coroutines.awaitObjectResponseResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.github.kittinunf.fuel.gson.jsonBody
import com.github.kittinunf.result.Result
import com.preference.PowerPreference
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.RegistrationResponse
import net.openid.appauth.ResponseTypeValues

fun AuthState.save() {
  PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).apply {
    val value = jsonSerializeString()
    setString("state", value)
  }
}

interface OAuth {

  fun exchange(context: Context, authorization: Intent, success: () -> Unit, error: () -> Unit)

  fun init(hostname: String): AuthState

  fun register(authState: AuthState? = null, callback: () -> Unit)

  fun authorize(activity: Activity)

  fun isAuthorized(context: Context): Boolean

  fun tryRefreshAccessToken(context: Context): Boolean

  fun tryState(): AuthState?

  fun state(): AuthState

  fun service(context: Context): AuthorizationService
}

object OAuthFactory {

  private val oAuth: OAuth

  init {
    oAuth = DefaultOAuth(AuthorizationServiceFactory())
  }

  fun instance() = oAuth
}

class AuthorizationServiceFactory {

  fun create(context: Context): AuthorizationService {
    return AuthorizationService(context)
  }
}

class DefaultOAuth(private val authorizationServiceFactory: AuthorizationServiceFactory) : OAuth {

  companion object {

    private val REDIRECT_URI =
      Uri.parse("urn:/audio.funkwhale.funkwhale-android/oauth/callback")
  }

  data class App(val client_id: String, val client_secret: String)

  override fun tryState(): AuthState? {

    val savedState = PowerPreference
      .getFileByName(AppContext.PREFS_CREDENTIALS)
      .getString("state")

    return if (savedState != null && savedState.isNotEmpty()) {
      return AuthState.jsonDeserialize(savedState)
    } else {
      null
    }
  }

  override fun state(): AuthState =
    tryState() ?: throw IllegalStateException("Couldn't find saved state")

  override fun isAuthorized(context: Context): Boolean {
    val state = tryState()
    return if (state != null) {
      state.isAuthorized || doTryRefreshAccessToken(state, context)
    } else {
      false
    }.also {
      it.log("isAuthorized()")
    }
  }

  override fun tryRefreshAccessToken(context: Context): Boolean {
    tryState()?.let { state ->
      return doTryRefreshAccessToken(state, context)
    }
    return false
  }

  private fun doTryRefreshAccessToken(
    state: AuthState,
    context: Context
  ): Boolean {
    if (state.needsTokenRefresh && state.refreshToken != null) {
      val refreshRequest = state.createTokenRefreshRequest()
      val auth = ClientSecretPost(state.clientSecret)
      runBlocking {
        service(context).performTokenRequest(refreshRequest, auth) { response, e ->
          state.apply {
            update(response, e)
            save()
          }
        }
      }
    }
    return (state.isAuthorized)
      .also {
        it.log("tryRefreshAccessToken()")
      }
  }

  override fun init(hostname: String): AuthState {
    return AuthState(
      AuthorizationServiceConfiguration(
        Uri.parse("$hostname/authorize"),
        Uri.parse("$hostname/api/v1/oauth/token/"),
        Uri.parse("$hostname/api/v1/oauth/apps/")
      )
    )
      .also {
        it.save()
      }
  }

  override fun service(context: Context): AuthorizationService =
    authorizationServiceFactory.create(context)

  override fun register(authState: AuthState?, callback: () -> Unit) {
    (authState ?: state()).authorizationServiceConfiguration?.let { config ->
      runBlocking {
        val (_, _, result: Result<App, FuelError>) = Fuel.post(config.registrationEndpoint.toString())
          .header("Content-Type", "application/json")
          .jsonBody(registrationBody())
          .awaitObjectResponseResult(gsonDeserializerOf(App::class.java))

        when (result) {
          is Result.Success -> {
            val app = result.get()

            val response = RegistrationResponse.Builder(registration()!!)
              .setClientId(app.client_id)
              .setClientSecret(app.client_secret)
              .setClientIdIssuedAt(0)
              .setClientSecretExpiresAt(null)
              .build()

            state().apply {
              update(response)
              save()

              callback()
            }
          }

          is Result.Failure -> {
            result.log("register()")
          }
        }
      }
    }
  }

  private fun registrationBody(): Map<String, String> {
    return mapOf(
      "name" to "Funkwhale for Android (${android.os.Build.MODEL})",
      "redirect_uris" to REDIRECT_URI.toString(),
      "scopes" to "read write"
    )
  }

  override fun authorize(activity: Activity) {
    val authService = service(activity)
    authorizationRequest()?.let { it ->
      val intent = authService.getAuthorizationRequestIntent(it)
      activity.startActivityForResult(intent, 0)
    }
  }

  override fun exchange(
    context: Context,
    authorization: Intent,
    success: () -> Unit,
    error: () -> Unit
  ) {
    state().let { state ->
      state.apply {
        update(
          AuthorizationResponse.fromIntent(authorization),
          AuthorizationException.fromIntent(authorization)
        )
        save()
      }

      AuthorizationResponse.fromIntent(authorization)?.let {
        val auth = ClientSecretPost(state().clientSecret)

        service(context).performTokenRequest(it.createTokenExchangeRequest(), auth) { response, e ->
          state
            .apply {
              update(response, e)
              save()
            }

          if (response != null) success()
          else error()
        }
      }
    }
  }

  private fun registration() =
    state().authorizationServiceConfiguration?.let { config ->
      RegistrationRequest.Builder(config, listOf(REDIRECT_URI)).build()
    }

  private fun authorizationRequest() = state().let { state ->
    state.authorizationServiceConfiguration?.let { config ->
      AuthorizationRequest.Builder(
        config,
        state.lastRegistrationResponse?.clientId ?: "",
        ResponseTypeValues.CODE,
        REDIRECT_URI
      )
        .setScopes("read", "write")
        .build()
    }
  }
}
