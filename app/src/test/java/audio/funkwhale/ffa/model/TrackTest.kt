package audio.funkwhale.ffa.model

import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class TrackTest {

  @Test
  fun trackMatchesTitle() {
    expectThat(createTrackObject(trackTitle = "track").matchesFilter("track")).isTrue()
  }

  @Test
  fun trackDoesntMatchTitle() {
    expectThat(createTrackObject(trackTitle = "xxxx").matchesFilter("track")).isFalse()
  }

  @Test
  fun trackMatchesArtist() {
    expectThat(createTrackObject(artistName = "artist").matchesFilter("artist")).isTrue()
  }

  @Test
  fun trackDoesntMatchArtist() {
    expectThat(createTrackObject(artistName = "xxxx").matchesFilter("artist")).isFalse()
  }

  @Test
  fun trackMatchesAlbum() {
    expectThat(createTrackObject(albumTitle = "album").matchesFilter("album")).isTrue()
  }

  @Test
  fun trackDoesntMatchAlbum() {
    expectThat(createTrackObject(albumTitle = "xxxx").matchesFilter("album")).isFalse()
  }

  @Test
  fun trackDoesntMatchNullAlbum() {
    expectThat(createTrackObject(albumTitle = null).matchesFilter("album")).isFalse()
  }

  private fun createTrackObject(
    trackTitle: String = "trackTitle",
    artistName: String = "artistName",
    albumTitle: String? = "albumTitle"
  ) = Track(
    id = 0,
    title = trackTitle,
    artist = Artist(id = 0, name = artistName, albums = listOf()),
    album =
    if (albumTitle == null)
      null
    else Album(
      id = 0,
      title = albumTitle,
      artist = Album.Artist("albumArtist"),
      cover = null,
      release_date = null
    )
  )
}
