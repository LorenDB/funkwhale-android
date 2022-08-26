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

    return digest.fold("") { acc, it -> acc + "%02x".format(it) }
  }

  fun set(context: Context?, key: String, value: String) {
    set(context, key, value.toByteArray())
  }

  fun set(context: Context?, key: String, value: ByteArray) {
    context?.let {
      with(File(it.cacheDir, key(key))) {
        writeBytes(value)
      }
    }
  }

  fun getLine(context: Context?, key: String): String? = get(context, key)?.let {
    val line = it.readLine()
    it.close()
    line
  }

  fun getLines(context: Context?, key: String): List<String>? = get(context, key)
    ?.let { reader ->
      val lines = reader.readLines()
      reader.close()
      lines
    }

  fun delete(context: Context?, key: String) = context?.let {
    with(File(it.cacheDir, key(key))) {
      delete()
    }
  }

  private fun get(context: Context?, key: String): BufferedReader? = context?.let {
    try {
      with(File(it.cacheDir, key(key))) {
        bufferedReader()
      }
    } catch (e: Exception) {
      return null
    }
  }
}
