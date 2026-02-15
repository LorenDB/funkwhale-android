package audio.funkwhale.ffa.views

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

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
  
  companion object {
    private const val SWIPE_THRESHOLD = 150f // Distance to trigger track change
    private const val GESTURE_DETECTION_THRESHOLD = 20f // Distance to determine gesture type
    private const val MAX_TRANSLATION = 300f // Maximum translation distance
    private const val TAP_THRESHOLD = 10f // Maximum movement for tap
  }
  
  private enum class GestureState {
    NONE,           // No gesture detected yet
    TAP,            // Looks like a tap (minimal movement)
    HORIZONTAL,     // Horizontal swipe detected
    VERTICAL        // Vertical movement detected (let parent handle)
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        initialX = ev.x
        initialY = ev.y
        currentX = ev.x
        gestureState = GestureState.NONE
        // Don't intercept yet - wait to see what kind of gesture this is
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        if (gestureState == GestureState.NONE) {
          // Determine gesture type based on movement
          val diffX = abs(ev.x - initialX)
          val diffY = abs(ev.y - initialY)
          
          // Need some movement to determine gesture type
          if (diffX > GESTURE_DETECTION_THRESHOLD || diffY > GESTURE_DETECTION_THRESHOLD) {
            gestureState = when {
              diffX > diffY * 1.5f -> {
                // Horizontal movement dominant - intercept for swipe
                parent?.requestDisallowInterceptTouchEvent(true)
                GestureState.HORIZONTAL
              }
              diffY > diffX * 1.5f -> {
                // Vertical movement dominant - let parent handle
                GestureState.VERTICAL
              }
              else -> {
                // Movement is diagonal or unclear - wait more
                GestureState.NONE
              }
            }
          }
        }
        
        // If we've determined it's a horizontal swipe, intercept
        if (gestureState == GestureState.HORIZONTAL) {
          return true
        }
        
        // If vertical, don't intercept (let bottom sheet handle)
        if (gestureState == GestureState.VERTICAL) {
          return false
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        // Check if this was a tap
        if (gestureState == GestureState.NONE) {
          val diffX = abs(ev.x - initialX)
          val diffY = abs(ev.y - initialY)
          if (diffX < TAP_THRESHOLD && diffY < TAP_THRESHOLD) {
            gestureState = GestureState.TAP
            // Don't intercept - let parent handle tap
          }
        }
        
        // Allow cleanup
        parent?.requestDisallowInterceptTouchEvent(false)
        return false
      }
    }
    
    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        // Only reset if we haven't already determined gesture state in onInterceptTouchEvent
        if (gestureState == GestureState.NONE) {
          initialX = event.x
          initialY = event.y
          currentX = event.x
        }
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        // Only handle if we're in horizontal swipe mode
        if (gestureState == GestureState.HORIZONTAL) {
          currentX = event.x
          val diffX = currentX - initialX
          
          // Apply live translation with diminishing returns for drag feel
          val dampedTranslation = dampTranslation(diffX)
          translationX = dampedTranslation
          
          // Apply subtle alpha change
          alpha = 1f - (abs(dampedTranslation) / MAX_TRANSLATION) * 0.3f
          
          return true
        }
        
        // Try to determine gesture type if not yet determined
        if (gestureState == GestureState.NONE) {
          val diffX = abs(event.x - initialX)
          val diffY = abs(event.y - initialY)
          
          if (diffX > GESTURE_DETECTION_THRESHOLD || diffY > GESTURE_DETECTION_THRESHOLD) {
            gestureState = when {
              diffX > diffY * 1.5f -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                GestureState.HORIZONTAL
              }
              diffY > diffX * 1.5f -> {
                // Vertical movement dominant
                GestureState.VERTICAL
              }
              else -> {
                // Movement is diagonal or unclear - keep NONE and wait
                GestureState.NONE
              }
            }
            
            if (gestureState == GestureState.HORIZONTAL) {
              // Start handling the gesture now
              currentX = event.x
              val diffX = currentX - initialX
              val dampedTranslation = dampTranslation(diffX)
              translationX = dampedTranslation
              alpha = 1f - (abs(dampedTranslation) / MAX_TRANSLATION) * 0.3f
              return true
            }
          }
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        
        if (gestureState == GestureState.HORIZONTAL) {
          val diffX = currentX - initialX
          
          // Check if we've swiped far enough to trigger action
          if (abs(diffX) > SWIPE_THRESHOLD) {
            // Complete the swipe animation
            completeSwipe(diffX > 0)
            
            // Notify listener
            if (diffX > 0) {
              swipeListener?.onSwipeRight()
            } else {
              swipeListener?.onSwipeLeft()
            }
          } else {
            // Spring back to original position
            springBack()
          }
          
          gestureState = GestureState.NONE
          return true
        }
        
        gestureState = GestureState.NONE
      }
    }
    
    return super.onTouchEvent(event)
  }
  
  private fun dampTranslation(translation: Float): Float {
    // Apply diminishing returns to make large swipes feel natural
    val absTranslation = abs(translation)
    val dampedAbs = if (absTranslation < SWIPE_THRESHOLD) {
      absTranslation
    } else {
      SWIPE_THRESHOLD + (absTranslation - SWIPE_THRESHOLD) * 0.5f
    }
    
    return min(dampedAbs, MAX_TRANSLATION) * translation.sign
  }

  private fun completeSwipe(isRight: Boolean) {
    val targetTranslation = if (isRight) width.toFloat() else -width.toFloat()
    
    val animator = ValueAnimator.ofFloat(translationX, targetTranslation)
    animator.duration = 200L
    animator.addUpdateListener { animation ->
      translationX = animation.animatedValue as Float
      alpha = max(0f, 1f - abs(translationX) / width)
    }
    animator.doOnEnd {
      // Reset immediately
      translationX = 0f
      alpha = 1f
    }
    animator.start()
  }

  private fun springBack() {
    val animator = ValueAnimator.ofFloat(translationX, 0f)
    animator.duration = 200L
    animator.addUpdateListener { animation ->
      translationX = animation.animatedValue as Float
      alpha = 1f - (abs(translationX) / MAX_TRANSLATION) * 0.3f
    }
    animator.doOnEnd {
      translationX = 0f
      alpha = 1f
    }
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
