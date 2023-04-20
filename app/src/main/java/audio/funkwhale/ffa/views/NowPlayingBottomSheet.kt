package audio.funkwhale.ffa.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.use
import audio.funkwhale.ffa.R
import audio.funkwhale.ffa.utils.BottomSheetIneractable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.card.MaterialCardView


class NowPlayingBottomSheet @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs), BottomSheetIneractable {
  private val behavior = BottomSheetBehavior<NowPlayingBottomSheet>()
  private val targetHeaderId: Int

  val peekHeight get() = behavior.peekHeight

  init {
    targetHeaderId = context.theme.obtainStyledAttributes(
      attrs, R.styleable.NowPlaying, defStyleAttr, 0
    ).use {
      it.getResourceId(R.styleable.NowPlaying_target_header, NO_ID)
    }
  }

  override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
    super.setLayoutParams(params)
    (params as CoordinatorLayout.LayoutParams).behavior = behavior
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    findViewById<View>(targetHeaderId)?.apply {
      behavior.setPeekHeight(this.measuredHeight, false)
      this.setOnClickListener { this@NowPlayingBottomSheet.toggle() }
    } ?: hide()
  }

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
    close()
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
