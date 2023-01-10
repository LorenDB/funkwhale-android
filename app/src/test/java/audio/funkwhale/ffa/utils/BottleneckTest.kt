package audio.funkwhale.ffa.utils

import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotSameInstanceAs
import strikt.assertions.isNull
import strikt.assertions.isSameInstanceAs
import strikt.assertions.isTrue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BottleneckTest {

  @Test
  fun `single threaded cache works like a cache`() {
    var callCount = 0
    val cache = Bottleneck<Int>()
    val materialize = { k: String ->
      callCount++
      k.toInt()
    }
    val key = "34"
    val keyCopy = String(key.encodeToByteArray().copyOf())
    expectThat(keyCopy).isEqualTo(key)
    expectThat(keyCopy).isNotSameInstanceAs(key)
    expectThat(callCount).isEqualTo(0)
    val first = cache.getOrCompute(key, materialize)
    expectThat(first).isEqualTo(34)
    expectThat(callCount).isEqualTo(1)
    val second = cache.getOrCompute(keyCopy, materialize)
    expectThat(second).isEqualTo(34)
    expectThat(second).isSameInstanceAs(first)
    expectThat(callCount).isEqualTo(1)
  }

  @Test
  fun `multi-threaded cache only lets one through for each key at a time`() {
    val maxThreads = 8
    val executor = ThreadPoolExecutor(
      maxThreads,
      maxThreads,
      5,
      TimeUnit.SECONDS,
      ArrayBlockingQueue(maxThreads)
    )
    val running = AtomicBoolean(false)
    val computeCount = AtomicInteger(0)
    val key = "43"
    val materialize = { k: String ->
      expectThat(running.getAndSet(true)).isFalse()
      expectThat(computeCount.incrementAndGet()).isEqualTo(1)
      Thread.sleep(3000)
      expectThat(running.getAndSet(false)).isTrue()
      expectThat(computeCount.get()).isEqualTo(1)
      k.toInt()
    }
    val cache = Bottleneck<Int>()
    val threadCount = AtomicInteger(0)
    for (c in 1..maxThreads) {
      executor.execute {
        Thread.currentThread().name = "test-thread-$c"
        val keyCopy = String(key.encodeToByteArray().copyOf())
        expectThat(cache.getOrCompute(keyCopy, materialize)).isEqualTo(43)
        threadCount.incrementAndGet()
      }
    }
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)
    expectThat(threadCount.get()).isEqualTo(maxThreads)
  }

  @Test
  fun `single-threaded remove does what you would expect`() {
    val cache = Bottleneck<Int>()
    val materialize = { k: String -> k.toInt() }
    val key = "24"
    val first = cache.getOrCompute(key, materialize)
    expectThat(first).isEqualTo(24)
    var callCount = 0
    val keyCopy = String(key.encodeToByteArray().copyOf())
    expectThat(keyCopy).isEqualTo(key)
    expectThat(keyCopy).isNotSameInstanceAs(key)
    cache.remove(keyCopy) { value, k ->
      expectThat(value).isSameInstanceAs(first)
      expectThat(k).isSameInstanceAs(key)
      callCount++
    }
    expectThat(callCount).isEqualTo(1)
    cache.remove(keyCopy) { value, k ->
      expectThat(value).isNull()
      expectThat(k).isSameInstanceAs(key)
      callCount++
    }
    expectThat(callCount).isEqualTo(2)
  }

  @Test
  fun `multi-threaded remove should synchronize and return correct results`() {
    val maxThreads = 8
    val executor = ThreadPoolExecutor(
      maxThreads,
      maxThreads,
      5,
      TimeUnit.SECONDS,
      ArrayBlockingQueue(maxThreads)
    )
    val running = AtomicBoolean(false)
    val computeCount = AtomicInteger(0)
    val key = "17"
    val dematerialize: (Int?, String) -> Unit = { value: Int?, k: String ->
      expectThat(running.getAndSet(true)).isFalse()
      if (computeCount.incrementAndGet() == 1) {
        expectThat(value).isEqualTo(17)
        Thread.sleep(3000)
        expectThat(computeCount.get()).isEqualTo(1) // no one else gets through until I'm done
      } else {
        expectThat(value).isNull()
      }
      expectThat(running.getAndSet(false)).isTrue()
      k.toInt()
    }
    val cache = Bottleneck<Int>()
    cache.getOrCompute(key) { k -> k.toInt() }
    val threadCount = AtomicInteger(0)
    for (c in 1..maxThreads) {
      executor.execute {
        Thread.currentThread().name = "test-thread-$c"
        val keyCopy = String(key.encodeToByteArray().copyOf())
        cache.remove(keyCopy, dematerialize)
        threadCount.incrementAndGet()
      }
    }
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)
    expectThat(threadCount.get()).isEqualTo(maxThreads)
  }

  @Test
  fun `blocking happens on a per-key basis`() {
    val cache = Bottleneck<Int>()
    val maxThreads = 4
    val executor = ThreadPoolExecutor(
      maxThreads,
      maxThreads,
      5,
      TimeUnit.SECONDS,
      ArrayBlockingQueue(maxThreads)
    )
    val running: Map<String, AtomicBoolean> = mapOf(
      Pair("tortoise", AtomicBoolean(false)),
      Pair("hare", AtomicBoolean(false)),
    )
    val count: Map<String, AtomicInteger> = mapOf(
      Pair("tortoise", AtomicInteger(0)),
      Pair("hare", AtomicInteger(0)),
    )
    val race = ConcurrentLinkedDeque<String>()
    val threadCount = AtomicInteger(0)
    for (key in arrayListOf("tortoise", "hare")) {
      for (n in 1..2) {
        executor.execute {
          try {
            cache.getOrCompute(String(key.encodeToByteArray().copyOf())) { k ->
              val num = count[key]?.incrementAndGet() ?: -1
              Thread.currentThread().name = "$key-$num"
              threadCount.incrementAndGet()
              if (key == "hare") {
                Thread.sleep(250) // give tortoise a chance to start
              }
              race.add("$key $num started")
              expectThat(running[key]?.getAndSet(true)).isFalse()
              if (num == 1) {
                Thread.sleep(if (key == "tortoise") 3000 else 1000)
              }
              expectThat(running[key]?.getAndSet(false)).isTrue()
              race.add("$key $num finished")
              null
            }
          } catch (e: Throwable) {
            race.add("Thread $key failed: ${e.message}")
          }
        }
      }
    }
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)
    expectThat(threadCount.get()).isEqualTo(maxThreads)
    expectThat(race.joinToString("\n")).isEqualTo(
      """
      tortoise 1 started
      hare 1 started
      hare 1 finished
      hare 2 started
      hare 2 finished
      tortoise 1 finished
      tortoise 2 started
      tortoise 2 finished
      """.trimIndent()
    )
  }
}
