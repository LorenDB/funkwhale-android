package audio.funkwhale.ffa.utils

import org.junit.Test
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ExtensionsKtTest {

  @Test
  fun nullStringDoesntContainCandidate() {
    val s: String? = null
    expectThat(s.containsIgnoringCase("candidate")).isFalse()
  }

  @Test
  fun stringDoesntContainCandidate() {
    expectThat("string".containsIgnoringCase("candidate")).isFalse()
  }

  @Test
  fun sameStringWithDifferentCasingContainsCandidate() {
    expectThat("CANDIDATE".containsIgnoringCase("candidate")).isTrue()
  }

  @Test
  fun sameStringWithMatchingCasingContainsCandidate() {
    expectThat("candidate".containsIgnoringCase("candidate")).isTrue()
  }
}
