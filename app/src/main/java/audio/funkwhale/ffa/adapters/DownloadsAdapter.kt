package audio.funkwhale.ffa.adapters

import android.content.Context
import android.graphics.drawable.Icon
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.databinding.RowDownloadBinding
import audio.funkwhale.ffa.model.DownloadInfo
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.playback.PinService
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService

class DownloadsAdapter(
  private val layoutInflater: LayoutInflater,
  private val context: Context,
  private val listener: OnDownloadChangedListener
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {

  interface OnDownloadChangedListener {
    fun onItemRemoved(index: Int)
  }

  private lateinit var binding: RowDownloadBinding

  var downloads: MutableList<DownloadInfo> = mutableListOf()

  override fun getItemCount() = downloads.size

  override fun getItemId(position: Int) = downloads[position].id.toLong()

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

    binding = RowDownloadBinding.inflate(layoutInflater, parent, false)

    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val download = downloads[position]

    holder.title.text = download.title
    holder.artist.text = download.artist

    download.download?.let { state ->
      when (state.isTerminalState) {
        true -> {
          holder.progress.visibility = View.INVISIBLE

          when (state.state) {
            Download.STATE_FAILED -> {
              holder.toggle.setImageDrawable(context.getDrawable(R.drawable.retry))
              holder.progress.visibility = View.INVISIBLE
            }

            else -> holder.toggle.visibility = View.GONE
          }
        }

        false -> {
          holder.progress.visibility = View.VISIBLE
          holder.toggle.visibility = View.VISIBLE
          holder.progress.isIndeterminate = false
          holder.progress.progress = state.percentDownloaded.toInt()

          when (state.state) {
            Download.STATE_QUEUED -> {
              holder.progress.isIndeterminate = true
            }

            Download.STATE_REMOVING -> {
              holder.progress.visibility = View.GONE
              holder.toggle.visibility = View.GONE
            }

            Download.STATE_STOPPED -> holder.toggle.setImageIcon(
              Icon.createWithResource(
                context,
                R.drawable.play
              )
            )

            else -> holder.toggle.setImageIcon(Icon.createWithResource(context, R.drawable.pause))
          }
        }
      }

      holder.toggle.setOnClickListener {
        when (state.state) {
          Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> DownloadService.sendSetStopReason(
            context,
            PinService::class.java,
            download.contentId,
            1,
            false
          )

          Download.STATE_FAILED -> {
            Track.fromDownload(download).also {
              PinService.download(context, it)
            }
          }

          else -> DownloadService.sendSetStopReason(
            context,
            PinService::class.java,
            download.contentId,
            Download.STOP_REASON_NONE,
            false
          )
        }
      }

      holder.delete.setOnClickListener {
        listener.onItemRemoved(position)
        DownloadService.sendRemoveDownload(
          context,
          PinService::class.java,
          download.contentId,
          false
        )
      }
    }
  }

  inner class ViewHolder(binding: RowDownloadBinding) : RecyclerView.ViewHolder(binding.root) {
    val title = binding.title
    val artist = binding.artist
    val progress = binding.progress
    val toggle = binding.toggle
    val delete = binding.delete
  }
}
