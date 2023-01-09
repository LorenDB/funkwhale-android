package audio.funkwhale.ffa.utils

import android.content.Context
import android.net.Uri
import android.transition.CircularPropagation
import android.util.Log
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import audio.funkwhale.ffa.BuildConfig
import audio.funkwhale.ffa.R
import com.squareup.picasso.Downloader
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import com.squareup.picasso.Request
import com.squareup.picasso.RequestCreator
import com.squareup.picasso.RequestHandler
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okio.Okio
import java.io.File
import java.security.MessageDigest

/**
 * Represent bytes as hex values.
 */
fun ByteArray.toHex(): String = joinToString("") { b -> "%02x".format(b) }

/**
 * Convert the string to its SHA-256 hash in hex format.
 */
fun String.sha256(): String =
  let { MessageDigest.getInstance("SHA-256").digest(it.encodeToByteArray()).toHex() }

/**
 * Remove the query string and fragment from a URI.
 * Mostly, this is to get rid of pre-signed URL silliness.
 * If we ever need to keep some query params, we'll need a more robust approach.
 */
fun Uri.asStableKey(): String = buildUpon().clearQuery().fragment("").build().toString()

/**
 * Try to extract a file suffix from the URI.  This isn't strictly
 * necessary, but it can make debugging easier when you're going through
 * the app cache with a filesystem browser.
 */
fun Uri.fileSuffix(): String = let {
  val p = it.path
  val ext = p?.substringAfterLast(".", "")?.lowercase() ?: ""
  if (ext == "") ext else ".$ext"
}

/**
 * Wrapper around Picasso with some smarter caching of image files.
 */
