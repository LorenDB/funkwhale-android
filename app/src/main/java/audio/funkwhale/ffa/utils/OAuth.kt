package audio.funkwhale.ffa.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.github.kittinunf.fuel.Fuel
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

  fun exchange(context: Activity, authorization: Intent, success: () -> Unit, error: () -> Unit)

  fun init(hostname: String)

  fun register(callback: () -> Unit)

  fun authorize(context: Activity)

  fun isAuthorized(context: Context): Boolean

  fun tryRefreshAccessToken(context: Context, overrideNeedsTokenRefresh: Boolean = false): Boolean

  fun tryState(): AuthState?

  fun state(): AuthState

  fun service(context: Context): AuthorizationService
}

object OAuthFactory {

  private val oAuth: OAuth

  init {
    oAuth = DefaultOAuth()
  }

  fun instance() = oAuth
}

class DefaultOAuth : OAuth {

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

  override fun state(): AuthState = tryState()!!

  override fun isAuthorized(context: Context): Boolean {
    val state = tryState()
    return if (state != null) {
      state.isAuthorized || tryRefreshAccessToken(context)
    } else {
      false
    }.also {
      it.log("isAuthorized()")
    }
  }

  override fun tryRefreshAccessToken(
    context: Context,
    overrideNeedsTokenRefresh: Boolean
  ): Boolean {
    tryState()?.let { state ->
      val shouldRefreshAccessToken = overrideNeedsTokenRefresh || state.needsTokenRefresh
      if (shouldRefreshAccessToken && state.refreshToken != null) {
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
    }

    return (tryState()?.isAuthorized ?: false)
      .also {
        it.log("tryRefreshAccessToken()")
      }
  }

  override fun init(hostname: String) {
    AuthState(config(hostname)).save()
  }

  override fun service(context: Context): AuthorizationService = AuthorizationService(context)

  override fun register(callback: () -> Unit) {
    state().authorizationServiceConfiguration?.let { config ->

      runBlocking {
        val (_, _, result) = Fuel.post(config.registrationEndpoint.toString())
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

  override fun authorize(context: Activity) {
    val intent = service(context).run {
      authorizationRequest()?.let {
        getAuthorizationRequestIntent(it)
      }
    }

    context.startActivityForResult(intent, 0)
  }

  override fun exchange(
    context: Activity,
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

  private fun config(hostname: String) = AuthorizationServiceConfiguration(
    Uri.parse("$hostname/authorize"),
    Uri.parse("$hostname/api/v1/oauth/token/"),
    Uri.parse("$hostname/api/v1/oauth/apps/")
  )

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
