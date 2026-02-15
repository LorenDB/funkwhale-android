package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.animation.doOnEnd
import kotlin.math.abs

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
  private var isHorizontalSwipe = false
  private var initialX = 0f
  private var initialY = 0f
  private var currentX = 0f
  private var swipeStarted = false

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  companion object {
    private const val SWIPE_THRESHOLD = 150f
    private const val HORIZONTAL_DETECTION_THRESHOLD = 30f
    private const val MAX_TRANSLATION = 300f
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        initialX = event.x
        initialY = event.y
        currentX = event.x
        isHorizontalSwipe = false
        swipeStarted = false
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        currentX = event.x
        val diffX = currentX - initialX
        val diffY = abs(event.y - initialY)
        
        // Detect horizontal swipe
        if (!isHorizontalSwipe && abs(diffX) > HORIZONTAL_DETECTION_THRESHOLD && abs(diffX) > diffY * 1.5f) {
          isHorizontalSwipe = true
          swipeStarted = true
        }
        
        if (isHorizontalSwipe) {
          // Apply live translation with diminishing returns
          val dampedTranslation = dampTranslation(diffX)
          translationX = dampedTranslation
          
          // Apply subtle alpha change
          alpha = 1f - (abs(dampedTranslation) / MAX_TRANSLATION) * 0.3f
          
          return true
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (swipeStarted) {
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
