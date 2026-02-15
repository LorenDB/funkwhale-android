package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
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
  private val gestureDetector: GestureDetector
  private var isHorizontalSwipe = false

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  init {
    gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
      private val SWIPE_THRESHOLD = 80
      private val SWIPE_VELOCITY_THRESHOLD = 80
      private val HORIZONTAL_DETECTION_THRESHOLD = 30

      override fun onDown(e: MotionEvent): Boolean {
        isHorizontalSwipe = false
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
        if (diffX > diffY && diffX > HORIZONTAL_DETECTION_THRESHOLD) {
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
    val animationTranslation = 100f
    val animationDuration = 300L
    val animationAlphaDivisor = 200f
    
    val targetTranslation = if (isRight) animationTranslation else -animationTranslation
    
    val animator = android.animation.ValueAnimator.ofFloat(0f, targetTranslation, 0f)
    animator.duration = animationDuration
    animator.addUpdateListener { animation ->
      translationX = animation.animatedValue as Float
      alpha = 1f - abs(animation.animatedValue as Float) / animationAlphaDivisor
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

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val handled = gestureDetector.onTouchEvent(event)
    
    // Reset the flag when touch ends
    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
      isHorizontalSwipe = false
    }
    
    // If we're handling a horizontal swipe, consume the event
    if (isHorizontalSwipe) {
      return true
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
