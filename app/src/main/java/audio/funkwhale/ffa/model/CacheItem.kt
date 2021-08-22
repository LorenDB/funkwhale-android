package audio.funkwhale.ffa.model

sealed class CacheItem<D : Any>(val data: List<D>)

class ArtistsCache(data: List<Artist>) : CacheItem<Artist>(data)
class AlbumsCache(data: List<Album>) : CacheItem<Album>(data)
class TracksCache(data: List<Track>) : CacheItem<Track>(data)
class PlaylistsCache(data: List<Playlist>) : CacheItem<Playlist>(data)
class PlaylistTracksCache(data: List<PlaylistTrack>) : CacheItem<PlaylistTrack>(data)
class RadiosCache(data: List<Radio>) : CacheItem<Radio>(data)
class FavoritedCache(data: List<Int>) : CacheItem<Int>(data)
class QueueCache(data: List<Track>) : CacheItem<Track>(data)