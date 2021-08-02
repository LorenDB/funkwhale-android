package audio.funkwhale.ffa.playback

import android.content.Context
import android.net.Uri
import audio.funkwhale.ffa.utils.OAuth
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.TransferListener
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class OAuthDatasourceTest {

  @InjectMockKs
  private lateinit var datasource: OAuthDatasource

  @MockK
  private lateinit var context: Context

  @MockK
  private lateinit var http: HttpDataSource

  @MockK
  private lateinit var oAuth: OAuth

  private var dataSpec: DataSpec = DataSpec(Uri.EMPTY)

  @Before
  fun setup(){
    MockKAnnotations.init(this, relaxUnitFun = true)
  }

  @Test
  fun `open() should set accessToken and delegate to http dataSource`() {
    every { http.open(any()) } returns 0
    every { oAuth.tryRefreshAccessToken(any()) } returns true
    every { oAuth.state().accessToken } returns "accessToken"

    datasource.open(dataSpec)
    verify { http.open(dataSpec) }
    verify { http.setRequestProperty("Authorization", "Bearer accessToken") }
  }

  @Test
  fun `close() should delegate to http dataSource`() {
    datasource.close()
    verify { http.close() }
  }

  @Test
  fun `addTransferListener() should delegate to http dataSource`() {
    val transferListener = mockk<TransferListener>()
    datasource.addTransferListener(transferListener)
    verify { http.addTransferListener(transferListener) }
  }

  @Test
  fun `read() should delegate to http dataSource`() {
    every { http.read(any(), any(), any()) } returns 0
    datasource.read("123".encodeToByteArray(), 1, 2)
    verify { http.read("123".encodeToByteArray(), 1, 2) }
  }

  @Test
  fun `getUri() should delegate to http dataSource`() {
    every { http.uri } returns Uri.EMPTY
    val result = datasource.uri
    verify { http.uri }
    expectThat(result).isEqualTo(Uri.EMPTY)
  }
}
