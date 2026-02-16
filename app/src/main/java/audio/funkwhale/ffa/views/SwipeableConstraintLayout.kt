package audio.funkwhale.ffa.views

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * A ConstraintLayout that detects horizontal swipe gestures for track skipping.
 *
 * During a swipe the layout translates *itself* (so the background slides
 * with the content) together with any extra "co-moving" views (e.g. the
 * album cover that sits outside this layout).
 *
 * Views that must stay visually static (e.g. playback buttons) are
 * counter-translated by the negative of the swipe offset so they appear
 * not to move.
 */
class SwipeableConstraintLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  private var swipeListener: OnSwipeListener? = null
  private var gestureState = GestureState.NONE
  private var initialX = 0f
  private var initialY = 0f
  private var currentX = 0f

  /**
   * Extra views that translate together with this layout during a swipe
   * (e.g. the album cover which is a sibling in the parent MotionLayout).
   */
  private var coMovingViews: List<View> = emptyList()

  /**
   * Views that must stay visually static during a swipe.  They receive
   * a counter-translation equal to the negative of the swipe offset so
   * they appear pinned in place (e.g. playback buttons).
   */
  private var staticViews: List<View> = emptyList()

  companion object {
    private const val SWIPE_THRESHOLD = 150f
    private const val GESTURE_DETECTION_THRESHOLD = 20f
    private const val MAX_TRANSLATION = 300f
    private const val TAP_THRESHOLD = 10f
  }

  private enum class GestureState {
    NONE,
    TAP,
    HORIZONTAL,
    VERTICAL
  }

  /**
   * Set extra views that should translate alongside this layout during a
   * swipe.  This layout always translates itself; these are additional
   * views that move with it.
   */
  fun setCoMovingViews(views: List<View>) {
    coMovingViews = views
  }

  /**
   * Set views that must remain visually static during a swipe.  These
   * views will receive a counter-translation so they appear pinned in
   * place while the rest of the layout slides.
   */
  fun setStaticViews(views: List<View>) {
    staticViews = views
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        initialX = ev.x
        initialY = ev.y
        currentX = ev.x
        gestureState = GestureState.NONE
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        if (gestureState == GestureState.NONE) {
          val diffX = abs(ev.x - initialX)
          val diffY = abs(ev.y - initialY)

          if (diffX > GESTURE_DETECTION_THRESHOLD || diffY > GESTURE_DETECTION_THRESHOLD) {
            gestureState = when {
              diffX > diffY * 1.5f -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                GestureState.HORIZONTAL
              }
              diffY > diffX * 1.5f -> GestureState.VERTICAL
              else -> GestureState.NONE
            }
          }
        }

        if (gestureState == GestureState.HORIZONTAL) return true
        if (gestureState == GestureState.VERTICAL) return false
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (gestureState == GestureState.NONE) {
          val diffX = abs(ev.x - initialX)
          val diffY = abs(ev.y - initialY)
          if (diffX < TAP_THRESHOLD && diffY < TAP_THRESHOLD) {
            gestureState = GestureState.TAP
          }
        }
        parent?.requestDisallowInterceptTouchEvent(false)
        return false
      }
    }

    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        if (gestureState == GestureState.NONE) {
          initialX = event.x
          initialY = event.y
          currentX = event.x
        }
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (gestureState == GestureState.HORIZONTAL) {
          currentX = event.x
          applyTranslation(dampTranslation(currentX - initialX))
          return true
        }

        if (gestureState == GestureState.NONE) {
          val diffX = abs(event.x - initialX)
          val diffY = abs(event.y - initialY)

          if (diffX > GESTURE_DETECTION_THRESHOLD || diffY > GESTURE_DETECTION_THRESHOLD) {
            gestureState = when {
              diffX > diffY * 1.5f -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                GestureState.HORIZONTAL
              }
              diffY > diffX * 1.5f -> GestureState.VERTICAL
              else -> GestureState.NONE
            }

            if (gestureState == GestureState.HORIZONTAL) {
              currentX = event.x
              applyTranslation(dampTranslation(currentX - initialX))
              return true
            }
          }
        }
      }
      MotionEvent.ACTION_UP -> {
        parent?.requestDisallowInterceptTouchEvent(false)

        if (gestureState == GestureState.HORIZONTAL) {
          val diffX = currentX - initialX

          if (abs(diffX) > SWIPE_THRESHOLD) {
            completeSwipe(diffX > 0)
            if (diffX > 0) swipeListener?.onSwipeRight()
            else swipeListener?.onSwipeLeft()
          } else {
            springBack()
          }

          gestureState = GestureState.NONE
          return true
        }

        val diffX = abs(event.x - initialX)
        val diffY = abs(event.y - initialY)
        if (diffX < TAP_THRESHOLD && diffY < TAP_THRESHOLD) {
          gestureState = GestureState.NONE
          performClick()
          return true
        }

        gestureState = GestureState.NONE
      }
      MotionEvent.ACTION_CANCEL -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        if (gestureState == GestureState.HORIZONTAL) springBack()
        gestureState = GestureState.NONE
      }
    }

    return super.onTouchEvent(event)
  }

  private fun computeAlpha(translation: Float): Float {
    val absTrans = abs(translation)
    return 1f - (absTrans / MAX_TRANSLATION) * 0.3f
  }

  private fun applyTranslation(translation: Float) {
    val alphaValue = computeAlpha(translation)

    // Translate this layout and all co-moving views
    this.translationX = translation
    this.alpha = alphaValue
    for (view in coMovingViews) {
      view.translationX = translation
      view.alpha = alphaValue
    }
    // Counter-translate static views so they appear pinned
    for (view in staticViews) {
      view.translationX = -translation
    }
  }

  private fun resetTranslation() {
    this.translationX = 0f
    this.alpha = 1f
    for (view in coMovingViews) {
      view.translationX = 0f
      view.alpha = 1f
    }
    for (view in staticViews) {
      view.translationX = 0f
    }
  }

  private fun dampTranslation(translation: Float): Float {
    val absTranslation = abs(translation)
    val dampedAbs = if (absTranslation < SWIPE_THRESHOLD) {
      absTranslation
    } else {
      SWIPE_THRESHOLD + (absTranslation - SWIPE_THRESHOLD) * 0.5f
    }
    return min(dampedAbs, MAX_TRANSLATION) * translation.sign
  }

  private fun completeSwipe(isRight: Boolean) {
    val startTranslation = this.translationX
    val targetTranslation = if (isRight) width.toFloat() else -width.toFloat()

    val animator = ValueAnimator.ofFloat(startTranslation, targetTranslation)
    animator.duration = 200L
    animator.addUpdateListener { animation ->
      val value = animation.animatedValue as Float
      val alphaValue = max(0f, 1f - abs(value) / width)

      this.translationX = value
      this.alpha = alphaValue
      for (view in coMovingViews) {
        view.translationX = value
        view.alpha = alphaValue
      }
      for (view in staticViews) {
        view.translationX = -value
      }
    }
    animator.doOnEnd { resetTranslation() }
    animator.start()
  }

  private fun springBack() {
    val startTranslation = this.translationX

    val animator = ValueAnimator.ofFloat(startTranslation, 0f)
    animator.duration = 200L
    animator.addUpdateListener { animation ->
      val value = animation.animatedValue as Float
      val alphaValue = computeAlpha(value)

      this.translationX = value
      this.alpha = alphaValue
      for (view in coMovingViews) {
        view.translationX = value
        view.alpha = alphaValue
      }
      for (view in staticViews) {
        view.translationX = -value
      }
    }
    animator.doOnEnd { resetTranslation() }
    animator.start()
  }

  fun setOnSwipeListener(listener: OnSwipeListener) {
    swipeListener = listener
  }

  interface OnSwipeListener {
    fun onSwipeLeft()
    fun onSwipeRight()
  }
}
