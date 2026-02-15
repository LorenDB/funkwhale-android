package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.animation.doOnEnd
import kotlin.math.abs
import kotlin.math.sign

open class SquareView : View {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val dimension = if (measuredWidth == 0 && measuredHeight > 0) measuredHeight else measuredWidth

    setMeasuredDimension(dimension, dimension)
  }
}


open class SquareImageView : AppCompatImageView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val dimension = if (measuredWidth == 0 && measuredHeight > 0) measuredHeight else measuredWidth

    setMeasuredDimension(dimension, dimension)
  }
}

open class SwipeableSquareImageView : AppCompatImageView {
  private var swipeListener: OnSwipeListener? = null
  private var gestureState = GestureState.NONE
  private var initialX = 0f
  private var initialY = 0f
  private var currentX = 0f

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  companion object {
    private const val SWIPE_THRESHOLD = 150f
    private const val GESTURE_DETECTION_THRESHOLD = 20f
    private const val MAX_TRANSLATION = 300f
    private const val TAP_THRESHOLD = 10f
  }
  
  private enum class GestureState {
    NONE,           // No gesture detected yet
    TAP,            // Looks like a tap (minimal movement)
    HORIZONTAL,     // Horizontal swipe detected
    VERTICAL        // Vertical movement detected
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        initialX = event.x
        initialY = event.y
        currentX = event.x
        gestureState = GestureState.NONE
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (gestureState == GestureState.NONE) {
          // Determine gesture type based on movement
          val diffX = abs(event.x - initialX)
          val diffY = abs(event.y - initialY)
          
          if (diffX > GESTURE_DETECTION_THRESHOLD || diffY > GESTURE_DETECTION_THRESHOLD) {
            gestureState = when {
              diffX > diffY * 1.5f -> {
                // Horizontal movement dominant
                parent?.requestDisallowInterceptTouchEvent(true)
                GestureState.HORIZONTAL
              }
              diffY > diffX * 1.5f -> {
                // Vertical movement dominant - let parent handle (for bottom sheet)
                GestureState.VERTICAL
              }
              else -> {
                // Movement is diagonal or unclear - wait more
                GestureState.NONE
              }
            }
          }
        }
        
        if (gestureState == GestureState.HORIZONTAL) {
          currentX = event.x
          val diffX = currentX - initialX
          
          // Apply live translation with diminishing returns
          val dampedTranslation = dampTranslation(diffX)
          translationX = dampedTranslation
          
          // Apply subtle alpha change
          alpha = 1f - (abs(dampedTranslation) / MAX_TRANSLATION) * 0.3f
          
          return true
        } else if (gestureState == GestureState.VERTICAL) {
          // Let parent handle vertical gestures (bottom sheet drag)
          return false
        }
      }
      MotionEvent.ACTION_UP -> {
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
        
        // If no swipe was detected, treat as a tap and propagate the click
        // up through the view hierarchy (to open the fullscreen player)
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

        if (gestureState == GestureState.HORIZONTAL) {
          springBack()
        }

        gestureState = GestureState.NONE
      }
    }
    
    return super.onTouchEvent(event)
  }
  
  private fun dampTranslation(translation: Float): Float {
    val absTranslation = abs(translation)
    val dampedAbs = if (absTranslation < SWIPE_THRESHOLD) {
      absTranslation
    } else {
      SWIPE_THRESHOLD + (absTranslation - SWIPE_THRESHOLD) * 0.5f
    }
    
    return kotlin.math.min(dampedAbs, MAX_TRANSLATION) * translation.sign
  }

  private fun completeSwipe(isRight: Boolean) {
    val targetTranslation = if (isRight) width.toFloat() else -width.toFloat()
    
    val animator = android.animation.ValueAnimator.ofFloat(translationX, targetTranslation)
    animator.duration = 200L
    animator.addUpdateListener { animation ->
      translationX = animation.animatedValue as Float
      alpha = kotlin.math.max(0f, 1f - abs(translationX) / width)
    }
    animator.doOnEnd {
      translationX = 0f
      alpha = 1f
    }
    animator.start()
  }

  private fun springBack() {
    val animator = android.animation.ValueAnimator.ofFloat(translationX, 0f)
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

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val dimension = if (measuredWidth == 0 && measuredHeight > 0) measuredHeight else measuredWidth

    setMeasuredDimension(dimension, dimension)
  }

  fun setOnSwipeListener(listener: OnSwipeListener) {
    swipeListener = listener
  }

  interface OnSwipeListener {
    fun onSwipeLeft()
    fun onSwipeRight()
  }
}
