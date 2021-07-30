package audio.funkwhale.util

import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import java.lang.reflect.Method

class MockKJUnitRunner(private val testClass: Class<*>) : Runner() {

  private val methodDescriptions: MutableMap<Method, Description> = mutableMapOf()

  init {
    // Build method/descriptions map
    testClass.methods
      .map { method ->
        val annotation: Annotation? = method.getAnnotation(Test::class.java)
        method to annotation
      }
      .filter { (_, annotation) ->
        annotation != null
      }
      .map { (method, annotation) ->
        val desc = Description.createTestDescription(testClass, method.name, annotation)
        method to desc
      }
      .forEach { (method, desc) -> methodDescriptions[method] = desc }
  }

  override fun getDescription(): Description {
    val description = Description.createSuiteDescription(
      testClass.name, *testClass.annotations
    )
    methodDescriptions.values.forEach { description.addChild(it) }
    return description
  }

  override fun run(notifier: RunNotifier?) {
    val testObject = testClass.newInstance()
    MockKAnnotations.init(testObject, relaxUnitFun = true)

    methodDescriptions
      .onEach { (_, _) -> clearAllMocks() }
      .onEach { (_, desc) -> notifier!!.fireTestStarted(desc) }
      .forEach { (method, desc) ->
        try {
          method.invoke(testObject)
        } catch (e: Throwable) {
          notifier!!.fireTestFailure(Failure(desc, e.cause))
        } finally {
          notifier!!.fireTestFinished(desc)
        }
      }
  }
}
