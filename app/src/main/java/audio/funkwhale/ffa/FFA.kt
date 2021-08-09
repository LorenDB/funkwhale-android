package audio.funkwhale.ffa

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import audio.funkwhale.ffa.koin.ffaModule
import audio.funkwhale.ffa.utils.*
import com.preference.PowerPreference
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import org.koin.core.context.startKoin
import java.text.SimpleDateFormat
import java.util.*

class FFA : Application() {

  companion object {
    private var instance: FFA = FFA()

    fun get(): FFA = instance
  }

  var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

  val eventBus: BroadcastChannel<Event> = BroadcastChannel(10)
  val commandBus: BroadcastChannel<Command> = BroadcastChannel(10)
  val requestBus: BroadcastChannel<Request> = BroadcastChannel(10)
  val progressBus: BroadcastChannel<Triple<Int, Int, Int>> = ConflatedBroadcastChannel()

  override fun onCreate() {
    super.onCreate()

    startKoin {
      modules(ffaModule(this@FFA))
    }

    defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler(CrashReportHandler())

    instance = this

    PowerPreference.init(this)

    when (PowerPreference.getDefaultFile().getString("night_mode")) {
      "on" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
      "off" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
      else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
  }

  fun deleteAllData(context: Context) {
    PowerPreference.getFileByName(AppContext.PREFS_CREDENTIALS).clear()

    context.cacheDir.listFiles()?.forEach {
      it.delete()
    }
    context.cacheDir.resolve("picasso-cache").deleteRecursively()
  }

  inner class CrashReportHandler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
      val now = Date(Date().time - (5 * 60 * 1000))
      val formatter = SimpleDateFormat("MM-dd kk:mm:ss.000", Locale.US)

      Runtime.getRuntime().exec(listOf("logcat", "-d", "-T", formatter.format(now)).toTypedArray())
        .also {
          it.inputStream.bufferedReader().also { reader ->
            val builder = StringBuilder()

            while (true) {
              builder.appendLine(reader.readLine() ?: break)
            }

            builder.appendLine(e.toString())

            FFACache.set(this@FFA, "crashdump", builder.toString().toByteArray())
          }
        }

      defaultExceptionHandler?.uncaughtException(t, e)
    }
  }
}
