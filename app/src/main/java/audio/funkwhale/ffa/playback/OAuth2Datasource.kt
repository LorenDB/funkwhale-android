package audio.funkwhale.ffa.playback

import android.content.Context
import android.net.Uri
import audio.funkwhale.ffa.utils.OAuth
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.TransferListener

class OAuthDatasource(
  private val context: Context,
  private val http: HttpDataSource
) : DataSource {

  override fun addTransferListener(transferListener: TransferListener?) {
    http.addTransferListener(transferListener)
  }

  override fun open(dataSpec: DataSpec?): Long {
    OAuth.tryRefreshAccessToken(context)
    return http.open(dataSpec)
  }

  override fun read(buffer: ByteArray?, offset: Int, readLength: Int): Int {
    return http.read(buffer, offset, readLength)
  }

  override fun getUri(): Uri? {
    return http.uri
  }

  override fun close() {
    http.close()
  }

}

class OAuth2DatasourceFactory(
  private val context: Context,
  private val http: DefaultHttpDataSourceFactory
) : DataSource.Factory {

  override fun createDataSource(): DataSource {
    return OAuthDatasource(context, http.createDataSource())
  }
}
