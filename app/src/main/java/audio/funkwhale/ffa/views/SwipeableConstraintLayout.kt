package audio.funkwhale.ffa.views

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import kotlin.math.abs

class SwipeableConstraintLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  private var swipeListener: OnSwipeListener? = null
  private val gestureDetector: GestureDetector
  private var isHorizontalSwipe = false
  private var initialX = 0f

  init {
    gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
      private val SWIPE_THRESHOLD = 80
      private val SWIPE_VELOCITY_THRESHOLD = 80

      override fun onDown(e: MotionEvent): Boolean {
        isHorizontalSwipe = false
        initialX = e.x
        return true
      }

      override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
      ): Boolean {
        val diffX = abs(e2.x - e1.x)
        val diffY = abs(e2.y - e1.y)
        
        // Detect if this is a horizontal swipe early
        if (diffX > diffY && diffX > 30) {
          isHorizontalSwipe = true
        }
        
        return isHorizontalSwipe
      }

      override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
      ): Boolean {
        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y

        if (abs(diffX) > abs(diffY) &&
            abs(diffX) > SWIPE_THRESHOLD &&
            abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
          
          // Animate the swipe
          animateSwipe(diffX > 0)
          
          if (diffX > 0) {
            swipeListener?.onSwipeRight()
          } else {
            swipeListener?.onSwipeLeft()
          }
          return true
        }
        return false
      }
    })
  }

  private fun animateSwipe(isRight: Boolean) {
    val targetTranslation = if (isRight) 100f else -100f
    
    val animator = ValueAnimator.ofFloat(0f, targetTranslation, 0f)
    animator.duration = 300
    animator.addUpdateListener { animation ->
      translationX = animation.animatedValue as Float
      alpha = 1f - abs(animation.animatedValue as Float) / 200f
    }
    animator.doOnEnd {
      translationX = 0f
      alpha = 1f
    }
    animator.start()
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    val detected = gestureDetector.onTouchEvent(ev)
    
    // If we detected a horizontal swipe, intercept the touch event
    // to prevent the bottom sheet from expanding
    if (isHorizontalSwipe) {
      return true
    }
    
    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val handled = gestureDetector.onTouchEvent(event)
    
    // Reset the flag when touch ends
    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
      isHorizontalSwipe = false
    }
    
    return handled || super.onTouchEvent(event)
  }

  fun setOnSwipeListener(listener: OnSwipeListener) {
    swipeListener = listener
  }

  interface OnSwipeListener {
    fun onSwipeLeft()
    fun onSwipeRight()
  }
}
