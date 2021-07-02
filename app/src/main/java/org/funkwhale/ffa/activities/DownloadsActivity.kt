package org.funkwhale.ffa.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.exoplayer2.offline.Download
import kotlinx.android.synthetic.main.activity_downloads.downloads
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.funkwhale.ffa.FFA
import org.funkwhale.ffa.R
import org.funkwhale.ffa.adapters.DownloadsAdapter
import org.funkwhale.ffa.utils.Event
import org.funkwhale.ffa.utils.EventBus
import org.funkwhale.ffa.utils.getMetadata

class DownloadsActivity : AppCompatActivity() {
  lateinit var adapter: DownloadsAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_downloads)

    downloads.itemAnimator = null

    adapter = DownloadsAdapter(this, DownloadChangedListener()).also {
      it.setHasStableIds(true)

      downloads.layoutManager = LinearLayoutManager(this)
      downloads.adapter = it
    }

    lifecycleScope.launch(Default) {
      while (true) {
        delay(1000)
        refreshProgress()
      }
    }
  }

  override fun onResume() {
    super.onResume()

    lifecycleScope.launch(Default) {
      EventBus.get().collect { event ->
        if (event is Event.DownloadChanged) {
          refreshTrack(event.download)
        }
      }
    }

    refresh()
  }

  private fun refresh() {
    lifecycleScope.launch(Main) {
      val cursor = FFA.get().exoDownloadManager.downloadIndex.getDownloads()

      adapter.downloads.clear()

      while (cursor.moveToNext()) {
        val download = cursor.download

        download.getMetadata()?.let { info ->
          adapter.downloads.add(info.apply {
            this.download = download
          })
        }
      }

      adapter.notifyDataSetChanged()
    }
  }

  private suspend fun refreshTrack(download: Download) {
    download.getMetadata()?.let { info ->
      adapter.downloads.withIndex().associate { it.value to it.index }.filter { it.key.id == info.id }.toList().getOrNull(0)?.let { match ->
        if (download.state != info.download?.state) {
          withContext(Main) {
            adapter.downloads[match.second] = info.apply {
              this.download = download
            }

            adapter.notifyItemChanged(match.second)
          }
        }
      }
    }
  }

  private suspend fun refreshProgress() {
    val cursor = FFA.get().exoDownloadManager.downloadIndex.getDownloads()

    while (cursor.moveToNext()) {
      val download = cursor.download

      download.getMetadata()?.let { info ->
        adapter.downloads.withIndex().associate { it.value to it.index }.filter { it.key.id == info.id }.toList().getOrNull(0)?.let { match ->
          if (download.state == Download.STATE_DOWNLOADING && download.percentDownloaded != info.download?.percentDownloaded ?: 0) {
            withContext(Main) {
              adapter.downloads[match.second] = info.apply {
                this.download = download
              }

              adapter.notifyItemChanged(match.second)
            }
          }
        }
      }
    }
  }

  inner class DownloadChangedListener : DownloadsAdapter.OnDownloadChangedListener {
    override fun onItemRemoved(index: Int) {
      adapter.downloads.removeAt(index)
      adapter.notifyDataSetChanged()
    }
  }
}
