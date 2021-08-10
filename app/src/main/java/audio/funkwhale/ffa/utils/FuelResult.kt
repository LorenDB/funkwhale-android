package audio.funkwhale.ffa.utils

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result

data class FuelResult(val httpStatus: Int? = null, val message: String? = null) {

  val success: Boolean get() = httpStatus == 200

  companion object {

    fun ok() = FuelResult(200)

    fun from(result: Result.Failure<FuelError>): FuelResult {
      return FuelResult(result.error.response.statusCode, result.error.response.responseMessage)
    }

    fun failure(): FuelResult {
      return FuelResult()
    }
  }
}