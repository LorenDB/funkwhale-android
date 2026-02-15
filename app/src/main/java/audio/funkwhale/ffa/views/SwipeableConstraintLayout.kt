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

class SwipeableConstraintLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  private var swipeListener: OnSwipeListener? = null
  private var isHorizontalSwipe = false
  private var initialX = 0f
  private var currentX = 0f
  private var swipeStarted = false
  
  companion object {
    private const val SWIPE_THRESHOLD = 150f // Distance to trigger track change
    private const val HORIZONTAL_DETECTION_THRESHOLD = 30f
    private const val MAX_TRANSLATION = 300f // Maximum translation distance
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        initialX = ev.x
        currentX = ev.x
        isHorizontalSwipe = false
        swipeStarted = false
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        val diffX = abs(ev.x - initialX)
        val diffY = abs(ev.y - ev.rawY + (ev.rawY - ev.y))
        
        // Detect horizontal swipe early
        if (!isHorizontalSwipe && diffX > HORIZONTAL_DETECTION_THRESHOLD && diffX > diffY * 1.5f) {
          isHorizontalSwipe = true
          swipeStarted = true
          return true // Intercept to handle in onTouchEvent
        }
        
        // If we've started a horizontal swipe, keep intercepting
        if (isHorizontalSwipe) {
          return true
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        // Don't intercept on release - let parent handle if it's a tap
        return false
      }
    }
    
    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!isHorizontalSwipe && !swipeStarted) {
      return super.onTouchEvent(event)
    }
    
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        initialX = event.x
        currentX = event.x
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        currentX = event.x
        val diffX = currentX - initialX
        
        // Apply live translation with diminishing returns for drag feel
        val dampedTranslation = dampTranslation(diffX)
        translationX = dampedTranslation
        
        // Apply subtle alpha change
        alpha = 1f - (abs(dampedTranslation) / MAX_TRANSLATION) * 0.3f
        
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
        
        isHorizontalSwipe = false
        swipeStarted = false
        return true
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
    
    return min(dampedAbs, MAX_TRANSLATION) * if (translation > 0) 1f else -1f
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
