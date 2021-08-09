package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.*
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import org.koin.java.KoinJavaComponent.inject
import java.io.BufferedReader

class RadiosRepository(override val context: Context?) : Repository<Radio, RadiosCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "radios"

  override val upstream = HttpUpstream<Radio, OtterResponse<Radio>>(
    context,
    HttpUpstream.Behavior.Progressive,
    "/api/v1/radios/radios/?ordering=name",
    object : TypeToken<RadiosResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Radio>) = RadiosCache(data)
  override fun uncache(reader: BufferedReader) =
    gsonDeserializerOf(RadiosCache::class.java).deserialize(reader)

  override fun onDataFetched(data: List<Radio>): List<Radio> {
    return data
      .map { radio -> radio.apply { radio_type = "custom" } }
      .toMutableList()
  }
}