open class CoverArt private constructor() {
  companion object {
    // For logging
    val TAG: String = CoverArt::class.java.simpleName

    // This is just a nice-to-have for API admins
    private const val userAgent =
      "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    // This client has the UA above, and has caching intentionally disabled.
    // (Because we cache the images ourselves and cannot rely on replaying requests.)
    private var httpClient: OkHttpClient? = null

    // Same: this has caching disabled.
    private var downloader: OkHttp3Downloader? = null

    // Cache with some useful concurrency semantics.  See its docs for details.
    val fileCache = Bottleneck<File>()

    /**
     * We don't need to hang onto the Context, just the Path it gets us.
     */
    fun cacheDirForContext(context: Context): File {
      return context.applicationContext.cacheDir.resolve("covers")
    }

    /**
     * Shim for Picasso which acts like a NetworkRequestHandler, but is opinionated
     * about how we want to use it.
     */
    open class CoverNetworkRequestHandler(context: Context) : RequestHandler() {
      /**
       * Path to the actual cache directory.
       */
      val coverCacheDir: File

      /**
       * This goes out with every request and never changes.
       */
      val noCacheControl: CacheControl = CacheControl.Builder()
        .noCache()
        .noStore()
        .noTransform()
        .build()

      init {
        coverCacheDir = cacheDirForContext(context)
        // Make the cache directory if it doesn't already exist.
        if (!coverCacheDir.isDirectory) {
          coverCacheDir.mkdir()
        }
      }

      /**
       * The primary logic of going from a Request to a usable File.
       * tl;dr: Use a local file if you can, otherwise download it and use that.
       */
      private fun materializeFile(request: Request): (String) -> File? {
        return fun(fileName: String): File? {
          val existing = coverCacheDir.resolve(fileName)
          if (existing.isFile) {
            return existing
          }
          val key = request.stableKey ?: request.uri.asStableKey()
          val httpUrl = HttpUrl.parse(request.uri.toString()) ?: return null
          return fetchToFile(httpUrl, fileName, key)
        }
      }

      /**
       * Required by Picasso, we only want to handle HTTP traffic.
       */
      override fun canHandleRequest(data: Request?): Boolean {
        return data != null && ("http" == data.uri.scheme || "https" == data.uri.scheme)
      }

      /**
       * Required by Picasso, this is the main entrypoint.
       */
      override fun load(request: Request?, networkPolicy: Int): Result? {
        if (request == null || !NetworkPolicy.shouldReadFromDiskCache(networkPolicy)) {
          return null
        }
        // Ditch any query params.
        val key = request.stableKey ?: request.uri.asStableKey()
        // Convert to a short, stable filename.
        val fileName =
          key.sha256() + request.uri.fileSuffix() // file extension for easier forensics
        // Actually find or fetch the file.
        val file = fileCache.getOrCompute(fileName, materializeFile(request))
        // Hand it back to Picasso in a way it can understand.
        return if (file == null) null else Result(Okio.source(file), LoadedFrom.DISK)
      }

      /**
       * The actual fetch logic is straightforward: download to a file.
       * Sadly, this is more manual than you might expect.
       */
      private fun fetchToFile(httpUrl: HttpUrl, fileName: String, cacheKey: String): File? {
        val httpRequest = okhttp3.Request.Builder()
          .get()
          .url(httpUrl)
          .cacheControl(noCacheControl)
          .build()
        val response = nonCachingDownloader().load(httpRequest)
        if (!response.isSuccessful) {
          return null
        }
        val body = response.body() ?: return null
        val file = coverCacheDir.resolve(fileName)
        if (BuildConfig.DEBUG) {
          Log.d(TAG, "fetchToFile($cacheKey) <- $fileName <- NETWORK")
        }
        val bytesWritten: Long
        body.use { b ->
          Okio.buffer(Okio.sink(file)).use { sink ->
            bytesWritten = sink.writeAll(b.source())
          }
        }
        return if (bytesWritten > 0) file else null
      }
    }

    /**
     * Picasso can send back notification that files are busted.
     * In those cases, it could be a transient problem, or credentials, etc.
     * We probably don't want to trust the file, so we invalidate it
     * from the memory cache and delete it from the filesystem.
     * This uses Bottleneck, so it's thread-safe.
     */
    fun invalidateIn(context: Context): (Picasso, Uri, Exception) -> Unit {
      val coverCacheDir = cacheDirForContext(context)
      return fun(_, uri: Uri, _) {
        val key = uri.asStableKey()
        val fileName = key.sha256() + uri.fileSuffix()
        fileCache.remove(fileName) { f, _ ->
          val file = f ?: coverCacheDir.resolve(fileName)
          if (file.isFile) {
            if (BuildConfig.DEBUG) {
              Log.d(TAG, "Deleting failed cover: $file")
            }
            file.delete()
          }
        }
      }
    }

    /**
     * Low-level Picasso wiring.
     */
    private fun buildPicasso(context: Context) = Picasso.Builder(context)
      // The bulk of the work happens here
      .addRequestHandler(CoverNetworkRequestHandler(context))
      // Be careful with this.  There's at least one place in Picasso where it
      // doesn't null-check when logging, so it'll throw errors in places you
      // wouldn't get them with logging turned off.  /sigh
      .loggingEnabled(false) // (BuildConfig.DEBUG)
      // Occasionally, we may get transient HTTP issues, or bogus files.
      // Listen for Picasso errors and invalidate those files
      .listener(invalidateIn(context))
      .build()

    /**
     * We don't want to cache the HTTP part of the flow, because:
     * 1. It's double-caching, since we're saving the images already.
     * 2. The URL may include pre-signed credentials, which expire, making the URL useless.
     */
    protected fun nonCachingDownloader(): Downloader {
      val downloader = this.downloader ?: OkHttp3Downloader(nonCachingHttpClient())
      if (this.downloader == null) {
        this.downloader = downloader
      }
      return downloader
    }

    /**
     * Same here: build a non-caching version just for cover art.
     */
    protected fun nonCachingHttpClient(): OkHttpClient {
      val hc = httpClient ?: OkHttpClient.Builder()
        .addInterceptor { chain ->
          chain.proceed(
            chain.request()
              .newBuilder()
              .addHeader("User-Agent", userAgent)
              .build()
          )
        }
        .cache(null) // No cache here, intentionally
        .build()
      if (httpClient == null) {
        httpClient = hc
      }
      return hc
    }

    /**
     * The primary entrypoint for the codebase.
     */
    fun withContext(context: Context, url: String?): RequestCreator {
      val request = buildPicasso(context).load(url)
      if(url == null) request.placeholder(R.drawable.cover)
      else request.placeholder(CircularProgressDrawable(context))
      return request.error(R.drawable.cover)
    }
  }
}
