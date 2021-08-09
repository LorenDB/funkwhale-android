package audio.funkwhale.ffa

import android.content.Context
import com.preference.PowerPreference
import com.preference.Preference
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import strikt.api.expectThat
import strikt.assertions.isFalse

class FFATest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `deleteAllData() should clear credentials preferences`() {
    mockkStatic(PowerPreference::class)
    val preference = mockk<Preference>(relaxed = true)
    every { PowerPreference.getFileByName("credentials") } returns preference

    val context = mockk<Context>()
    every { context.cacheDir } returns mockk(relaxed = true)
    FFA().deleteAllData(context)
    verify { preference.clear() }
  }

  @Test
  fun `deleteAllData() should delete cacheDir contents`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName("credentials") } returns mockk(relaxed = true)

    val tempFile = temporaryFolder.newFile()

    val context = mockk<Context>()
    every { context.cacheDir } returns temporaryFolder.root
    FFA().deleteAllData(context)

    expectThat(tempFile.exists()).isFalse()
  }

  @Test
  fun `deleteAllData() should delete picasso cache`() {
    mockkStatic(PowerPreference::class)
    every { PowerPreference.getFileByName("credentials") } returns mockk(relaxed = true)

    val picassoCache = temporaryFolder.newFolder("picasso-cache")

    val context = mockk<Context>()
    every { context.cacheDir } returns temporaryFolder.root

    FFA().deleteAllData(context)

    expectThat(picassoCache.exists()).isFalse()
  }
}