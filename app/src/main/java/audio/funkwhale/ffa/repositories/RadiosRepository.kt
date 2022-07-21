package audio.funkwhale.ffa.repositories

import android.content.Context
import audio.funkwhale.ffa.model.FFAResponse
import audio.funkwhale.ffa.model.Radio
import audio.funkwhale.ffa.model.RadiosCache
import audio.funkwhale.ffa.model.RadiosResponse
import audio.funkwhale.ffa.utils.OAuth
import audio.funkwhale.ffa.utils.gsonDeserializerOf
import com.google.gson.reflect.TypeToken
import org.koin.java.KoinJavaComponent.inject

class RadiosRepository(override val context: Context?) : Repository<Radio, RadiosCache>() {

  private val oAuth: OAuth by inject(OAuth::class.java)

  override val cacheId = "radios"

  override val upstream = HttpUpstream<Radio, FFAResponse<Radio>>(
    context,
    HttpUpstream.Behavior.Progressive,
    "/api/v1/radios/radios/?ordering=name",
    object : TypeToken<RadiosResponse>() {}.type,
    oAuth
  )

  override fun cache(data: List<Radio>) = RadiosCache(data)
  override fun uncache(json: String) =
    gsonDeserializerOf(RadiosCache::class.java).deserialize(json.reader())

  override fun onDataFetched(data: List<Radio>): List<Radio> {
    return data
      .map { radio -> radio.apply { radio_type = "custom" } }
      .toMutableList()
  }
}
