package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

open class SquareView : View {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val dimension = if(measuredWidth == 0 && measuredHeight > 0) measuredHeight else measuredWidth

    setMeasuredDimension(dimension, dimension)
  }
}


open class SquareImageView : AppCompatImageView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val dimension = if(measuredWidth == 0 && measuredHeight > 0) measuredHeight else measuredWidth

    setMeasuredDimension(dimension, dimension)
  }
}

open class SwipeableSquareImageView : AppCompatImageView {
  private var swipeListener: OnSwipeListener? = null
  private val gestureDetector: GestureDetector

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, style: Int) : super(context, attrs, style)

  init {
    gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
      private val SWIPE_THRESHOLD = 100
      private val SWIPE_VELOCITY_THRESHOLD = 100

      override fun onDown(e: MotionEvent): Boolean = true

      override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
      ): Boolean {
        if (e1 == null) return false

        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y

        if (abs(diffX) > abs(diffY) &&
            abs(diffX) > SWIPE_THRESHOLD &&
            abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
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

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val dimension = if(measuredWidth == 0 && measuredHeight > 0) measuredHeight else measuredWidth

    setMeasuredDimension(dimension, dimension)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
  }

  fun setOnSwipeListener(listener: OnSwipeListener) {
    swipeListener = listener
  }

  interface OnSwipeListener {
    fun onSwipeLeft()
    fun onSwipeRight()
  }
}
