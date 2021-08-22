package audio.funkwhale.ffa.utils

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest

object FFACache {

  private fun key(key: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val digest = md.digest(key.toByteArray(Charset.defaultCharset()))

    return digest.fold("", { acc, it -> acc + "%02x".format(it) })
  }

  fun set(context: Context?, key: String, value: ByteArray) = context?.let {
    with(File(it.cacheDir, key(key))) {
      writeBytes(value)
    }
  }

  fun get(context: Context?, key: String): BufferedReader? = context?.let {
    try {
      with(File(it.cacheDir, key(key))) {
        bufferedReader()
      }
    } catch (e: Exception) {
      return null
    }
  }

  fun delete(context: Context?, key: String) = context?.let {
    with(File(it.cacheDir, key(key))) {
      delete()
    }
  }
}
