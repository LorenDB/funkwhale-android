package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.utils.OtterResponse
import audio.funkwhale.ffa.utils.Radio
import audio.funkwhale.ffa.utils.RadiosCache
import audio.funkwhale.ffa.utils.RadiosResponse
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader

class RadiosRepository(override val context: Context?) : Repository<Radio, RadiosCache>() {

  override val cacheId = "radios"

  override val upstream = HttpUpstream<Radio, OtterResponse<Radio>>(
    context,
    HttpUpstream.Behavior.Progressive,
    "/api/v1/radios/radios/?ordering=name",
    object : TypeToken<RadiosResponse>() {}.type
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
