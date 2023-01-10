package audio.funkwhale.ffa.utils

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Similar to a Map, but with the semantic that operations single-thread on a per-key basis.
 * That is: given concurrent accesses to keys "apple" and "banana", one "apple" thread
 * will block all other "apple" threads, but not any "banana" threads.
 * In practical terms, we use this to make sure we don't get weird edge cases when working
 * with the filesystem cache.
 */
class Bottleneck<T> {
  // It would be nice to use LruCache here, but its behavior of
  // replacing values doesn't get us the right results.
  // As it is, this should be a trivial amount of memory compared to
  // images and media.
  // We single-thread this, so it doesn't need to be concurrent.
  private val keys = WeakHashMap<String, String>()

  // This one needs to be concurrent, as we don't want to single-thread it.
  private val values = ConcurrentHashMap<String, WeakReference<T>>()

  /**
   * As you would expect from the Map function of the same name, except concurrent
   * accesses to the same key will block on each other.  If the first call succeeds,
   * all other calls will fall through with the same result.  (Unlike LRUCache.)
   */
  fun getOrCompute(key: String, materialize: (key: String) -> T?): T? {
    // First, get the lockable version of the key, no matter how
    // many copies of the key exist.
    // This map doesn't need to be a synchronized collection, because
    // we single-thread access to it.  (And there's no compute, so
    // it should be low-contention.)
    val sharedKey: String = canonical(key)
    synchronized(sharedKey) {
      val ref = values[sharedKey]
      var value = ref?.get()
      if (value == null) {
        if (ref != null) {
          values.remove(sharedKey) // empty ref
        }
        value = materialize(sharedKey)
        if (value != null) {
          values[sharedKey] = WeakReference(value)
        }
      }
      return value
    }
  }

  /**
   * The beating heart of this system: each key is is "upgraded" to
   * the one which we use for locking.  This does mean we block on
   * access to `keys` for all concurrent access, but as it's so light-
   * weight, this shouldn't be much of a problem in practical terms.
   * The hope here is that this is slightly better than interning.
   * In theory we could convert this over to also use WeakReference.
   */
  private fun canonical(key: String): String {
    val sharedKey: String
    synchronized(keys) {
      val maybeShared = keys[key]
      if (maybeShared == null) {
        keys[key] = key // first key of its value becomes canonical
        sharedKey = key
      } else {
        sharedKey = maybeShared
      }
    }
    return sharedKey
  }

  /**
   * Invalidate a key and run the supplied bi-consumer with the old value.
   * Note that this will <em>always</em> run the supplied block, even if
   * the value is not in the cache.
   */
  fun remove(key: String, andDo: ((T?, String) -> Unit)?) {
    val sharedKey = canonical(key)
    synchronized(sharedKey) {
      val oldValue = values.remove(sharedKey)
      if (andDo != null) {
        andDo(oldValue?.get(), sharedKey)
      }
    }
  }
}
