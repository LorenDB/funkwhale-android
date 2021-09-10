package audio.funkwhale.ffa.adapters

import audio.funkwhale.ffa.repositories.FavoritesRepository

class FavoriteListener(private val repository: FavoritesRepository) {

  fun onToggleFavorite(id: Int, state: Boolean) {
    when (state) {
      true -> repository.addFavorite(id)
      false -> repository.deleteFavorite(id)
    }
  }
}
