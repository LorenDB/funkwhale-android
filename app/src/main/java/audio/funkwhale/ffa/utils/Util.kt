package audio.funkwhale.ffa.utils

import android.content.Context
import android.widget.Toast
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Log.LOG_LEVEL_ERROR
import com.google.android.exoplayer2.util.Log.LOG_LEVEL_INFO
import com.preference.PowerPreference
import java.net.URI

fun Context?.toast(message: String, length: Int = Toast.LENGTH_SHORT) {
  if (this != null) {
    Toast.makeText(this, message, length).show()
  }
}

private fun logClassName(): String {
  val known = setOf(
    "dalvik.system.VMStack",
    "java.lang.Thread",
    "audio.funkwhale.ffa.utils.UtilKt"
  )

  Thread.currentThread().stackTrace.forEach {
    if (!known.contains(it.className)) {
      val className = it.className.split('.').last()
      val line = it.lineNumber

      return "$className:$line"
    }
  }

  return "UNKNOWN"
}

enum class LogLevel(value: Int) {
  INFO(LOG_LEVEL_INFO),
  DEBUG(Log.LOG_LEVEL_ALL),
  ERROR(LOG_LEVEL_ERROR)
}

fun Any?.logError(prefix: String? = null) = this.log(prefix, LogLevel.ERROR)
fun Any?.logInfo(prefix: String? = null) = this.log(prefix, LogLevel.INFO)

fun Any?.log(prefix: String? = null, logLevel: LogLevel = LogLevel.DEBUG) {
  val tag = "FFA"
  val message = "${logClassName()} - ${prefix?.let { "$it: " }}$this"
  when (logLevel) {
    LogLevel.DEBUG -> Log.d(tag, message)
    LogLevel.INFO -> Log.i(tag, message)
    LogLevel.ERROR -> Log.e(tag, message)
  }
}

fun maybeNormalizeUrl(rawUrl: String?): String? {
  try {
    if (rawUrl == null || rawUrl.isEmpty()) return null

    return mustNormalizeUrl(rawUrl)
  } catch (e: Exception) {
    return null
  }
}

fun mustNormalizeUrl(rawUrl: String): String {
  val fallbackHost =
    PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).getString("hostname")
  val uri = URI(rawUrl).takeIf { it.host != null } ?: URI("$fallbackHost$rawUrl")
  return uri.toString()
}

fun toDurationString(duration: Long, showSeconds: Boolean = false): String {
  val days = (duration / 86400)
  val hours = (duration % 86400) / 3600
  val minutes = (duration % 86400 % 3600) / 60
  val seconds = duration % 86400 % 3600 % 60

  val ret = StringBuilder()

  if (days > 0) ret.append("${days}d ")
  if (hours > 0) ret.append("${hours}h ")
  if (minutes > 0) ret.append("${minutes}m ")
  if (showSeconds && seconds > 0) ret.append("${seconds}s")

  return ret.toString()
}

object Settings {

  fun isAnonymous() =
    PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).getBoolean("anonymous", false)

  fun getScopes() = PowerPreference.getDefaultFile().getString("scope", "all").split(",")
}
