package audio.funkwhale.ffa.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
    setString("state", jsonSerializeString())
  }
}

class AuthorizationServiceFactory {

  fun create(context: Context): AuthorizationService {
    return AuthorizationService(context)
  }
}

class OAuth(private val authorizationServiceFactory: AuthorizationServiceFactory) {

  companion object {
    private val REDIRECT_URI =
      Uri.parse("urn:/audio.funkwhale.funkwhale-android/oauth/callback")
  }

  data class App(val client_id: String, val client_secret: String)

  fun tryState(): AuthState? {

    val savedState = PowerPreference
      .getFileByName(AppContext.PREFS_CREDENTIALS)
      .getString("state")

    return if (savedState != null && savedState.isNotEmpty()) {
      return AuthState.jsonDeserialize(savedState)
    } else {
      null
    }
  }

  fun state(): AuthState {
    return tryState() ?: throw IllegalStateException("Couldn't find saved state")
  }

  fun isAuthorized(context: Context): Boolean {
    val state = tryState()
    return (
      if (state != null) {
        state.validAuthorization() || refreshAccessToken(state, context)
      } else {
        false
      }
      )
      .also { it.logInfo("isAuthorized()") }
  }

  private fun AuthState.validAuthorization() = this.isAuthorized && !this.needsTokenRefresh

  fun tryRefreshAccessToken(context: Context): Boolean {
    tryState()?.let { state ->
      return if (state.needsTokenRefresh && state.refreshToken != null) {
        refreshAccessToken(state, context)
      } else {
        state.isAuthorized
      }.also { it.logInfo("tryRefreshAccessToken()") }
    }
    return false
  }

  fun refreshAccessToken(context: Context): Boolean {
    return tryState()?.let { refreshAccessToken(it, context) } ?: false
  }

  private fun refreshAccessToken(state: AuthState, context: Context): Boolean {
    Log.i("OAuth", "refreshAccessToken()")
    return if (state.refreshToken != null) {
      val refreshRequest = state.createTokenRefreshRequest()
      val auth = ClientSecretPost(state.clientSecret)
      val refreshService = service(context)
      runBlocking {
        refreshService.performTokenRequest(refreshRequest, auth) { response, e ->
          if (e != null) {
            Log.e("OAuth", "performTokenRequest failed: $e")
            Log.e("OAuth", Log.getStackTraceString(e))
          } else {
            state.apply {
              Log.i("OAuth", "applying new authState")
              update(response, e)
              save()
            }
          }
        }
      }
      refreshService.dispose()
      true
    } else {
      false
    }
  }

  fun init(hostname: String): AuthState {
    return AuthState(
      AuthorizationServiceConfiguration(
        Uri.parse("$hostname/authorize"),
        Uri.parse("$hostname/api/v1/oauth/token/"),
        Uri.parse("$hostname/api/v1/oauth/apps/")
      )
    ).also {
      it.save()
    }
  }

  fun service(context: Context): AuthorizationService =
    authorizationServiceFactory.create(context)

  fun register(authState: AuthState? = null, callback: () -> Unit): FuelResult {
    (authState ?: state()).authorizationServiceConfiguration?.let { config ->

      val (_, _, result: Result<App, FuelError>) = runBlocking {
        Fuel.post(config.registrationEndpoint.toString())
          .header("Content-Type", "application/json")
          .jsonBody(registrationBody())
          .awaitObjectResponseResult(gsonDeserializerOf(App::class.java))
      }

      when (result) {
        is Result.Success -> {
          Log.i("OAuth", "OAuth client app created.")
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
            return FuelResult.ok()
          }
        }

        is Result.Failure -> {
          Log.i(
            "OAuth", "Couldn't register client application ${result.error.formatResponseMessage()}"
          )
          return FuelResult.from(result)
        }
      }
    }
    Log.i("OAuth", "Missing AuthorizationServiceConfiguration")
    return FuelResult.failure()
  }

  private fun registrationBody(): Map<String, String> {
    return mapOf(
      "name" to "Funkwhale for Android (${android.os.Build.MODEL})",
      "redirect_uris" to REDIRECT_URI.toString(),
      "scopes" to "read write"
    )
  }

  fun authorizeIntent(activity: Activity): Intent? {
    val authService = service(activity)
    return authorizationRequest()?.let { it ->
      authService.getAuthorizationRequestIntent(it)
    }
  }

  fun exchange(
    context: Context,
    authorization: Intent,
    success: () -> Unit
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
        val requestService = service(context)

        requestService.performTokenRequest(it.createTokenExchangeRequest(), auth) { response, e ->
          if (e != null) {
            Log.e("FFA", "performTokenRequest failed: $e")
            Log.e("FFA", Log.getStackTraceString(e))
          } else {
            state.apply {
              update(response, e)
              save()
            }
          }

          if (response != null) success()
          else Log.e("FFA", "performTokenRequest() not successful")
        }
        requestService.dispose()
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
