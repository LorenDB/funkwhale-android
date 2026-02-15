package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs

class SwipeableConstraintLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  private var swipeListener: OnSwipeListener? = null
  private val gestureDetector: GestureDetector

  init {
    gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
      private val SWIPE_THRESHOLD = 100
      private val SWIPE_VELOCITY_THRESHOLD = 100

      override fun onDown(e: MotionEvent): Boolean = true

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

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    gestureDetector.onTouchEvent(ev)
    return super.onInterceptTouchEvent(ev)
  }

  fun setOnSwipeListener(listener: OnSwipeListener) {
    swipeListener = listener
  }

  interface OnSwipeListener {
    fun onSwipeLeft()
    fun onSwipeRight()
  }
}
