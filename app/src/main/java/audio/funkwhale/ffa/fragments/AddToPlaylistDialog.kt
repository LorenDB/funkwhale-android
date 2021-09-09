package audio.funkwhale.ffa.fragments

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.adapters.PlaylistsAdapter
import audio.funkwhale.ffa.databinding.DialogAddToPlaylistBinding
import audio.funkwhale.ffa.model.Playlist
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.ManagementPlaylistsRepository
import audio.funkwhale.ffa.utils.FFACache
import audio.funkwhale.ffa.utils.untilNetwork
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AddToPlaylistDialog {

  fun show(
    layoutInflater: LayoutInflater,
    activity: Activity,
    lifecycleScope: CoroutineScope,
    tracks: List<Track>
  ) {

    val binding = DialogAddToPlaylistBinding.inflate(layoutInflater)
    val dialog = AlertDialog.Builder(activity).run {
      setTitle(activity.getString(R.string.playlist_add_to))
      setView(binding.root)

      create()
    }

    dialog.show()

    val repository = ManagementPlaylistsRepository(activity)

    binding.name.editText?.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // empty
      }

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // empty
      }

      override fun afterTextChanged(s: Editable?) {
        binding.create.isEnabled = !(binding.name.editText?.text?.trim()?.isBlank() ?: true)
      }
    })

    binding.create.setOnClickListener {
      val name = binding.name.editText?.text?.toString()?.trim() ?: ""

      if (name.isEmpty()) return@setOnClickListener

      lifecycleScope.launch(IO) {
        repository.new(name)?.let { id ->
          repository.add(id, tracks)

          withContext(Main) {
            Toast.makeText(
              activity,
              activity.getString(R.string.playlist_added_to, name),
              Toast.LENGTH_SHORT
            ).show()
          }

          dialog.dismiss()
        }
      }
    }

    val adapter =
      PlaylistsAdapter(
        layoutInflater, activity,
        object : PlaylistsAdapter.OnPlaylistClickListener {
          override fun onClick(holder: View?, playlist: Playlist) {
            repository.add(playlist.id, tracks)

            Toast.makeText(
              activity,
              activity.getString(R.string.playlist_added_to, playlist.name),
              Toast.LENGTH_SHORT
            ).show()

            dialog.dismiss()
          }
        }
      )

    binding.playlists.layoutManager = LinearLayoutManager(activity)
    binding.playlists.adapter = adapter

    repository.apply {
      var first = true

      fetch().untilNetwork(lifecycleScope) { data, isCache, _, hasMore ->
        if (isCache) {
          adapter.data = data.toMutableList()
          adapter.notifyDataSetChanged()

          return@untilNetwork
        }

        if (first) {
          adapter.data.clear()
          first = false
        }

        adapter.data.addAll(data)

        lifecycleScope.launch(IO) {
          try {
            FFACache.set(
              context,
              cacheId,
              Gson().toJson(cache(adapter.data)).toByteArray()
            )
          } catch (e: ConcurrentModificationException) {
          }
        }

        if (!hasMore) {
          adapter.notifyDataSetChanged()
          first = false
        }
      }
    }
  }
}
