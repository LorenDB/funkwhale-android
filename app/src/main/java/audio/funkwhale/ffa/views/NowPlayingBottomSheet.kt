package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.use
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.utils.BottomSheetIneractable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback


class NowPlayingBottomSheet @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr), BottomSheetIneractable {
  private val behavior = BottomSheetBehavior<NowPlayingBottomSheet>()
  private val targetHeaderId: Int

  val peekHeight get() = behavior.peekHeight

  init {
    targetHeaderId = context.theme.obtainStyledAttributes(
      attrs, R.styleable.NowPlaying, defStyleAttr, 0
    ).use {
      it.getResourceId(R.styleable.NowPlaying_target_header, NO_ID)
    }

    // Put default peek height to actionBarSize so it is not 0
    val tv = TypedValue()
    if (context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
      behavior.peekHeight = TypedValue.complexToDimensionPixelSize(
        tv.data, resources.displayMetrics
      )
    }
  }

  override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
    super.setLayoutParams(params)
    (params as CoordinatorLayout.LayoutParams).behavior = behavior
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    findViewById<View>(targetHeaderId)?.apply {
      behavior.setPeekHeight(this.height, false)
      this.setOnClickListener { this@NowPlayingBottomSheet.toggle() }
    } ?: hide()

    // Swipeable views overlay the header and consume touch events.
    // Set click listeners on them so taps still toggle the bottom sheet.
    findViewById<View>(R.id.now_playing_cover)?.setOnClickListener {
      this@NowPlayingBottomSheet.toggle()
    }
    findViewById<View>(R.id.header_controls)?.setOnClickListener {
      this@NowPlayingBottomSheet.toggle()
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean = true

  fun addBottomSheetCallback(callback: BottomSheetCallback) {
    behavior.addBottomSheetCallback(callback)
  }

  // Bottom sheet interactions
  override val isHidden: Boolean get() = behavior.state == BottomSheetBehavior.STATE_HIDDEN

  override fun isOpen(): Boolean = behavior.state == BottomSheetBehavior.STATE_EXPANDED

  override fun open() {
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
  }

  override fun close() {
    behavior.state = BottomSheetBehavior.STATE_COLLAPSED
  }

  override fun show() {
    behavior.isHideable = false
    if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
      close()
    }
  }

  override fun hide() {
    behavior.isHideable = true
    behavior.state = BottomSheetBehavior.STATE_HIDDEN
  }

  override fun toggle() {
    if (isHidden) return
    if (isOpen) close() else open()
  }
}
