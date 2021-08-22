package audio.funkwhale.ffa.model

interface SearchResult {
  fun cover(): String?
  fun title(): String
  fun subtitle(): String
}