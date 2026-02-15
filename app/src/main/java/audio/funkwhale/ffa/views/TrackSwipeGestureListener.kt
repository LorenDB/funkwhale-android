package audio.funkwhale.ffa.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Detects horizontal swipe gestures on a target view and triggers track skip
 * callbacks with a sliding animation. Ensures each swipe is handled in only
 * one direction (horizontal or vertical, never both).
 *
 * During a horizontal drag, the swipeable view follows the finger to provide
 * visual feedback. If there are no more tracks in the direction being swiped,
 * the drag is prevented. On release with sufficient velocity, the view slides
 * off-screen, the track change is triggered, and new content slides in from
 * the opposite side.
 *
 * @param context Android context
 * @param swipeableView The view whose contents should animate (slide) on swipe
 * @param onSwipeLeft Called when the user swipes left (next track). Return true if handled.
 * @param onSwipeRight Called when the user swipes right (previous track). Return true if handled.
 * @param canSwipeLeft Returns true if swiping left (next track) is allowed
 * @param canSwipeRight Returns true if swiping right (previous track) is allowed
 */
class TrackSwipeGestureListener(
  context: Context,
  private val swipeableView: View,
  private val onSwipeLeft: () -> Boolean,
  private val onSwipeRight: () -> Boolean,
  private val canSwipeLeft: () -> Boolean,
  private val canSwipeRight: () -> Boolean,
  private val onTap: (() -> Unit)? = null,
) {

  companion object {
    private const val SWIPE_THRESHOLD = 100 // pixels
    private const val SWIPE_VELOCITY_THRESHOLD = 100 // pixels per second
    private const val DIRECTION_THRESHOLD = 10 // pixels
    private const val ANIMATION_DURATION = 200L // milliseconds
  }

  private var isAnimating = false
  private var directionLocked = false
  private var isHorizontalSwipe = false

  private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean {
      directionLocked = false
      isHorizontalSwipe = false
      return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
      onTap?.invoke()
      return onTap != null
    }

    override fun onScroll(
      e1: MotionEvent?,
      e2: MotionEvent,
      distanceX: Float,
      distanceY: Float
    ): Boolean {
      if (e1 == null || isAnimating) return false

      if (!directionLocked) {
        val diffX = abs(e2.x - e1.x)
        val diffY = abs(e2.y - e1.y)
        if (diffX > DIRECTION_THRESHOLD || diffY > DIRECTION_THRESHOLD) {
          directionLocked = true
          isHorizontalSwipe = diffX > diffY
          if (!isHorizontalSwipe) {
            // Vertical swipe detected: allow parents (e.g. BottomSheetBehavior)
            // to intercept so they can handle the vertical drag.
            swipeableView.parent?.requestDisallowInterceptTouchEvent(false)
          }
        }
      }

      if (isHorizontalSwipe) {
        val rawDiffX = e2.x - e1.x
        // Don't allow dragging if no track in that direction
        val clampedDiff = when {
          rawDiffX < 0 && !canSwipeLeft() -> 0f
          rawDiffX > 0 && !canSwipeRight() -> 0f
          else -> rawDiffX
        }
        swipeableView.translationX = clampedDiff
        return true
      }

      return false
    }

    override fun onFling(
      e1: MotionEvent?,
      e2: MotionEvent,
      velocityX: Float,
      velocityY: Float
    ): Boolean {
      if (e1 == null || isAnimating) return false
      if (!isHorizontalSwipe) return false

      val diffX = e2.x - e1.x

      if (abs(diffX) < SWIPE_THRESHOLD || abs(velocityX) < SWIPE_VELOCITY_THRESHOLD) return false

      return if (diffX < 0) {
        if (canSwipeLeft()) {
          completeSwipe(toLeft = true) { onSwipeLeft() }
          true
        } else false
      } else {
        if (canSwipeRight()) {
          completeSwipe(toLeft = false) { onSwipeRight() }
          true
        } else false
      }
    }
  })

  /**
   * Pass touch events from the target view to this listener.
   * Always returns true to receive subsequent events in the gesture sequence.
   * Taps are forwarded via the onTap callback; vertical drags are not
   * consumed and the parent can intercept them.
   */
  fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        // Temporarily prevent parents (e.g. BottomSheetBehavior) from
        // intercepting until we determine the swipe direction.
        swipeableView.parent?.requestDisallowInterceptTouchEvent(true)
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        swipeableView.parent?.requestDisallowInterceptTouchEvent(false)

        if (isHorizontalSwipe && !isAnimating) {
          // Snap back if not flung with enough velocity
          snapBack()
        }

        directionLocked = false
        isHorizontalSwipe = false
      }
    }

    gestureDetector.onTouchEvent(event)

    return true
  }

  private fun snapBack() {
    swipeableView.animate()
      .translationX(0f)
      .setDuration(ANIMATION_DURATION)
      .setListener(null)
      .start()
  }

  private fun completeSwipe(toLeft: Boolean, onComplete: () -> Unit) {
    if (isAnimating) return
    isAnimating = true

    val width = swipeableView.width.toFloat()
    val exitTranslation = if (toLeft) -width else width

    // Slide out to edge
    swipeableView.animate()
      .translationX(exitTranslation)
      .setDuration(ANIMATION_DURATION)
      .setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          // Trigger the track change (new content will be loaded by data binding)
          onComplete()

          // Position on opposite side for slide-in
          swipeableView.translationX = -exitTranslation

          // Slide in with new content
          swipeableView.animate()
            .translationX(0f)
            .setDuration(ANIMATION_DURATION)
            .setListener(object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
              }
            })
            .start()
        }
      })
      .start()
  }
}
