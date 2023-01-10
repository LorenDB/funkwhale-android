package audio.funkwhale.ffa.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import audio.funkwhale.ffa.FFA
import audio.funkwhale.ffa.model.Album
import audio.funkwhale.ffa.model.Artist
import audio.funkwhale.ffa.model.Track
import audio.funkwhale.ffa.repositories.AlbumsSearchRepository
import audio.funkwhale.ffa.repositories.ArtistsSearchRepository
import audio.funkwhale.ffa.repositories.Repository
import audio.funkwhale.ffa.repositories.TracksSearchRepository
import audio.funkwhale.ffa.utils.mergeWith
import audio.funkwhale.ffa.utils.untilNetwork
import kotlinx.coroutines.Dispatchers
import java.net.URLEncoder
import java.util.Locale

class SearchViewModel(app: Application) : AndroidViewModel(app), Observer<String> {
  private val artistResultsLoading = MutableLiveData(false)
  private val albumResultsLoading = MutableLiveData(false)
  private val tackResultsLoading = MutableLiveData(false)

  private val artistsRepository =
    ArtistsSearchRepository(getApplication<FFA>().applicationContext, "")
  private val albumsRepository =
    AlbumsSearchRepository(getApplication<FFA>().applicationContext, "")
  private val tracksRepository =
    TracksSearchRepository(getApplication<FFA>().applicationContext, "")

  private val dedupQuery: LiveData<String>

  val query = MutableLiveData("")

  val artistResults: LiveData<List<Artist>> = MutableLiveData(listOf())
  val albumResults: LiveData<List<Album>> = MutableLiveData(listOf())
  val trackResults: LiveData<List<Track>> = MutableLiveData(listOf())

  val isLoadingData: LiveData<Boolean> = artistResultsLoading.mergeWith(
    albumResultsLoading, tackResultsLoading
  ) { b1, b2, b3 -> b1 || b2 || b3 }

  val hasResults: LiveData<Boolean> = isLoadingData.mergeWith(
    artistResults, albumResults, trackResults
  ) { b, r1, r2, r3 -> b || r1.isNotEmpty() || r2.isNotEmpty() || r3.isNotEmpty() }

  init {
    dedupQuery = query.map { it.trim().lowercase(Locale.ROOT) }.distinctUntilChanged()
    dedupQuery.observeForever(this)
  }

  override fun onChanged(token: String) {
    if (token.isBlank()) { // Empty search
      (artistResults as MutableLiveData).postValue(listOf())
      (albumResults as MutableLiveData).postValue(listOf())
      (trackResults as MutableLiveData).postValue(listOf())
      return
    }

    artistResultsLoading.postValue(true)
    albumResultsLoading.postValue(true)
    tackResultsLoading.postValue(true)

    val encoded = URLEncoder.encode(token, "UTF-8")

    (artistResults as MutableLiveData).postValue(listOf())
    artistsRepository.apply {
      query = encoded
      fetch(Repository.Origin.Network.origin).untilNetwork(
        viewModelScope,
        Dispatchers.IO
      ) { data, _, _, hasMore ->
        artistResults.postValue(artistResults.value!! + data)
        if (!hasMore) {
          artistResultsLoading.postValue(false)
        }
      }
    }

    (albumResults as MutableLiveData).postValue(listOf())
    albumsRepository.apply {
      query = encoded
      fetch(Repository.Origin.Network.origin).untilNetwork(
        viewModelScope,
        Dispatchers.IO
      ) { data, _, _, hasMore ->
        albumResults.postValue(albumResults.value!! + data)
        if (!hasMore) {
          albumResultsLoading.postValue(false)
        }
      }
    }

    (trackResults as MutableLiveData).postValue(listOf())
    tracksRepository.apply {
      query = encoded
      fetch(Repository.Origin.Network.origin).untilNetwork(
        viewModelScope,
        Dispatchers.IO
      ) { data, _, _, hasMore ->
        trackResults.postValue(trackResults.value!! + data)
        if (!hasMore) {
          tackResultsLoading.postValue(false)
        }
      }
    }
  }

  override fun onCleared() {
    dedupQuery.removeObserver(this)
  }
}
