package audio.funkwhale.ffa

import android.app.Application
import com.preference.PowerPreference
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module

class KoinTestApp : Application() {

  override fun onCreate() {
    super.onCreate()
    PowerPreference.init(this)
    startKoin {
      androidContext(this@KoinTestApp)
      modules(emptyList())
    }
  }

  fun loadModules(module: Module, block: () -> Unit) {
    loadKoinModules(module)
    block()
    unloadKoinModules(module)
  }
}